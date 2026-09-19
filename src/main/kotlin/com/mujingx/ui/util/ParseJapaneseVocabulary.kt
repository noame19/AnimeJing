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
 * AnimeJing modifications (c) 2026: parseJapaneseDocument mirrors the
 * English parseDocument pipeline but routes through LanguageTokenizer
 * so Kuromoji is used for tokenization. Kept in a separate file to
 * keep the diff against MuJing minimal.
 */

package com.mujingx.ui.util

import com.mujingx.data.JishoClient
import com.mujingx.data.Word
import com.mujingx.ui.util.tokenizer.LanguageTokenizer
import com.mujingx.ui.util.tokenizer.Tokenizers
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException
import javax.swing.JOptionPane

/**
 * Japanese counterpart of [parseDocument]. Reads a PDF or plain-text
 * file, tokenizes through the active [LanguageTokenizer] (which must
 * be a Japanese tokenizer), then collects lemma → context pairs.
 *
 * NOTE: This function does NOT yet look up lemmas in a JMdict SQLite
 * database — it returns [Word] rows with `value` set to the surface
 * form and `kanji` / `kana` populated from the Kuromoji Morpheme. A
 * follow-up commit wires JMdict lookup (C1).
 */
fun parseJapaneseDocument(
    pathName: String,
    enablePhrases: Boolean,
    sentenceLength: Int = 25,
    setProgressText: (String) -> Unit,
    tokenizer: LanguageTokenizer = Tokenizers.current,
): List<Word> {
    require(tokenizer.language == "japanese") {
        "parseJapaneseDocument requires a Japanese LanguageTokenizer, got ${tokenizer.language}"
    }

    val file = File(pathName)
    var text = ""
    val extension = file.extension
    val otherExtensions = listOf("txt", "java", "md", "cs", "cpp", "c", "kt", "js", "py", "ts")

    try {
        if (extension == "pdf") {
            setProgressText("日本語PDFを読み込み中")
            val document: PDDocument = PDDocument.load(file)
            val pdfStripper = PDFTextStripper()
            text = pdfStripper.getText(document)
            document.close()
        } else if (otherExtensions.contains(extension)) {
            text = file.readText()
            if (extension == "txt" && text.isNotEmpty() && text[0].code == 65279) {
                text = text.substring(1)
            }
        }
    } catch (_: InvalidPasswordException) {
        JOptionPane.showMessageDialog(null, "PDF is password-protected")
    } catch (_: IOException) {
        JOptionPane.showMessageDialog(null, "Failed to read file")
    }

    setProgressText("形態素解析中 (Kuromoji)")

    val tokens: List<com.mujingx.ui.util.tokenizer.Token> = if (enablePhrases) {
        tokenizer.tokenizeWithPhrases(text)
    } else {
        tokenizer.tokenize(text)
    }

    // Japanese punctuation that we want to filter out of the lemma set.
    val filterChars = setOf(
        "。", "、", "，", "．", "！", "？", "「", "」", "『", "』",
        "（", "）", "【", "】", "…", "・", "：", "；",
        "ー", "〜", " ", "\n", "\r", "\t",
    )

    val contextByLemma = mutableMapOf<String, MutableList<String>>()

    for (token in tokens) {
        val lemma = token.lemma.ifBlank { token.surface }
        if (lemma.isBlank()) continue
        if (lemma.length == 1 && filterChars.contains(lemma[0].toString())) continue
        if (filterChars.contains(lemma)) continue

        val context = clipJapaneseContext(token, text, sentenceLength)
        val list = contextByLemma.getOrPut(lemma) { mutableListOf() }
        if (list.size < 3) list.add(context)
    }

    setProgressText("日本語トークン ${contextByLemma.size} 件を抽出")

    // Until C1 lands we synthesise a Word from the Kuromoji morpheme so
    // downstream UI / FSRS can still render something.
    // AnimeJing: enrich every lemma via JishoClient so each Word row
    // carries kana / kanji / glossEn / jlpt fields populated. Network or
    // cache failures fall back to a context-only Word so vocabulary
    // generation never silently drops a word.
    val jisho = JishoClient()
    return contextByLemma.entries.map { (lemma, ctxs) ->
        val joinedCtx = ctxs.joinToString("\n").trim()
        val hit = jisho.lookup(lemma)
        if (hit != null) {
            Word(
                value = hit.kanji.ifBlank { lemma },
                kanji = hit.kanji,
                kana = hit.kana ?: "",
                romaji = "",
                glossCn = joinedCtx,
                glossEn = hit.englishGloss,
                jlpt = hit.jlptLevel,
                pos = "",
                tag = "anime-subtitle",
            )
        } else {
            Word(
                value = lemma,
                kanji = lemma,
                kana = "",
                romaji = "",
                glossCn = joinedCtx,
                pos = "",
                tag = "anime-subtitle",
            )
        }
    }
}

/**
 * Return up to `sentenceLength` characters of `text` centred on the
 * given token's character offset. Mirrors the behaviour of the English
 * `clipSentence` helper without depending on OpenNLP.
 */
private fun clipJapaneseContext(
    token: com.mujingx.ui.util.tokenizer.Token,
    text: String,
    sentenceLength: Int,
): String {
    val center = token.start
    val half = sentenceLength / 2
    val start = (center - half).coerceAtLeast(0)
    val end = (center + token.surface.length + half).coerceAtMost(text.length)
    return text.substring(start, end).replace("\n", " ").replace("\r", " ").trim()
}