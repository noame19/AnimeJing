/*
 * Copyright (c) 2023-2025 tang shimin
 *
 * This file is part of MuJing.
 *
 * MuJing is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MuJing is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MuJing. If not, see <https://www.gnu.org/licenses/>.
 *
 * AnimeJing modifications (c) 2026: neologd support placeholder.
 */

package com.mujingx.ui.util.tokenizer

/**
 * Optional dictionary override for [JapaneseTokenizer].
 *
 * Status (2026-09-17): NOT YET WIRED.
 *
 * Why:
 *  - atilika/kuromoji 0.9.0 ships only the basic IPADIC (~80k entries,
 *    dated 2007). Anime / web vocabulary coverage is poor.
 *  - The `kuromoji-ipadic-neologd` artifact exists in source but has
 *    never been published to Maven Central — README explicitly says
 *    "will be available in a future version". We would have to build
 *    from source (requires Maven + JDK + network) and ship a
 *    ~70MB binary alongside the app, or fetch on first launch.
 *
 * Until the runtime download + dictionary-load path is implemented,
 * [JapaneseTokenizer] falls back to the bundled IPADIC and we accept
 * the lower OOV rate for now. The TODO below captures the work.
 *
 * TODO: implement the runtime neologd override. Sketch:
 *   1. On first Japanese tokenize, check `~/.cache/kuromoji/neologd`.
 *   2. If missing, download from the official Atilika build (or our
 *      own GitHub Release) with progress reporting.
 *   3. Construct `Tokenizer.Builder().setUserDictionary(...)` from the
 *      downloaded file.
 *   4. On success, replace [JapaneseTokenizer]'s `tokenizer` field.
 *
 * Until then, the Android-facing dictionary cache is keyed off
 * `kuromoji-ipadic-0.9.0` so swapping in neologd later only requires
 * invalidating the cache key.
 */
object NeologdDict {
    /** Cache directory relative to the user home; matches the
     *  `~/.cache/kuromoji/neologd` path that GitHub Actions caches. */
    const val CACHE_DIR = ".cache/kuromoji/neologd"

    /** Currently a constant; flipped when override becomes available. */
    const val ENABLED: Boolean = false
}