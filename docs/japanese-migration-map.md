# AnimeJing — 日语改造 diff 地图

> 本文档是把 `tangshimin/MuJing`（4.6k stars、Kotlin/Compose Desktop、英语视频学单词）改造成 AnimeJing（通过动漫番剧学日语）所要触碰的全部代码点。
>
> 范围只到「能跑起来、能加载日语词库、能跟读一个动漫片段」MVP。后面再分阶段加 JLPT 词库、Anki 导出、用户自建词库等。
>
> 本机不编译；每步走 GitHub Actions 验。

## 1. 仓库现状（已扫）

| 层 | 位置 | 行数 / 形状 | 是否语言相关 |
|---|---|---|---|
| 应用入口 | `src/main/kotlin/com/mujingx/Main.kt` + `build.gradle.kts` | `APP_NAME="幕境"`、`group="com.mujingx"`、v2.12.3 | 是（名字、版本） |
| 数据契约 | `data/Vocabulary.kt` + `data/Dictionary.kt` | `Vocabulary{ name, type, language, size, wordList }`，`Word{ value, usphone, ukphone, definition, translation, pos, collins, oxford, tag, bnc, frq, exchange }` | **强相关** |
| 词典 SQLite | `data/Dictionary.kt`（单 object）+ `dict/ecdict.7z` | 30+ SQL 函数，全是 `SELECT * from ecdict` | **强相关** |
| 字幕分词 | `ui/util/GenerateVocabulary.kt`（984 行） | 用 OpenNLP：`TokenizerModel/POSModel/ChunkerModel` 三个 `.bin` | **强相关** |
| NLP 模型 | `src/main/resources/opennlp/`（4 个 .bin） | 仅英语 UD-EWT | 是 |
| 词库 JSON | `resources/common/vocabulary/出国/*.json` 等 | `"language": "english"` | 是 |
| 单词卡片 UI | `ui/wordscreen/Word.kt`（482 行）+ `WordScreen.kt` + `WordScreenSidebar.kt` | `word.value` 单行、`usphone/ukphone` 双音标 | **强相关** |
| 弹幕 | `player/danmaku/*` | `WordDetail`、`InteractiveDanmakuRenderer` | 中等 |
| 视频播放 | `player/VidePlayer.kt`、`MediaPlayerComponent.kt`、`NewTimedCaption.kt`、`HoverableCaption.kt` | VLCJ + Compose；字幕即时查词 | 中等（HoverableCaption 查 Dictionary） |
| TTS | `tts/`（Azure / MS / Mac / Ubuntu） | 与语种绑定 | 中等 |
| 跟读/听写 | `ui/wordscreen/DictationState.kt` `MemoryStrategy.kt` `ExitButton.kt` | 通用 | 否 |
| 词库编辑 | `ui/edit/*` | 通用 | 否 |
| 字幕浏览器 | `ui/subtitlescreen/*` | 通用 | 否 |
| FSRS 间隔重复 | `fsrs/*` | 通用 | 否 |
| APKG 导出 | `fsrs/apkg/*` | 通用 | 否 |

## 2. 关键架构发现

- **Word 是 `@Serializable data class`，被 27 个文件引用**。改动 Word 字段会引发级联编译错误 → 必须在所有引用点同步更新，或保留旧字段为 nullable 然后新增日语字段。
- **Vocabulary 已经有 `language: String` 字段**（值现在是 `"english"`）。日语词库直接 `"japanese"`，**这里不用改 schema，只改默认/校验/UI 标签**。
- **分词只在 `GenerateVocabulary.kt` 一处**（984 行集中所有 NLP 调用）。完美封装。
- **词典只在 `data/Dictionary.kt`**（单例）。HoverableCaption 跟 EditWordDialog 5 处查词。
- **词库生成主流程**（看懂就够了）：
  1. 用户选 MKV/字幕/文档
  2. `GenerateVocabulary.kt` 加载 OpenNLP 模型 → 断句 → 分词 → POS → chunker
  3. 把 token 当作 key，句子当作 context 存到 `map`
  4. 调 `Dictionary.query` 把 token 转成 `Word`（音标、释义、tags、frequency）
  5. 把 map 序列化成 `Vocabulary` JSON，写盘

