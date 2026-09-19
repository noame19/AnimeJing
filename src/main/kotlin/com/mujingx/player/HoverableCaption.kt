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

package com.mujingx.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mujingx.data.*
import com.mujingx.player.danmaku.DisplayMode
import com.mujingx.player.danmaku.WordDetail
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalUriHandler
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URLEncoder
import java.nio.charset.Charset
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HoverableText(
    text: String,
    color : Color = Color.White,
    hoverColor : Color = Color(0xFF29417F),// 悬停时的背景颜色,使用经典的文本选择颜色
    style: TextStyle = MaterialTheme.typography.h4,
    fontFamily: FontFamily? = null,
    isActive: Boolean = true,
    playerState: PlayerState,
    playAudio:(String) -> Unit = {},
    modifier: Modifier = Modifier,
    onPopupHoverChanged: (Boolean) -> Unit = {},
    addWord: (Word) -> Unit = {},
    addToFamiliar: (Word) -> Unit = {},
    onClick: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    var isInPopup by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current


    Box(modifier = Modifier) {
        val interactionSource = remember { MutableInteractionSource()}
        val hoverJob = remember { mutableStateOf<Job?>(null) }
        Text(
            text = text,
            color = color,
            style = style,
            fontFamily = fontFamily,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null // 不显示点击波纹效果
                ) {
                    onClick()
                }
                .background(
                    if (expanded) hoverColor
                    else Color.Transparent
                )
                .hoverable(interactionSource)
                .onPointerEvent(PointerEventType.Enter) {
                    if(!isActive) return@onPointerEvent
                    hoverJob.value?.cancel() // 取消之前的 Job
                    hoverJob.value = scope.launch {
                        delay(300) // 设置悬停最少停留时间为 300 毫秒
                        expanded = true
                    }
                }
                .onPointerEvent(PointerEventType.Exit) {
                    if(!isActive) return@onPointerEvent
                    hoverJob.value?.cancel() // 取消悬停的 Job
                    // 添加延时，让 Popup 的 Enter 事件有机会先执行
                    scope.launch {
                        delay(50)
                        if (!isInPopup) {
                            expanded = false
                        }
                    }

                }
        )

        if (expanded && isActive) {
            // 处理前面的分词错误，去掉结尾的 --
            if(text.endsWith("--")){
                text.dropLast(2)
            }

            // AnimeJing: dispatch to the right dictionary. English
            // vocabulary keeps using the local SQLite ecdict; everything
            // else (kanji, hiragana, romaji, mixed) falls back to
            // jisho.org (cached in ~/.cache/animejing/jisho.sqlite).
            val dictWord = lookupWordForCaption(text, playerState)

            Popup(
                alignment = Alignment.TopCenter,
                offset = with(density) { IntOffset(0, -350.dp.toPx().toInt()) },
                onDismissRequest = { expanded = false },
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                Column( modifier = Modifier
                    .size(400.dp, 353.dp)
                    .onPointerEvent(PointerEventType.Enter) {
                        isInPopup = true
                        onPopupHoverChanged(true)
                    }
                    .onPointerEvent(PointerEventType.Exit) {
                        isInPopup = false
                        expanded = false
                        onPopupHoverChanged(false)
                    }){
                    Box(modifier = Modifier.size(400.dp, 350.dp),) {
                        if(dictWord != null){
                            WordDetail(
                                word =dictWord ,
                                displayMode = DisplayMode.DICT,
                                playerState = playerState,
                                pointerExit = {},
                                height = 350.dp,
                                addWord = addWord,
                                addToFamiliar = addToFamiliar,
                                playAudio =playAudio ,
                            )
                        }else{
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colors.background),
                                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "内置词典里面没有 \"$text\"",
                                        style = MaterialTheme.typography.body1,
                                        color =  MaterialTheme.colors.onBackground
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ){
                                        // 复制按钮
                                        Button(
                                            onClick = {
                                                val stringSelection = StringSelection(text)
                                                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                                clipboard.setContents(stringSelection, null)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = MaterialTheme.colors.primary
                                            ),
//                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("复制", color = Color.White)
                                        }
                                    }


                                    Text(
                                        text = "网络查词:",
                                        style = MaterialTheme.typography.body2,
                                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
                                    )


                                    // 网络查词按钮组
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val encoded = URLEncoder.encode(text, Charset.forName("UTF-8"))
                                                uriHandler.openUri("https://www.youdao.com/result?word=$encoded&lang=en")
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = MaterialTheme.colors.secondary
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("有道", color = Color.White)
                                        }

                                        Button(
                                            onClick = {
                                                val encoded = URLEncoder.encode(text, Charset.forName("UTF-8"))
                                                uriHandler.openUri("https://dictionary.cambridge.org/dictionary/english-chinese-simplified/$encoded")
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = MaterialTheme.colors.secondary
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("剑桥", color = Color.White)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val encoded = URLEncoder.encode(text, Charset.forName("UTF-8"))
                                                uriHandler.openUri("https://www.merriam-webster.com/dictionary/$encoded")
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = MaterialTheme.colors.secondary
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("韦氏", color = Color.White)
                                        }

                                        Button(
                                            onClick = {
                                                val encoded = URLEncoder.encode(text, Charset.forName("UTF-8"))
                                                uriHandler.openUri("https://www.vocabulary.com/dictionary/$encoded")
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = MaterialTheme.colors.secondary
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Vocabulary", color = Color.White)
                                        }
                                    }


                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val encoded = URLEncoder.encode(text, Charset.forName("UTF-8"))
                                                uriHandler.openUri("https://www.oxfordlearnersdictionaries.com/definition/english/$encoded")
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                backgroundColor = MaterialTheme.colors.secondary
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Oxford", color = Color.White)
                                        }

                                        if (isMacOS()) {
                                            Button(
                                                onClick = {
                                                    openMacOSDictionary(text)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    backgroundColor = MaterialTheme.colors.secondary
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("系统词典", color = Color.White)
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }

                                    }

                                }

                            }
                        }

                    }
                    Box(Modifier.height(3.dp).width(400.dp).background(Color.Transparent))
                }
            }
        }
    }
}


