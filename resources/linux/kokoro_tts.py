#!/usr/bin/env python3
"""
AnimeJing Kokoro TTS helper.

Usage:
    kokoro_tts.py --text "食べる" --voice jf_alpha --speed 1.0 --out out.wav

Wraps the third-party `kokoro-onnx` package (thewh1teagle/kokoro-onnx).
Expects:
    - kokoro-onnx installed in a venv
    - Models at ~/.cache/animejing/kokoro/{kokoro-v1.0.onnx,voices-v1.0.bin}

If anything fails the script exits non-zero and writes the error to
stdout; the Java side falls back to the OS-level TTS (espeak-ng /
macOS say / Windows SAPI).
"""

import argparse
import sys
from pathlib import Path

DEFAULT_CACHE_DIR = Path.home() / ".cache" / "animejing" / "kokoro"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--text", required=True)
    parser.add_argument("--voice", default="jf_alpha")
    parser.add_argument("--speed", type=float, default=1.0)
    parser.add_argument("--out", required=True)
    parser.add_argument("--cache-dir", default=str(DEFAULT_CACHE_DIR))
    args = parser.parse_args()

    cache_dir = Path(args.cache_dir)
    onnx_path = cache_dir / "kokoro-v1.0.onnx"
    voices_path = cache_dir / "voices-v1.0.bin"
    if not onnx_path.exists() or not voices_path.exists():
        print(
            f"kokoro model files missing at {cache_dir}; "
            f"need kokoro-v1.0.onnx and voices-v1.0.bin",
            file=sys.stderr,
        )
        return 2

    try:
        from kokoro_onnx import Kokoro  # type: ignore
    except ImportError as e:
        print(
            f"kokoro-onnx not installed (pip install kokoro-onnx); {e}",
            file=sys.stderr,
        )
        return 3

    kokoro = Kokoro(str(onnx_path), str(voices_path))
    samples, sample_rate = kokoro.create(
        args.text,
        voice=args.voice,
        speed=args.speed,
        lang="ja",
    )

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)

    import wave
    with wave.open(str(out_path), "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        wf.writeframes(samples.tobytes())

    print(f"wrote {len(samples)} samples @ {sample_rate} Hz -> {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())