## 3. 日语改造策略

按依赖层次，从底层往上改，每层一个 commit：

### 阶段 A — 资源与品牌（低风险）
| 顺序 | Commit | 文件 | 内容 |
|---|---|---|---|
| A1 | `chore(rebrand): rename app to AnimeJing / 番境` | `build.gradle.kts`、`README.md`、`AboutDialog.kt`、`Privacy Policy.md` | `APP_NAME = "番境"`（或 AnimeJing，看用户选择）、版本 `0.1.0-alpha.1`、包名先留 `com.mujingx` 不动避免重命名工作量 |
| A2 | `chore(i18n): add Japanese UI strings scaffold` | 新文件 `src/main/resources/i18n/strings.properties` + `strings_ja.properties` | 引入 ResourceBundle；从主屏开始逐步替换硬编码中文字符串 |
| A3 | `chore(ci): add Japanese-only GitHub Actions workflow` | `.github/workflows/build-jvm.yml`（新增） | 用 `./gradlew assemble` 跑 JRuby/Compose Desktop；缓存 `~/.gradle`、`~/.cargo`；matrix: ubuntu + macos（暂不加 windows） |

### 阶段 B — 数据契约（核心）
| 顺序 | Commit | 文件 | 内容 |
|---|---|---|---|
| B1 | `feat(data): add Japanese term fields to Word` | `data/Vocabulary.kt` | `Word` 加 `kanji: String`、`kana: String`、`romaji: String`、`conjugations: MutableList<String>`、`jlpt: Int`、`pitchAccent: String`、`gloss_en: String`、`gloss_cn: String`、`frequencyRank: Int`。保留英语字段为 nullable 让旧词库可读 |
| B2 | `feat(data): add Language enum to Vocabulary` | `data/Vocabulary.kt`、`data/VocabularyType.kt` | `Vocabulary.language: String` → 改成 `Language` 枚举（`ENGLISH / JAPANESE`），给默认 `JAPANESE` 给新文件 |
| B3 | `chore(data): keep serialization backwards-compatible` | `data/Vocabulary.kt` | 用 `@SerialName` + `@Required` 让旧 JSON 词库仍能加载（用户已有英语词库不丢） |

### 阶段 C — 词典层（核心）
| 顺序 | Commit | 文件 | 内容 |
|---|---|---|---|
| C1 | `feat(dict): add JMdict SQLite loader` | 新文件 `data/JMDict.kt` | 加载 `jmdict.db`（表 `jmdict(id, expression, reading, pos, gloss_en, gloss_cn, jlpt, freq_rank)`），实现 `query / queryList` 形态与 Dictionary 平行 |
| C2 | `feat(dict): add language-aware Dictionary facade` | `data/Dictionary.kt` | 拆 `DictionaryEnglish : DictionaryBase` / `DictionaryJapanese : DictionaryBase`；外部 API 改成 `Dictionary.forLanguage(Language.JAPANESE).query(...)` |
| C3 | `chore(dict): deprecate English-only call sites` | 5 个调用点 | `HoverableCaption.kt`、`EditWordDialog.kt`、`WordFrequencyDialog.kt`、`Search.kt`、`GenerateVocabulary.kt` 切到 language-aware API |