@Composable
fun HoverableCaption(
    caption: String,
    playAudio: (String) -> Unit,
    playerState: PlayerState,
    modifier: Modifier = Modifier,
    onPopupHoverChanged: (Boolean) -> Unit = {},
    addWord: (Word) -> Unit = {},
    addToFamiliar: (Word) -> Unit = {},
    primaryCaptionVisible : Boolean = true,
    secondaryCaptionVisible : Boolean= true,
    isBilingual : Boolean= false,
    swapEnabled: Boolean = false,
) {
    Column(modifier) {
        val lines = caption.split("\n")

        // 处理双语字幕的显示顺序
        if (isBilingual && lines.size >= 2) {
            // 确定主次字幕的显示顺序
            val primaryLine = lines[0]
            val secondaryLine =  lines[1]

            // 根据交换状态和可见性决定显示顺序
            val displayLines = mutableListOf<String>()

            if (swapEnabled) {
                // 交换模式：次字幕在前，主字幕在后
                displayLines.add(secondaryLine)
                displayLines.add(primaryLine)
            } else {
                // 正常模式：主字幕在前，次字幕在后
                displayLines.add(primaryLine)
                displayLines.add(secondaryLine)
            }

            displayLines.forEachIndexed { index, line ->
                val color = when (index) {
                    0 -> {
                        // 主字幕（白色）
                        if (primaryCaptionVisible)Color.White else Color.Transparent
                    }
                    1 -> {
                        // 次字幕（灰色）
                        if (secondaryCaptionVisible) Color(0xFFAAAAAA) else Color.Transparent
                    }
                    else -> {
                        Color.Transparent
                    }
                }


                renderCaptionLine(
                    line,
                    color,
                    hoverColor = Color(0xFF29417F),
                    MaterialTheme.typography.h4,
                    null,
                    playAudio,
                    playerState,
                    onPopupHoverChanged,
                    addWord,
                    addToFamiliar,
                    alwaysActive = false
                )

            }
        } else {
            // 单语字幕或非双语情况
            lines.forEachIndexed { index, line ->
                val color = if (isBilingual) {
                    when (index) {
                        0 if primaryCaptionVisible -> Color.White
                        1 if secondaryCaptionVisible -> Color(0xFFAAAAAA)
                        else -> Color.Transparent
                    }
                } else {
                    if (primaryCaptionVisible) Color.White else Color.Transparent
                }


                renderCaptionLine(
                    line,
                    color,
                    hoverColor = Color(0xFF29417F),
                    MaterialTheme.typography.h4,
                    fontFamily = null ,
                    playAudio,
                    playerState,
                    onPopupHoverChanged,
                    addWord,
                    addToFamiliar,
                    alwaysActive = false
                )

            }
        }
    }
}

