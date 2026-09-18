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
 * AnimeJing modifications (c) 2026: Japanese dictionary lookup via
 * jisho.org REST API (which is itself a JMdict-backed service). Lookups
 * are cached in a per-user SQLite database so subsequent runs are
 * fully offline. The cache file lives at ~/.cache/animejing/jisho.sqlite.
 */

package com.mujingx.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * One jisho.org lookup result, narrowed to the fields AnimeJing
 * actually needs. Use [lookup] / [lookupBatch] for production code.
 */
@Serializable
data class JishoResult(
    val slug: String = "",
    val isCommon: Boolean = false,
    val tags: List<String> = emptyList(),
    val jlptLevels: List<String> = emptyList(),
    val japanese: List<JishoJapanese> = emptyList(),
    val senses: List<JishoSense> = emptyList(),
) {
    val kanji: String get() = japanese.firstOrNull()?.word ?: slug
    val kana: String? get() = japanese.firstOrNull()?.reading
    val englishGloss: String get() =
        senses.flatMap { it.englishDefinitions }.joinToString("; ")
    val jlptLevel: Int get() = jlptLevels.firstNotNullOfOrNull { lvl ->
        Regex("""jlpt-n(\d)""").find(lvl)?.groupValues?.get(1)?.toIntOrNull()
    } ?: 0
}

@Serializable
data class JishoJapanese(
    val word: String? = null,
    val reading: String? = null,
)

@Serializable
data class JishoSense(
    val englishDefinitions: List<String> = emptyList(),
    val partsOfSpeech: List<String> = emptyList(),
)

/**
 * AnimeJing's Japanese dictionary client. Wraps the jisho.org public API
 * and mirrors successful lookups into a local SQLite cache under
 * ~/.cache/animejing/jisho.sqlite so repeated queries are offline.
 *
 * Why not the JMdict SQLite directly? The full JMdict release is
 * ~50MB after extraction; shipping it in the AppImage would inflate
 * the bundle by 50MB for every user even if they never look anything
 * up. jisho.org returns a subset (~the first 20 hits) which is more
 * than enough for hover-translate and vocabulary generation. We can
 * always ship a bundled JMdict later as an opt-in download.
 */
class JishoClient(
    private val cacheFile: File = defaultCacheFile(),
    private val httpTimeoutMs: Long = 5_000L,
) {
    private val log = LoggerFactory.getLogger("JishoClient")
    private val json = Json { ignoreUnknownKeys = true }
    private val client: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = httpTimeoutMs
            connectTimeoutMillis = httpTimeoutMs
            socketTimeoutMillis = httpTimeoutMs
        }
    }

    /** Lookup one keyword. Cache-first, then API, then null. */
    fun lookup(keyword: String): JishoResult? {
        val key = keyword.trim()
        if (key.isEmpty()) return null
        val cached = readCache(key)
        if (cached != null) return cached

        val result = try { fetchFromApi(key) } catch (e: Exception) {
            log.warn("jisho API failed for '{}': {}", key, e.message)
            null
        }
        if (result != null) writeCache(key, result)
        return result
    }

    /** Batch lookup; sequential to respect jisho.org rate limits. */
    fun lookupBatch(keywords: Iterable<String>): Map<String, JishoResult?> {
        val out = LinkedHashMap<String, JishoResult?>()
        for (kw in keywords) out[kw] = lookup(kw)
        return out
    }

    /** Network call. Suspending to allow callers to off-thread it. */
    fun fetchFromApi(keyword: String): JishoResult? {
        val encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8)
        val url = "https://jisho.org/api/v1/search/words?keyword=$encoded"
        val text = runBlocking {
            withTimeoutOrNull(httpTimeoutMs) {
                runCatching { client.get(url).bodyAsText() }.getOrNull()
            }
        } ?: return null

        val root = try { json.parseToJsonElement(text).jsonObject } catch (e: Exception) {
            log.warn("jisho JSON parse failed for '{}': {}", keyword, e.message)
            return null
        }
        val status = (root["meta"] as? JsonObject)
            ?.get("status") as? JsonPrimitive
        if (status?.content != "200") return null
        val data = root["data"] as? JsonArray ?: return null
        if (data.isEmpty()) return null

        val firstCommon = data.firstOrNull {
            (it.jsonObject["is_common"] as? JsonPrimitive)?.boolean == true
        } ?: data[0]
        val obj = firstCommon.jsonObject
        val slug = (obj["slug"] as? JsonPrimitive)?.contentOrNull ?: ""
        val isCommon = (obj["is_common"] as? JsonPrimitive)?.boolean ?: false
        val tags = (obj["tags"] as? JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        } ?: emptyList()
        val jlpt = (obj["jlpt"] as? JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        } ?: emptyList()

        val japanese = (obj["japanese"] as? JsonArray)?.mapNotNull { el ->
            val j = (el as? JsonObject) ?: return@mapNotNull null
            JishoJapanese(
                word = (j["word"] as? JsonPrimitive)?.contentOrNull,
                reading = (j["reading"] as? JsonPrimitive)?.contentOrNull,
            )
        } ?: emptyList()

        val senses = (obj["senses"] as? JsonArray)?.mapNotNull { el ->
            val s = (el as? JsonObject) ?: return@mapNotNull null
            JishoSense(
                englishDefinitions = (s["english_definitions"] as? JsonArray)?.mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull
                } ?: emptyList(),
                partsOfSpeech = (s["parts_of_speech"] as? JsonArray)?.mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull
                } ?: emptyList(),
            )
        } ?: emptyList()

        return JishoResult(slug, isCommon, tags, jlpt, japanese, senses)
    }

    private fun readCache(keyword: String): JishoResult? = withConnection { conn ->
        conn.prepareStatement(
            "SELECT payload FROM jisho_cache WHERE keyword = ? LIMIT 1"
        ).use { stmt ->
            stmt.setString(1, keyword)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                val payload = rs.getString(1) ?: return@withConnection null
                return@withConnection runCatching {
                    json.decodeFromString(JishoResult.serializer(), payload)
                }.getOrNull()
            }
            null
        }
    }

    private fun writeCache(keyword: String, result: JishoResult) = withConnection { conn ->
        val payload = json.encodeToString(JishoResult.serializer(), result)
        conn.prepareStatement(
            "INSERT OR REPLACE INTO jisho_cache(keyword, payload, fetched_at) VALUES(?, ?, ?)"
        ).use { stmt ->
            stmt.setString(1, keyword)
            stmt.setString(2, payload)
            stmt.setLong(3, System.currentTimeMillis())
            stmt.executeUpdate()
        }
    }

    private inline fun <T> withConnection(block: (Connection) -> T): T? {
        val conn = try {
            cacheFile.parentFile?.mkdirs()
            DriverManager.getConnection("jdbc:sqlite:${cacheFile.absolutePath}").also {
                it.autoCommit = false
                it.createStatement().use { stmt ->
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS jisho_cache (
                            keyword TEXT PRIMARY KEY,
                            payload TEXT NOT NULL,
                            fetched_at INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }
                it.commit()
            }
        } catch (e: SQLException) {
            log.warn("jisho cache open failed: {}", e.message)
            return null
        }
        return conn.use(block).also { conn.close() }
    }

    companion object {
        fun defaultCacheFile(): File {
            val home = System.getProperty("user.home") ?: "."
            return File(home, ".cache/animejing/jisho.sqlite")
        }
    }
}