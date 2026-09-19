#!/usr/bin/env python3
"""
AnimeJing SenseVoice ASR helper.

Reads a media file (audio or video), extracts audio with ffmpeg,
runs SenseVoiceSmall ONNX, prints SRT-format segments to stdout so
the Java caller can write them to a real .srt file.

Usage:
    sensevoice_asr.py --input episode01.mkv --lang ja
    sensevoice_asr.py --input episode01.mkv --lang ja --out episode01.srt
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

DEFAULT_CACHE_DIR = Path.home() / ".cache" / "animejing" / "sensevoice"


def find_ffmpeg() -> str:
    """Locate ffmpeg. AnimeJing bundles one under resources/ in dev, but
    on a user machine it's expected on PATH."""
    candidates = [
        shutil.which("ffmpeg"),
        shutil.which("ffmpeg.exe"),
        # Bundled paths (AppImage layout)
        "AnimeJing/lib/runtime/bin/ffmpeg",
        "AnimeJing/lib/app/ffmpeg",
        "resources/linux/ffmpeg/ffmpeg",
    ]
    for c in candidates:
        if c and os.path.exists(c):
            return c
    raise FileNotFoundError("ffmpeg not found; install ffmpeg or run inside the AnimeJing AppImage")


def extract_audio(ffmpeg: str, input_path: str, out_wav: str) -> None:
    # 16 kHz mono PCM — SenseVoice input format
    cmd = [
        ffmpeg, "-y", "-i", input_path,
        "-ac", "1", "-ar", "16000", "-f", "wav",
        out_wav,
    ]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def seconds_to_srt_time(t: float) -> str:
    h = int(t // 3600)
    m = int((t % 3600) // 60)
    s = t - h * 3600 - m * 60
    return f"{h:02d}:{m:02d}:{s:06.3f}".replace(".", ",")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--lang", default="ja", choices=["ja", "en", "zh", "yue", "ko"])
    parser.add_argument("--cache-dir", default=str(DEFAULT_CACHE_DIR))
    parser.add_argument("--out", default="-", help="output SRT path, - for stdout")
    args = parser.parse_args()

    onnx_path = Path(args.cache_dir) / "sense-voice-small.onnx"
    if not onnx_path.exists():
        print(f"sense-voice-small.onnx missing at {onnx_path}", file=sys.stderr)
        return 2

    try:
        import sherpa_onnx  # type: ignore
    except ImportError as e:
        print(f"sherpa-onnx not installed (pip install sherpa-onnx); {e}", file=sys.stderr)
        return 3

    ffmpeg = find_ffmpeg()
    with tempfile.TemporaryDirectory() as td:
        wav_path = os.path.join(td, "audio.wav")
        extract_audio(ffmpeg, args.input, wav_path)

        recognizer = sherpa_onnx.OfflineRecognizer.from_sense_voice(
            model=str(onnx_path),
            language=args.lang,
            use_itn=True,
        )
        stream = recognizer.create_stream()
        import wave
        with wave.open(wav_path, "rb") as wf:
            samples = wf.readframes(wf.getnframes())
        stream.accept_waveform(sample_rate=16000, samples=samples)
        recognizer.decode_stream(stream)

        result_text = stream.result.text
        tokens = stream.result.tokens

        # Group tokens into SRT cues of ~6s each
        cues = []
        if tokens:
            chunk_size = max(1, len(tokens) // 10 or 1)
            for i in range(0, len(tokens), chunk_size):
                chunk = tokens[i:i + chunk_size]
                start = chunk[0].start_time if hasattr(chunk[0], "start_time") else 0.0
                end = chunk[-1].end_time if hasattr(chunk[-1], "end_time") else start + 6.0
                text = "".join(t.text for t in chunk).strip()
                if text:
                    cues.append((start, end, text))

        out_f = sys.stdout if args.out == "-" else open(args.out, "w", encoding="utf-8")
        try:
            for idx, (start, end, text) in enumerate(cues, 1):
                out_f.write(f"{idx}\n")
                out_f.write(f"{seconds_to_srt_time(start)} --> {seconds_to_srt_time(end)}\n")
                out_f.write(f"{text}\n\n")
        finally:
            if args.out != "-":
                out_f.close()

    return 0


if __name__ == "__main__":
    sys.exit(main())