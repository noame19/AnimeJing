#!/usr/bin/env python3
"""
AnimeJing model fetcher — single entry point so the user can run it once
instead of wrestling with Gradle tasks.

Downloads to ~/.cache/animejing/:
  - kokoro/kokoro-v1.0.onnx       (TTS)
  - kokoro/voices-v1.0.bin
  - sensevoice/sense-voice-small.onnx (ASR)

Usage:
    python3 scripts/fetch_models.py           # fetch all
    python3 scripts/fetch_models.py kokoro    # fetch only kokoro
    python3 scripts/fetch_models.py sensevoice
"""

import sys
import urllib.request
from pathlib import Path

CACHE = Path.home() / ".cache" / "animejing"
SOURCES = {
    "kokoro": {
        "dir": CACHE / "kokoro",
        "files": {
            "kokoro-v1.0.onnx":
                "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.1/kokoro-v1.0.onnx",
            "voices-v1.0.bin":
                "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.1/voices-v1.0.bin",
        },
        "min_size": 1_000_000,
    },
    "sensevoice": {
        "dir": CACHE / "sensevoice",
        "files": {
            "sense-voice-small.onnx":
                "https://github.com/FunAudioLLM/SenseVoice/releases/download/v1.2.0/sense-voice-small.onnx",
        },
        "min_size": 200_000_000,
    },
}


def fetch_one(name: str, url: str, out: Path, min_size: int) -> bool:
    if out.exists() and out.stat().st_size > min_size:
        print(f"  [skip] {name} already present ({out.stat().st_size:,} bytes)")
        return True
    out.parent.mkdir(parents=True, exist_ok=True)
    print(f"  [fetch] {name}  ←  {url}")
    try:
        with urllib.request.urlopen(url, timeout=60) as resp:
            data = resp.read()
        out.write_bytes(data)
        print(f"  [done]  {out}  ({len(data):,} bytes)")
        return True
    except Exception as e:
        print(f"  [FAIL]  {name}: {e}", file=sys.stderr)
        return False


def main() -> int:
    targets = sys.argv[1:] or list(SOURCES.keys())
    failed = 0
    for key in targets:
        spec = SOURCES.get(key)
        if not spec:
            print(f"unknown target {key!r}; known: {list(SOURCES)}", file=sys.stderr)
            failed += 1
            continue
        print(f"== {key} ==")
        spec["dir"].mkdir(parents=True, exist_ok=True)
        for name, url in spec["files"].items():
            if not fetch_one(name, url, spec["dir"] / name, spec["min_size"]):
                failed += 1
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())