### 阶段 D — NLP / 分词（核心）
| 顺序 | Commit | 文件 | 内容 |
|---|---|---|---|
| D1 | `feat(nlp): introduce LanguageTokenizer interface` | 新文件 `ui/util/tokenizer/LanguageTokenizer.kt` + `EnglishTokenizer.kt` + `JapaneseTokenizer.kt` | 抽象 `tokenize(text): List<Token>`，`Token(lemma, kana, pos, isPhrase, start, end)`；英文实现把现有 OpenNLP 三个函数包起来；日文实现基于 `atilika/kuromoji` |
| D2 | `build(deps): add Kuromoji IPADIC for Japanese tokenization` | `build.gradle.kts` | `com.atilika.kuromoji:kuromoji-ipadic:0.9.0`；下载 ipadic 字典到 `src/main/resources/japanese/ipadic/`（小文件，~25MB）或运行时从 GitHub release 下载 |
| D3 | `refactor(nlp): route GenerateVocabulary through LanguageTokenizer` | `ui/util/GenerateVocabulary.kt` | 把直接 `tokenizer.tokenize` / `posTagger.tag` / `chunker.chunkAsSpans` 三处替换为 `languageTokenizer.tokenize(text)` 一行调用 |
| D4 | `chore(nlp): remove OpenNLP English models from runtime path` | `src/main/resources/opennlp/`、`.gitignore` | 默认日语模型为主，OpenNLP 模型按需懒加载；保留作为 fallback 给老英语词库 |

### 阶段 E — 词库生成 UI（重要）
| 顺序 | Commit | 文件 | 内容 |
|---|---|---|---|
| E1 | `feat(ui): add Japanese dictionary source picker` | `ui/dialog/GenerateVocabularyDialog.kt`、`GenerateVocabularyListDialog.kt` | 加「JLPT 等级」筛选（N5/N4/N3/N2/N1）、「最低出现频率」slider、「分词模式：表记形 / 词态还原 / 汉字+假名」radio |
| E2 | `feat(generate): include conjugation expansion for verbs/adjectives` | `ui/util/GenerateVocabulary.kt` | `JapaneseTokenizer` 暴露 `expandConjugations(token): List<String>`；动词/イ形容词自动收录活用形（食べる→食べ/食べた/食べれば…）让用户既能练原型也能练活用 |

### 阶段 F — 单词卡片 UI（最显眼）
| 顺序 | Commit | 文件 | 内容 |
|---|---|---|---|
| F1 | `feat(ui): Word card renders kanji + kana + romaji stack` | `ui/wordscreen/Word.kt` | 把单行 `word.value` 改成三层 Column：漢字 28sp、kana 16sp 灰色、romaji 12sp 灰色（按 Ctrl+P/数字键可隐藏各层，模仿现有 Ctrl+V/P/L/E/K 快捷键） |
| F2 | `feat(ui): add Japanese sentence under-word panel` | `ui/wordscreen/Word.kt` + `WordScreenSidebar.kt` | 替换 `usphone/ukphone` 区为「漢字 / 假名 / 重音」三行；右下加小型「活用形」tags |
| F3 | `feat(danmaku): Japanese danmaku shows kanji hover-to-kana` | `player/danmaku/WordDetail.kt`、`CanvasDanmakuItem.kt` | 弹幕本体仍是漢字，悬浮追加 kana 行（与 F1 配套） |
| F4 | `feat(tts): add macOS say ja-JP + Azure ja-JP routes` | `tts/MacTTS.kt`、`tts/Azure TTS.kt`、`SettingsDialog.kt` | TTS 语言下拉加 `日本語` 选项，默认走 macOS `say -v Kyoko` / Azure `ja-JP-NanamiNeural` |

### 阶段 G — 词库与冷启动（对外）
| 顺序 | Commit | 文件 | 内容 |
|---|---|---|---|
| G1 | `feat(vocab): ship 2 demo Japanese vocabularies (JLPT N5/N4)` | `resources/common/vocabulary/japanese/` | 两个示范词库 JSON：从公共领域语料（NHK ニュース、青空文庫）生成。**避免任何版权敏感的动漫字幕** |
| G2 | `feat(generate): built-in sample anime subtitle` | `resources/common/sample/` | 提供一段 5 分钟公开番（NHK 教育频道新闻节目字幕，CC-BY）的示范资源，让用户立刻能点开看效果 |
| G3 | `docs: README rewritten for AnimeJing` | `README.md` | 强调「动漫番剧学日语」、JMdict 许可、Table 字幕版权边界 |

### 阶段 H — 可选（之后再说）
- Anki APKG 导出（已有 `fsrs/apkg/*` 框架）
- 用户上传自有动漫资源 + 自动生成词库
- 在线词库（JMdict/日本語アクセント辞書月度同步）

