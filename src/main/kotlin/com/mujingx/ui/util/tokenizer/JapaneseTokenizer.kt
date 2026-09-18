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
 * AnimeJing modifications (c) 2026: Japanese tokenization backed by
 * Kuromoji IPADIC. Optional mecab-ipadic-neologd replacement is wired
 * up in [NeologdDict]; until the override is installed we fall back to
 * the bundled IPADIC so the app still runs offline.
 */

package com.mujingx.ui.util.tokenizer

import com.atilika.kuromoji.ipadic.Tokenizer

/**
 * Japanese tokenizer backed by Kuromoji IPADIC. The Kuromoji [Tokenizer]
 * is thread-safe once constructed, so we build it lazily on first use.
 * Optional mecab-ipadic-neologd replacement is out of scope here; see
 * NeologdDict.kt.
 */
class JapaneseTokenizer : LanguageTokenizer {
    override val language: String = "japanese"

    private val tokenizer: Tokenizer by lazy { Tokenizer.Builder().build() }

    override fun tokenize(text: String): List<Token> {
        if (text.isEmpty()) return emptyList()
        val morphs = tokenizer.tokenize(text)
        return morphs.map { m ->
            // Kuromoji com.atilika.kuromoji.ipadic.Token exposes (Kotlin property syntax):
            //   surface         — literal text
            //   baseForm        — dictionary form; "*" for unknown
            //   partOfSpeech    — "名詞,一般,*,*,*,*,日本語,ニホンゴ,ニホンゴ"
            //   reading         — katakana reading; "*" when unknown
            //   position        — char offset of first char of surface
            //   allFeatures     — String[] split by comma
            val pos: String = m.allFeatures.firstOrNull() ?: ""
            val base = m.baseForm.takeIf { it != "*" && it.isNotEmpty() } ?: m.surface
            val reading = m.reading.takeIf { it != "*" && it.isNotEmpty() } ?: ""
            Token(
                surface = m.surface,
                lemma = base,
                reading = reading,
                pos = pos,
                start = m.position,
                end = m.position + m.surface.length,
            )
        }
    }

    override fun tokenizeWithPhrases(text: String): List<Token> {
        // Japanese noun-phrase chunking is non-trivial and not required
        // for vocabulary generation in the first cut; return surface
        // tokens so the toggle has the same semantics as English.
        return tokenize(text)
    }
}