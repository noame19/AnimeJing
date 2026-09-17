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
 * AnimeJing modifications (c) 2026: tokenizer interface factored out so
 * English (OpenNLP) and Japanese (Kuromoji) implementations can be
 * swapped without changing vocabulary generation code.
 */

package com.mujingx.ui.util.tokenizer

/**
 * One morpheme produced by a language-specific tokenizer.
 *
 * @param surface the literal substring as it appears in the input text
 * @param lemma   dictionary / base form (English: lowercase; Japanese: kanji/kana base form)
 * @param reading pronunciation hint (English: empty; Japanese: katakana reading)
 * @param pos     coarse part-of-speech (NN, VB, 動詞, 名詞, …)
 * @param start   inclusive char offset
 * @param end     exclusive char offset
 */
data class Token(
    val surface: String,
    val lemma: String,
    val reading: String,
    val pos: String,
    val start: Int,
    val end: Int,
)

/**
 * Strategy interface for language-specific tokenization. Implementations
 * are expected to be expensive to construct (loading dictionary / models
 * from disk) and therefore singleton-ish; callers obtain them via
 * [Tokenizers].
 */
interface LanguageTokenizer {
    /** Stable language identifier, e.g. "english", "japanese". */
    val language: String

    /**
     * Split [text] into morphemes. Pure function: same input → same
     * output, no I/O. Punctuation and whitespace are returned as
     * [Token]s with empty [Token.lemma] so the vocabulary generator can
     * filter them out.
     */
    fun tokenize(text: String): List<Token>

    /**
     * Like [tokenize] but additionally collapses noun-phrase / verb-phrase
     * chunks into a single [Token]. Used when the user asks for phrase
     * extraction. The base [Token] is returned with `pos == "PHRASE"`.
     */
    fun tokenizeWithPhrases(text: String): List<Token>
}

/**
 * Global entry point. Default language is "english" to preserve the
 * existing behaviour; the vocabulary generator switches it based on the
 * user's choice.
 */
object Tokenizers {
    @Volatile var current: LanguageTokenizer = EnglishTokenizer()

    fun setLanguage(language: String): LanguageTokenizer {
        current = when (language.lowercase()) {
            "japanese", "ja", "jp" -> JapaneseTokenizer()
            else -> EnglishTokenizer()
        }
        return current
    }
}