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

package com.mujingx.data

import androidx.compose.runtime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.mujingx.state.getResourcesFile
import com.mujingx.state.getSettingsDirectory
import java.io.File
import javax.swing.JOptionPane

/**
 * 词库
 */
@Serializable
data class Vocabulary(
    var name: String = "",
    val type: VocabularyType = VocabularyType.DOCUMENT,
    val language: String,
    var size: Int,
    var relateVideoPath: String = "",
    val subtitlesTrackId: Int = 0,
    var wordList: MutableList<Word> = mutableListOf(),
)

/**
 * 可观察的词库
 */
class MutableVocabulary(vocabulary: Vocabulary) {
    var name by mutableStateOf(vocabulary.name)
    var type by mutableStateOf(vocabulary.type)
    var language by mutableStateOf(vocabulary.language)
    var size by mutableStateOf(vocabulary.size)
    var relateVideoPath by mutableStateOf(vocabulary.relateVideoPath)
    var subtitlesTrackId by mutableStateOf(vocabulary.subtitlesTrackId)
    var wordList = vocabulary.wordList.toMutableStateList()

    /**
     * 用于持久化
     */
    val serializeVocabulary
        get() = Vocabulary(name, type, language, size, relateVideoPath, subtitlesTrackId, wordList)

}


/**
links 存储字幕链接,格式：(subtitlePath)[videoPath][subtitleTrackId][index]
captions 字幕列表
 */
@Serializable
data class Word(
    var value: String,
    var usphone: String = "",
    var ukphone: String = "",
    var definition: String = "",
    var translation: String = "",
    var pos: String = "",
    var collins: Int = 0,
    var oxford: Boolean = false,
    var tag: String = "",
    var bnc: Int? = 0,
    var frq: Int? = 0,
    var exchange: String = "",
    // --- AnimeJing Japanese fields (default values keep old English JSON compatible) ---
    /** 漢字 / 表記形 (Japanese surface form). For English words leave empty. */
    var kanji: String = "",
    /** 仮名読み (Japanese reading, katakana/hiragana). */
    var kana: String = "",
    /** Romaji transcription of kana, for the secondary line under the main term. */
    var romaji: String = "",
    /** 語形変化 (conjugated forms), e.g. 食べる -> [食べ][食べた][食べれば][食べろ]. Empty for English. */
    var conjugations: MutableList<String> = mutableListOf(),
    /** JLPT level (5..1); 0 means ungraded. */
    var jlpt: Int = 0,
    /** Pitch-accent pattern like [0], [1], [2], [3]. Empty if unknown. */
    var pitchAccent: String = "",
    /** English gloss (from JMdict sense.eng). */
    var glossEn: String = "",
    /** Chinese gloss (from JMdict sense.gloss translation). */
    var glossCn: String = "",
    /** Anime-style frequency rank from 1 (most common in anime subtitles) downward; 0 = unknown. */
    var animeFrequency: Int = 0,
    var externalCaptions: MutableList<ExternalCaption> = mutableListOf(),
    var captions: MutableList<Caption> = mutableListOf()
) {
    override fun equals(other: Any?): Boolean {
        val otherWord = other as Word
        return this.value.lowercase() == otherWord.value.lowercase()
    }

    override fun hashCode(): Int {
        return value.lowercase().hashCode()
    }
}
fun Word.deepCopy():Word{
    val newWord =  Word(
        value, usphone, ukphone, definition, translation, pos, collins, oxford, tag, bnc, frq, exchange
    )
    externalCaptions.forEach { externalCaption ->
        newWord.externalCaptions.add(externalCaption)
    }
    captions.forEach { caption ->
        newWord.captions.add(caption)
    }
    return newWord
}


@Serializable
data class Caption(var start: String, var end: String, var content: String) {
    override fun toString(): String {
        return content
    }
}

/**
 * @param relateVideoPath 视频地址
 * @param subtitlesTrackId 字幕轨道 ID
 * @param subtitlesName 字幕名称，链接字幕词库时设置。在链接字幕窗口，用来删除这个字幕文件的所有字幕
 * @param start 开始
 * @param end 结束
 * @param content 字幕内容
 */
@Serializable
data class ExternalCaption(
    val relateVideoPath: String,
    val subtitlesTrackId: Int,
    var subtitlesName: String,
    var start: String,
    var end: String,
    var content: String
) {
    override fun toString(): String {
        return content
    }
}




