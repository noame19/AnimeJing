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
 */

package com.mujingx.ui.util.tokenizer

import com.mujingx.ui.util.loadModelResource
import opennlp.tools.chunker.ChunkerME
import opennlp.tools.chunker.ChunkerModel
import opennlp.tools.postag.POSModel
import opennlp.tools.postag.POSTaggerME
import opennlp.tools.sentdetect.SentenceDetectorME
import opennlp.tools.sentdetect.SentenceModel
import opennlp.tools.tokenize.Tokenizer
import opennlp.tools.tokenize.TokenizerME
import opennlp.tools.tokenizer.TokenizerModel

/**
 * English tokenizer backed by Apache OpenNLP. Loads the three UD-EWT
 * models from classpath once at construction; OpenNLP tokenizer
 * instances are thread-safe and reused for every call.
 */
class EnglishTokenizer : LanguageTokenizer {
    override val language: String = "english"

    private val tokenizer: Tokenizer
    private val posTagger: POSTaggerME
    private val chunker: ChunkerME

    init {
        val tokenModel = loadModelResource("opennlp/opennlp-en-ud-ewt-tokens-1.0-1.9.3.bin").use { TokenizerModel(it) }
        tokenizer = TokenizerME(tokenModel)
        val posModel = loadModelResource("opennlp/opennlp-en-ud-ewt-pos-1.0-1.9.3.bin").use { POSModel(it) }
        posTagger = POSTaggerME(posModel)
        val chunkerModel = loadModelResource("opennlp/en-chunker.bin").use { ChunkerModel(it) }
        chunker = ChunkerME(chunkerModel)
    }

    override fun tokenize(text: String): List<Token> {
        // For English we tokenize the whole text (the legacy code split
        // sentences first but for vocabulary generation we don't need
        // sentence boundaries — only the per-token lemma / offset).
        val tokens = tokenizer.tokenize(text)
        val posTags = posTagger.tag(tokens)
        var cursor = 0
        return tokens.mapIndexed { i, surface ->
            val idx = text.indexOf(surface, cursor)
            val start = if (idx >= 0) idx else cursor
            cursor = start + surface.length
            Token(
                surface = surface,
                lemma = surface.lowercase(),
                reading = "",
                pos = posTags.getOrNull(i) ?: "",
                start = start,
                end = start + surface.length,
            )
        }
    }

    override fun tokenizeWithPhrases(text: String): List<Token> {
        val baseTokens = tokenizer.tokenize(text)
        val posTags = posTagger.tag(baseTokens)
        val chunks = chunker.chunkAsSpans(baseTokens, posTags)

        val result = mutableListOf<Token>()
        var cursor = 0
        for (chunk in chunks) {
            val phrase = baseTokens.copyOfRange(chunk.start, chunk.end).joinToString(" ").trim()
            if (phrase.isBlank()) continue
            val start = text.indexOf(phrase, cursor).takeIf { it >= 0 } ?: cursor
            cursor = start + phrase.length
            result.add(
                Token(
                    surface = phrase,
                    lemma = phrase.lowercase(),
                    reading = "",
                    pos = "PHRASE",
                    start = start,
                    end = start + phrase.length,
                )
            )
        }
        // Also keep individual tokens for the vocabulary set.
        for (t in baseTokens) {
            val start = text.indexOf(t, cursor).takeIf { it >= 0 } ?: cursor
            cursor = start + t.length
            result.add(
                Token(
                    surface = t,
                    lemma = t.lowercase(),
                    reading = "",
                    pos = "",
                    start = start,
                    end = start + t.length,
                )
            )
        }
        return result
    }

    /**
     * Detect sentence boundaries using OpenNLP's SentenceDetector.
     * Kept for callers that still want the legacy per-sentence pipeline.
     */
    fun sentenceDetect(text: String): List<String> {
        loadModelResource("opennlp/opennlp-en-ud-ewt-sentence-1.0-1.9.3.bin").use { modelIn ->
            val model = SentenceModel(modelIn)
            val sentenceDetector = SentenceDetectorME(model)
            return sentenceDetector.sentDetect(text).toList()
        }
    }
}