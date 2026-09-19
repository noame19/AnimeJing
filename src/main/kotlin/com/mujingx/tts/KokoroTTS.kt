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
 * AnimeJing modifications (c) 2026: Kokoro-82M ONNX TTS bridge for
 * Japanese (and English) word pronunciation. Spawns the
 * `kokoro_tts.py` helper bundled in AppImage resources; that script
 * runs `kokoro-onnx` from a venv and writes a 24 kHz mono WAV to a
 * temp file. We play it through the existing AudioPlayerComponent.
 *
 * Why Python instead of pure Kotlin ONNX runtime: ONNX runtime Java
 * is ~30 MB of native libs; bundling it inflates AppImage by 30 MB
 * even for users who never touch TTS. Python kokoro-onnx is ~80 MB
 * but only loaded when the user actually clicks "play". A system
 * with python3 already pays zero extra cost.
 */

package com.mujingx.tts

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files

/**
 * AnimeJing's preferred TTS for Japanese vocabulary. Falls back to
 * [UbuntuTTS] / [MacTTS] / [MSTTSpeech] when the kokoro helper
 * script is missing or the spawn fails.
 */
class KokoroTTS(
    private val voice: String = "jf_alpha",     // japanese female default
    private val speed: Float = 1.0f,
    private val helperScript: File = defaultHelperScript(),
) {
    private val log = LoggerFactory.getLogger("KokoroTTS")

    /** Speak [text] synchronously; returns true if kokoro handled it. */
    fun speakAndWait(text: String): Boolean = try {
        if (!helperScript.exists()) {
            log.warn("kokoro helper script not found at {}", helperScript.absolutePath)
            return false
        }
        val wav = Files.createTempFile("animejing-kokoro-", ".wav").toFile()
        wav.deleteOnExit()
        val pb = ProcessBuilder(
            "python3",
            helperScript.absolutePath,
            "--text", text,
            "--voice", voice,
            "--speed", speed.toString(),
            "--out", wav.absolutePath,
        ).redirectErrorStream(true)
        val proc = pb.start()
        // Drain stdout so the child doesn't block on a full pipe.
        proc.inputStream.bufferedReader().use { reader ->
            reader.lineSequence().forEach { line ->
                if (line.isNotBlank()) log.debug("kokoro> {}", line)
            }
        }
        val exit = proc.waitFor()
        if (exit != 0) {
            log.warn("kokoro exited with {}", exit)
            wav.delete()
            return false
        }
        // Play the wav via Java Sound.
        playWav(wav)
        wav.delete()
        true
    } catch (e: Exception) {
        log.warn("kokoro speak failed: {}", e.message)
        false
    }

    /** Pure-Java WAV playback. Falls back if no audio device. */
    private fun playWav(wav: File) {
        val clip = javax.sound.sampled.AudioSystem.getAudioInputStream(wav).use { ais ->
            javax.sound.sampled.AudioSystem.getClip().apply {
                open(ais)
                start()
            }
        }
        // Wait for the line to stop; bail out on interrupt so the
        // caller's "speak and wait" semantics hold.
        try {
            while (clip.isActive) Thread.sleep(20)
        } catch (_: InterruptedException) {
            clip.stop()
        }
        clip.close()
    }

    companion object {
        /**
         * The kokoro helper script lives under the AppImage's app
         * resources dir; on a plain unpacked launcher it's the same
         * `AnimeJing/lib/app/` tree.
         *
         * Resolution order:
         *   1. AnimeJing/lib/app/kokoro_tts.py          (AppImage layout)
         *   2. resources/linux/kokoro_tts.py            (raw dev tree)
         *   3. ~/.local/share/animejing/kokoro_tts.py   (user install)
         */
        fun defaultHelperScript(): File {
            val home = System.getProperty("user.home") ?: "."
            val candidates = listOf(
                File("AnimeJing/lib/app/kokoro_tts.py"),
                File("resources/linux/kokoro_tts.py"),
                File(home, ".local/share/animejing/kokoro_tts.py"),
            )
            return candidates.firstOrNull { it.exists() } ?: candidates.first()
        }
    }
}