/**
 * 渲染字幕行，将文本按单词分割并渲染，支持单词悬停查看详情
 *
 * 该函数会智能地解析字幕文本，分离出单词和标点符号，并为每个单词添加悬停交互功能。
 * 当用户将鼠标悬停在单词上时，会显示单词详情弹窗（如果词典中存在该单词）。
 *
 * @param line 要渲染的字幕文本行
 * @param color 文本颜色，用于控制字幕的可见性
 * @param hoverColor 鼠标悬停时的背景颜色，默认为 Color(0xFF29417F)
 * @param style 文本样式，默认为 MaterialTheme.typography.h4
 * @param fontFamily 字体族，可选参数
 * @param playAudio 播放单词音频的回调函数，参数为单词文本
 * @param playerState 播放器状态，用于管理单词详情弹窗
 * @param onPopupHoverChanged 弹窗悬停状态改变的回调，参数为是否正在悬停
 * @param addWord 添加单词到词库的回调函数
 * @param addToFamiliar 添加单词到熟悉词库的回调函数
 * @param alwaysActive 是否始终激活悬停功能
 *   - true: 始终可以悬停查看单词（用于 SubtitleHoverableCaption）
 *   - false: 只在字幕可见时（color != Color.Transparent）才激活悬停功能（用于 VideoPlayer 的 HoverableCaption）
 * @param onClick 点击单词时的回调函数，用于字幕浏览器界面切换当前字幕索引等操作，视频播放器界面不需要
 *
 * 实现细节：
 * - 使用正则表达式按空格分割文本为单词
 * - 每个单词会被进一步分解为：前导标点、单词主体、尾随标点
 * - 单词主体部分会被包装为 HoverableText 组件，支持悬停交互
 * - 标点符号直接渲染为普通 Text 组件
 * - 单词之间自动添加空格（最后一个单词除外）
 */
@Composable
private fun renderCaptionLine(
    line: String,
    color: Color,
    hoverColor : Color = Color(0xFF29417F),
    style: TextStyle = MaterialTheme.typography.h4,
    fontFamily: FontFamily? = null,
    playAudio: (String) -> Unit,
    playerState: PlayerState,
    onPopupHoverChanged: (Boolean) -> Unit,
    addWord: (Word) -> Unit,
    addToFamiliar: (Word) -> Unit,
    alwaysActive: Boolean = false,
    onClick: () -> Unit = {},
) {
    Row (
        Modifier
            .padding(horizontal = 3.dp)// 这里是为了给文本选择留出一点空间
    ){
        // 改进的分词逻辑
        val words = line.split(Regex("\\s+")) // 按空格分割
        words.forEachIndexed { index, rawWord ->
            if (rawWord.isNotEmpty()) {
                // 提取开头的标点符号
                val leadingPunctuation = rawWord.takeWhile { !(it in 'a'..'z' || it in 'A'..'Z' || it.isDigit()) }
                val remaining = rawWord.drop(leadingPunctuation.length)

                // 从剩余部分提取单词（字母、撇号、连字符）
                val wordPart = remaining.takeWhile { it in 'a'..'z' || it in 'A'..'Z' || it.isDigit() || it == '\'' || it == '-' }
                val trailingPunctuation = remaining.drop(wordPart.length)

                // 渲染开头标点
                if (leadingPunctuation.isNotEmpty()) {
                    Text(
                        leadingPunctuation,
                        color = color,
                        style = style,
                        fontFamily = fontFamily,
                    )
                }

                // 渲染单词部分
                if (wordPart.isNotEmpty()) {
                    HoverableText(
                        text = wordPart,
                        color = color,
                        hoverColor = hoverColor,
                        style = style,
                        fontFamily = fontFamily,
                        isActive = if (alwaysActive) true else color != Color.Transparent,
                        playAudio = playAudio,
                        playerState = playerState,
                        modifier = Modifier,
                        onPopupHoverChanged = onPopupHoverChanged,
                        addWord = addWord,
                        addToFamiliar = addToFamiliar,
                        onClick = onClick,
                    )
                }

                // 渲染结尾标点
                if (trailingPunctuation.isNotEmpty()) {
                    Text(
                        trailingPunctuation,
                        color = color,
                        style = style,
                        fontFamily = fontFamily,
                    )
                }

                // 添加空格（除了最后一个词）
                if (index < words.size - 1) {
                    Text(
                        " ",
                        color = color,
                        style = style,
                        fontFamily = fontFamily,
                    )
                }
            }
        }
    }
}


