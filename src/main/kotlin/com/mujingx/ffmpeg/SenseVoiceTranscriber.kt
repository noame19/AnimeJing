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
 * AnimeJing modifications (c) 2026: SenseVoiceSmall ASR wrapper. Used
 * to transcribe a media file (anime MKV without subtitles) into an SRT
 * file that the rest of the app can then parse into a vocabulary.
 */

package com.mujingx.ffmpeg

import org.slf4j.LoggerFactory
import java.io.File

/**
 * AnimeJing's optional ASR for media files that have no embedded
 * subtitles. Wraps the SenseVoiceSmall ONNX model via a Python helper
 * (sherpa-onnx), parallel in shape to [com.mujingx.tts.KokoroTTS].
 *
 * Models live under ~/.cache/animejing/sensevoice and must be fetched
 * beforehand via:
 *
 *     python3 scripts/fetch_models.py sensevoice
 *
 * Falls back to a clear RuntimeException if the helper is missing so
 * the UI can surface "open Settings to install ASR model".
 */
object SenseVoiceTranscriber {
    private val log = LoggerFactory.getLogger("SenseVoiceTranscriber")

    /** Run SenseVoice on [inputMedia]; write SRT to [outputSrt]. */
    fun transcribe(
        inputMedia: File,
        outputSrt: File,
        language: String = "ja",
        helperScript: File = defaultHelper(),
    ): Boolean {
        return try {
            if (!helperScript.exists()) {
                log.warn("sensevoice helper script missing at {}", helperScript.absolutePath)
                false
            } else if (!inputMedia.exists()) {
                log.warn("input media not found: {}", inputMedia.absolutePath)
                false
            } else {
                doTranscribe(inputMedia, outputSrt, language, helperScript)
            }
        } catch (e: Exception) {
            log.warn("sensevoice transcribe failed: {}", e.message)
            false
        }
    }

    private fun doTranscribe(
        inputMedia: File,
        outputSrt: File,
        language: String,
        helperScript: File,
    ): Boolean {
        val pb = ProcessBuilder(
            "python3",
            helperScript.absolutePath,
            "--input", inputMedia.absolutePath,
            "--lang", language,
            "--out", outputSrt.absolutePath,
        ).redirectErrorStream(true)
        val proc = pb.start()
        proc.inputStream.bufferedReader().use { reader ->
            reader.lineSequence().forEach { line ->
                if (line.isNotBlank()) log.info("sensevoice> {}", line)
            }
        }
        val exit = proc.waitFor()
        if (exit != 0) {
            log.warn("sensevoice exited with {}", exit)
            return false
        }
        log.info("wrote SRT to {}", outputSrt.absolutePath)
        return true
    }

    fun defaultHelper(): File {
        val candidates = listOf(
            File("AnimeJing/lib/app/sensevoice_asr.py"),
            File("resources/linux/sensevoice_asr.py"),
            File(System.getProperty("user.home") ?: ".", ".local/share/animejing/sensevoice_asr.py"),
        )
        return candidates.firstOrNull { it.exists() } ?: candidates.first()
    }
}