fun loadMutableVocabulary(path: String): MutableVocabulary {
    val file = getResourcesFile(path)
    val name = file.nameWithoutExtension
    // 如果当前词库被删除，重启之后又没有选择新的词库，再次重启时才会调用，
    // 主要是为了避免再次重启是出现”找不到词库"对话框
    return if(path.isEmpty()){
        val vocabulary = Vocabulary(
            name = name,
            type = VocabularyType.DOCUMENT,
            language = "",
            size = 0,
            relateVideoPath = "",
            subtitlesTrackId = 0,
            wordList = mutableListOf()
        )
        MutableVocabulary(vocabulary)
    }else if (file.exists()) {

        try {
            val vocabulary = Json.decodeFromString<Vocabulary>(file.readText())
            if(vocabulary.size != vocabulary.wordList.size){
                vocabulary.size = vocabulary.wordList.size
            }
            // 如果用户修改了词库的文件名，以用户修改的为准。
            if(vocabulary.name != name){
                vocabulary.name = name
            }
            MutableVocabulary(vocabulary)
        } catch (exception: Exception) {
            exception.printStackTrace()
            val vocabulary = Vocabulary(
                name = name,
                type = VocabularyType.DOCUMENT,
                language = "",
                size = 0,
                relateVideoPath = "",
                subtitlesTrackId = 0,
                wordList = mutableListOf()
            )
            JOptionPane.showMessageDialog(null, "词库解析错误：\n地址：$path\n" + exception.message)
            if(vocabulary.size != vocabulary.wordList.size){
                vocabulary.size = vocabulary.wordList.size
            }
            MutableVocabulary(vocabulary)
        }

    } else {
        val vocabulary = Vocabulary(
            name = name,
            type = VocabularyType.DOCUMENT,
            language = "",
            size = 0,
            relateVideoPath = "",
            subtitlesTrackId = 0,
            wordList = mutableListOf()
        )
        JOptionPane.showMessageDialog(null, "找不到词库：\n$path")
        MutableVocabulary(vocabulary)
    }

}


fun loadVocabulary(path: String): Vocabulary {
    val file = getResourcesFile(path)
    val name = file.nameWithoutExtension
    if (file.exists()) {
        return try {
            //  TODO 链接字幕词库时选取了一个错误文件，导致程序卡死。定位到这里  error:  Required array length 2147483638 + 16142 is too large
           val vocabulary =  Json.decodeFromString<Vocabulary>(file.readText())
            // 如果用户修改了词库的文件名，以用户修改的为准。
            if(vocabulary.name != name){
                vocabulary.name = name
            }
            vocabulary
        } catch (exception: Exception) {
            exception.printStackTrace()
            JOptionPane.showMessageDialog(null, "词库解析错误：\n地址：$path\n" + exception.message)
            Vocabulary(
                name = name,
                type = VocabularyType.DOCUMENT,
                language = "",
                size = 0,
                relateVideoPath = "",
                subtitlesTrackId = 0,
                wordList = mutableListOf()
            )
        }
    } else {

        return Vocabulary(
            name = name,
            type = VocabularyType.DOCUMENT,
            language = "",
            size = 0,
            relateVideoPath = "",
            subtitlesTrackId = 0,
            wordList = mutableListOf()
        )
    }

}
/** 主要加载困难词库和熟悉词库 */
fun loadMutableVocabularyByName(name: String):MutableVocabulary{
    val file = if (name == "FamiliarVocabulary") {
        getFamiliarVocabularyFile()
    } else getHardVocabularyFile()

    return if (file.exists()) {
        try {
            val vocabulary = Json.decodeFromString<Vocabulary>(file.readText())
            MutableVocabulary(vocabulary)
        } catch (exception: Exception) {
            exception.printStackTrace()
            val vocabulary = Vocabulary(
                name = name,
                type = VocabularyType.DOCUMENT,
                language = "",
                size = 0,
                relateVideoPath = "",
                subtitlesTrackId = 0,
                wordList = mutableListOf()
            )
            JOptionPane.showMessageDialog(null, "词库解析错误：\n地址：${file.absoluteFile}\n" + exception.message)
            MutableVocabulary(vocabulary)
        }

    } else {
        val vocabulary = Vocabulary(
            name = name,
            type = VocabularyType.DOCUMENT,
            language = "",
            size = 0,
            relateVideoPath = "",
            subtitlesTrackId = 0,
            wordList = mutableListOf()
        )
        MutableVocabulary(vocabulary)
}}


/** 返回熟悉词库文件 */
fun getFamiliarVocabularyFile():File{
    val settingsDir = getSettingsDirectory()
    return File(settingsDir, "FamiliarVocabulary.json")
}
/** 返回困难词库文件 */
fun getHardVocabularyFile():File{
    val settingsDir = getSettingsDirectory()
    return File(settingsDir, "HardVocabulary.json")
}

fun saveVocabularyToTempDirectory(vocabulary: Vocabulary, directory: String) {
    val format = Json {
        prettyPrint = true
        encodeDefaults = true
    }
    val json = format.encodeToString(vocabulary)
    val file = File("src/com.mujingx.main/resources/temp/$directory/${vocabulary.name}.json")
    if (!file.parentFile.exists()) {
        file.parentFile.mkdirs()
    }
    File("src/com.mujingx.main/resources/temp/$directory/${vocabulary.name}.json").writeText(json)
}

fun saveVocabulary(vocabulary: Vocabulary, path: String) {
    val format = Json {
        prettyPrint = true
        encodeDefaults = true
    }
    val json = format.encodeToString(vocabulary)
    val file = getResourcesFile(path)
    file.writeText(json)
}
