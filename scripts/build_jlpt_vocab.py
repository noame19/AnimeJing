#!/usr/bin/env python3
"""
AnimeJing JLPT vocabulary builder (OpenJLPT-based).

Reads the OpenJLPT vocab CSV for each level from
    https://github.com/evanclan/OpenJLPT/blob/main/data/csv/vocab-n{1..5}.csv
caches them in scripts/.cache/openjlpt/, optionally enriches each row
via the jisho.org API (kana / romaji / glossEn / jlpt / examples), and
writes MuJing-compatible Vocabulary JSON to
    resources/common/vocabulary/japanese/JLPT_Nx.json

Data attribution:
  - OpenJLPT by evanclan — CC BY-SA 4.0
  - JMDict glosses via jisho.org — EDRDG / JMdict project (GPL/CC)

Usage:
    python3 scripts/build_jlpt_vocab.py                # all 5 levels, CSV only
    python3 scripts/build_jlpt_vocab.py N5 N4          # subset
    python3 scripts/build_jlpt_vocab.py --enrich N5   # also hit jisho.org for kana/glossEn
"""

import argparse
import csv
import json
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

VOCAB_DIR = Path(__file__).resolve().parents[1] / "resources" / "common" / "vocabulary" / "japanese"
CACHE_DIR = Path(__file__).resolve().parent / ".cache" / "openjlpt"
ENRICH_CACHE_DIR = Path(__file__).resolve().parent / ".cache" / "jisho_enrich"

OPENJLPT_BASE = "https://raw.githubusercontent.com/evanclan/OpenJLPT/main/data/csv"


def fetch_csv(level: str) -> list[dict]:
    fname = f"vocab-{level.lower()}.csv"
    out = CACHE_DIR / fname
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    if not out.exists() or out.stat().st_size < 1000:
        url = f"{OPENJLPT_BASE}/{fname}"
        print(f"  [fetch] {url}")
        try:
            with urllib.request.urlopen(url, timeout=30) as resp:
                out.write_bytes(resp.read())
        except Exception as e:
            print(f"  [FAIL] {fname}: {e}", file=sys.stderr)
            return []
    rows = []
    with open(out, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            rows.append(row)
    return rows


def cached_jisho_enrich(word: str) -> dict | None:
    """Look up via jisho.org with on-disk cache. Re-running this script
    is then nearly free after the first run."""
    ENRICH_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    cache_key = word.replace("/", "_").replace(" ", "_") + ".json"
    cache_file = ENRICH_CACHE_DIR / cache_key
    if cache_file.exists() and cache_file.stat().st_size > 0:
        try:
            return json.loads(cache_file.read_text(encoding="utf-8"))
        except Exception:
            pass
    for attempt in range(3):
        try:
            url = "https://jisho.org/api/v1/search/words?keyword=" + urllib.parse.quote(word)
            req = urllib.request.Request(url, headers={"User-Agent": "AnimeJing/0.1"})
            with urllib.request.urlopen(req, timeout=15) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
            data = payload.get("data") or []
            if not data:
                cache_file.write_text("{}", encoding="utf-8")
                return None
            cache_file.write_text(json.dumps(data[0], ensure_ascii=False), encoding="utf-8")
            return data[0]
        except Exception as e:
            if attempt == 2:
                print(f"  [warn] jisho '{word}' failed: {e}", file=sys.stderr)
                return None
            time.sleep(0.6 * (attempt + 1))
    return None


def build_word(level: str, row: dict, hit: dict | None) -> dict:
    surface = (row.get("word") or "").strip()
    reading = (row.get("reading") or "").strip()
    meanings = (row.get("meanings") or "").strip()
    level_str = (row.get("level") or level).strip()
    example_ja = (row.get("example_ja") or "").strip()
    example_en = (row.get("example_en") or "").strip()

    jlpt_level = 0
    if level_str and len(level_str) >= 2 and level_str[0] in "Nn" and level_str[1:].isdigit():
        jlpt_level = int(level_str[1:])

    kanji = surface
    kana = reading
    gloss_en = meanings

    if hit is not None:
        japanese = hit.get("japanese") or [{}]
        primary = japanese[0] if japanese else {}
        if primary.get("word"):
            kanji = primary["word"]
        if primary.get("reading"):
            kana = primary["reading"]
        senses = hit.get("senses") or []
        glosses = []
        for s in senses[:5]:
            for g in s.get("english_definitions") or []:
                glosses.append(g)
        if glosses:
            gloss_en = "; ".join(glosses[:5])

    captions = []
    if example_ja:
        captions.append({
            "start": "00:00:00.000",
            "end": "00:00:00.000",
            "content": f"{example_ja}\n— {example_en}" if example_en else example_ja,
        })

    return {
        "value": kanji or surface,
        "usphone": "",
        "ukphone": "",
        "definition": gloss_en,
        "translation": gloss_en.split(";")[0].strip() if gloss_en else "",
        "pos": "",
        "collins": 0,
        "oxford": False,
        "tag": f"jlpt-{level_str.lower()}",
        "bnc": 0,
        "frq": 0,
        "exchange": "",
        "kanji": kanji,
        "kana": kana,
        "romaji": "",
        "conjugations": [],
        "jlpt": jlpt_level,
        "pitchAccent": "",
        "glossEn": gloss_en,
        "glossCn": "",
        "animeFrequency": 0,
        "externalCaptions": [],
        "captions": captions,
    }


def build_level(level: str, enrich: bool) -> dict:
    rows = fetch_csv(level)
    if not rows:
        return {"name": f"JLPT {level}", "type": "DOCUMENT", "language": "japanese",
                "size": 0, "relateVideoPath": "", "subtitlesTrackId": 0, "wordList": []}
    seen = set()
    word_list = []
    skipped = 0
    for idx, row in enumerate(rows, 1):
        surface = (row.get("word") or "").strip()
        if not surface or surface in seen:
            skipped += 1
            continue
        seen.add(surface)
        hit = cached_jisho_enrich(surface) if enrich else None
        word_list.append(build_word(level, row, hit))
        if enrich and idx % 100 == 0:
            print(f"  [{level}] enriched {idx}/{len(rows)}")
        if enrich:
            time.sleep(0.13)
    return {
        "name": f"JLPT {level}",
        "type": "DOCUMENT",
        "language": "japanese",
        "size": len(word_list),
        "relateVideoPath": "",
        "subtitlesTrackId": 0,
        "wordList": word_list,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("levels", nargs="*", default=["N5", "N4", "N3", "N2", "N1"])
    parser.add_argument("--enrich", action="store_true",
                        help="Also hit jisho.org for kana/glossEn/jlpt level on each entry")
    args = parser.parse_args()

    VOCAB_DIR.mkdir(parents=True, exist_ok=True)
    failed = 0
    for level in args.levels:
        level = level.upper()
        print(f"== {level} ==")
        vocab = build_level(level, enrich=args.enrich)
        if vocab["size"] == 0:
            print(f"  [FAIL] no rows for {level}", file=sys.stderr)
            failed += 1
            continue
        out = VOCAB_DIR / f"JLPT_{level}.json"
        out.write_text(json.dumps(vocab, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"  wrote {out}  ({vocab['size']} words)")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())