## 4. 工程纪律

- **本机零编译**：每个 commit 推上去后看 GitHub Actions 是否过；失败先在本地 `gradlew help`、`gradlew tasks` 之类的**非编译**命令验证语法。
- **每个 commit 单一职责**：改完一个文件能编译过就不要混第二件事。
- **遇到 Word 字段破坏老 JSON**：用 `@Required` + default value 兜底，绝不让老英语词库读取报错。
- **不动 `com.mujingx` 包名**：重命名包名要改 100+ import，先做产品验证再换。
- **JMdict 协议**：JMdict 是 GPL+EDICT 双协议，标明在 README 与 About Dialog。
- **资源版权**：内置动漫只用 CC-BY / 公共领域，绝不放商业动漫字幕。

## 5. 阶段间检查点（每个阶段结束要做的）

- 推 master → Actions 跑过 → 手动 review diff
- 用本地 `gradle tasks --all | grep assemble`（不编译）确认 task 链路没断
- 阶段 D 完成后，**第一次在 Actions 里跑 `assembleDeb`** 看是否真能出包（日语词典/分词器加载链路打通）

## 6. 用户可在哪个节点介入

- A1 改名之前：确认是「AnimeJing」「番境」「日本語境」还是别的中文/罗马字命名
- B1 字段定义之前：是否要 `kanji+kana+romaji` 三栏，还是要更简洁的「漢字 / 假名」两栏
- D1 分词接口之前：是否接受 Kuromoji IPADIC（默认自带 25MB），还是想要用 Sudachi 或 MeCab+neologd（动漫 OOV 更少但部署更重）

## 7. 用户决策（2026-09-17）

| 项 | 选择 | 备注 |
|---|---|---|
| 项目名 | **番境** | GitHub repo 仍叫 `AnimeJing`；APP_NAME、菜单、macOS app 名称用「番境」 |
| 卡片排版 | **2 选 1 切换**：漢字 + 罗马字 **或** 假名 + 罗马字 | 快捷键 Ctrl+P 切换「漢字模式 / 假名模式」；永远不同时显示三层 |
| 分词器 | **Sudachi 包装** | Sudachi 的 JVM 形态（worksapplications 提供官方预编译 jar + small/medium/core 词典三档，默认 small） |
| 示范资源 | **NHK 新闻 + 青空文庫** | 公共领域 + 官方公开；零版权风险；用户首次打开看到日语新闻视频 |

### 番境卡片 UI 草图

```
漢字模式（默认）
   食べる                    ← 漢字 28sp
   ta be ru                  ← 罗马字 12sp 灰色

假名模式（Ctrl+P 切换）
   たべる                     ← 假名 28sp
   ta be ru                  ← 罗马字 12sp 灰色

下方一行：
   v. 吃；吃饭；生活           ← 释义

右下角可选小 tag（如果有）：
   [动1] [食べる][食べ][食べた][食べれば]…  ← 活用形
```

### Sudachi 集成计划

- 依赖：`com.worksap.nlp:sudachi:0.6.2`（worksap 官方 JVM 包装；含 dictionary/、settings/）
- 词典档：默认 `sudachi-dictionary-small`（~50MB；含基本 80 万词）；后续可换 core（~130MB）给动漫 OOV 更好覆盖
- Actions 里跑：
  ```yaml
  - name: Cache Sudachi dict
    uses: actions/cache@v4
    with:
      path: ~/.cache/sudachi
      key: sudachi-dict-small-v1
  ```
- 首次构建由 Gradle 任务 `fetchSudachiDict` 下载到 `~/.cache/sudachi`（与 Whisper 模型同思路）

### 不做什么

- 不动 `com.mujingx` 包名（重命名要改 100+ import，先做产品验证再换）
- 不内置任何商业动漫字幕（用户自备；示范用 NHK/青空文庫）

- G1 示范词库之前：是否要内置动漫？还是先用 NHK 新闻示范，避免任何版权问题