@Composable
fun SubtitleHoverableCaption(
     content: String,
     caption: Caption,
     fontFamily: FontFamily,
     playerState: PlayerState,
     wordScreenState: com.mujingx.ui.wordscreen.WordScreenState,
     playAudio: (String) -> Unit,
     mediaPath: String = "",
     showNotification: (String, Long) -> Unit = { _, _ -> },
     index: Int,
     currentIndex: Int,
     currentIndexChanged: (Int) -> Unit,
     multipleLines: com.mujingx.ui.subtitlescreen.MultipleLines,
     focusRequester: androidx.compose.ui.focus.FocusRequester,
){
    val scope = rememberCoroutineScope()

    renderCaptionLine(
        line = content,
        hoverColor = Color.Transparent,
        color = Color.Transparent,
        style = MaterialTheme.typography.h5,
        fontFamily = fontFamily,
        playAudio = playAudio,
        playerState = playerState,
        onPopupHoverChanged ={},
        alwaysActive = true,
        onClick = {
            // 点击单词时切换 currentIndex，模拟 BasicTextField 的行为
            if (!multipleLines.enabled && currentIndex != index) {
                currentIndexChanged(index)
                // 请求焦点，确保与 BasicTextField 的行为一致
                focusRequester.requestFocus()
            }
        },
        addWord = { word ->
            scope.launch {
                try {
                    // 检查词库路径是否存在
                    if(wordScreenState.vocabularyPath.isEmpty()){
                        showNotification("添加失败，\n要先在记忆单词界面选择一个词库，\n否则无法使用这个功能。\n", 3000L)
                        return@launch
                    }

                    val newWord = word.deepCopy()

                    // 添加字幕信息到单词（最多3个）
                    if(newWord.captions.size < 3){
                        val dataCaption = com.mujingx.data.Caption(
                            start = caption.start,
                            end = caption.end,
                            content = caption.content
                        )
                        newWord.captions.add(dataCaption)
                    }

                    // 加载词库
                    val vocabularyPath = wordScreenState.vocabularyPath
                    val vocabulary = loadMutableVocabulary(vocabularyPath)

                    // 检查词库类型
                    if(vocabulary.name == "FamiliarVocabulary" || vocabulary.name == "HardVocabulary"){
                        showNotification("单词记忆界面的词库是 ${vocabulary.name}, 无法添加单词。", 3000L)
                        return@launch
                    }

                    // 如果词库是 SUBTITLES 类型且媒体路径不匹配，转换为外部字幕
                    if(vocabulary.type == VocabularyType.SUBTITLES &&
                       vocabulary.relateVideoPath != mediaPath){
                        // 转换为 DOCUMENT 类型
                        vocabulary.type = VocabularyType.DOCUMENT
                        vocabulary.relateVideoPath = ""
                        vocabulary.subtitlesTrackId = -1

                        // 转换现有单词的字幕为外部字幕
                        vocabulary.wordList.forEach { existingWord ->
                            existingWord.captions.forEach { cap: com.mujingx.data.Caption ->
                                val externalCaption = ExternalCaption(
                                    relateVideoPath = "",
                                    subtitlesTrackId = -1,
                                    subtitlesName = vocabulary.name,
                                    start = cap.start,
                                    end = cap.end,
                                    content = cap.content
                                )
                                existingWord.externalCaptions.add(externalCaption)
                            }
                            existingWord.captions.clear()
                        }

                        // 转换新单词的字幕为外部字幕
                        newWord.captions.forEach { cap: com.mujingx.data.Caption ->
                            val externalCaption = ExternalCaption(
                                relateVideoPath = mediaPath,
                                subtitlesTrackId = -1,
                                subtitlesName = if(mediaPath.isNotEmpty()) java.io.File(mediaPath).name else "",
                                start = cap.start,
                                end = cap.end,
                                content = cap.content
                            )
                            newWord.externalCaptions.add(externalCaption)
                        }
                        newWord.captions.clear()
                    } else if(vocabulary.type == VocabularyType.DOCUMENT){
                        // 如果是 DOCUMENT 类型，直接转换为外部字幕
                        newWord.captions.forEach { cap: com.mujingx.data.Caption ->
                            val externalCaption = ExternalCaption(
                                relateVideoPath = mediaPath,
                                subtitlesTrackId = -1,
                                subtitlesName = if(mediaPath.isNotEmpty()) java.io.File(mediaPath).name else vocabulary.name,
                                start = cap.start,
                                end = cap.end,
                                content = cap.content
                            )
                            newWord.externalCaptions.add(externalCaption)
                        }
                        newWord.captions.clear()
                    }

                    // 检查单词是否已存在
                    if(vocabulary.wordList.contains(newWord)){
                        showNotification("单词: ${newWord.value} 已经存在于词库:\n${vocabulary.name} 中。", 3000L)
                        return@launch
                    }

                    // 添加单词
                    vocabulary.wordList.add(newWord)
                    vocabulary.size = vocabulary.wordList.size

                    // 保存词库
                    saveVocabulary(vocabulary.serializeVocabulary, vocabularyPath)

                    // 更新 wordScreenState 中的词库
                    wordScreenState.vocabulary = vocabulary

                    showNotification("已添加单词: ${newWord.value} 到正在记忆的词库", 3000L)
                } catch (e: Exception) {
                    e.printStackTrace()
                    showNotification("添加单词失败: ${word.value}\n错误信息:${e.message}", 3000L)
                }
            }
        },
        addToFamiliar = { word ->
            scope.launch {
                try {
                    val familiarWord = word.deepCopy()

                    // 添加字幕信息到单词（最多3个）
                    if(familiarWord.captions.size < 3){
                        val dataCaption = com.mujingx.data.Caption(
                            start = caption.start,
                            end = caption.end,
                            content = caption.content
                        )
                        familiarWord.captions.add(dataCaption)
                    }

                    // 转换为外部字幕
                    familiarWord.captions.forEach { cap: com.mujingx.data.Caption ->
                        val externalCaption = ExternalCaption(
                            relateVideoPath = mediaPath,
                            subtitlesTrackId = -1,
                            subtitlesName = if(mediaPath.isNotEmpty()) java.io.File(mediaPath).name else "",
                            start = cap.start,
                            end = cap.end,
                            content = cap.content
                        )
                        familiarWord.externalCaptions.add(externalCaption)
                    }
                    familiarWord.captions.clear()

                    // 加载熟悉词库
                    val file = getFamiliarVocabularyFile()
                    val familiar = loadVocabulary(file.absolutePath)

                    // 检查是否已存在
                    if(familiar.wordList.contains(familiarWord)){
                        showNotification("熟悉词库已经存在单词: ${familiarWord.value}", 3000L)
                        return@launch
                    }

                    // 添加到熟悉词库
                    familiar.wordList.add(familiarWord)
                    familiar.size = familiar.wordList.size

                    if(familiar.name.isEmpty()){
                        familiar.name = "FamiliarVocabulary"
                    }

                    // 保存熟悉词库
                    saveVocabulary(familiar, file.absolutePath)

                    showNotification("已添加到熟悉词库: ${word.value}", 3000L)
                } catch (e: Exception) {
                    e.printStackTrace()
                    showNotification("保存到熟悉词库失败: ${word.value}\n错误信息:${e.message}", 3000L)
                }
            }
        }
    )
}

/**
 * AnimeJing: dispatch to the right dictionary backend based on the
 * currently loaded vocabulary's language. English queries keep using
 * the bundled ecdict; Japanese and others fall back to jisho.org via
 * JishoClient (which keeps a local SQLite cache).
 */
private fun lookupWordForCaption(text: String, playerState: PlayerState): Word? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val lang = playerState.vocabulary?.language?.lowercase().orEmpty()
    return when {
        lang == "japanese" || lang == "ja" -> jishoToWord(trimmed)
        lang == "english" || lang == "en" || lang.isEmpty() -> Dictionary.query(trimmed.lowercase())
        else -> jishoToWord(trimmed) ?: Dictionary.query(trimmed.lowercase())
    }
}

private fun jishoToWord(text: String): Word? {
    return try {
        val client = JishoClient()
        val hit = client.lookup(text) ?: return null
        Word(
            value = hit.kanji,
            usphone = "",
            ukphone = "",
            definition = hit.englishGloss,
            translation = hit.englishGloss,
            pos = hit.japanese.firstOrNull()?.let { "" } ?: "",
            collins = 0,
            oxford = false,
            tag = "jisho",
            bnc = 0,
            frq = 0,
            exchange = "",
            kanji = hit.kanji,
            kana = hit.kana ?: "",
            romaji = "",
            jlpt = hit.jlptLevel,
            glossEn = hit.englishGloss,
            glossCn = "",
        )
    } catch (e: Exception) {
        null
    }
}
