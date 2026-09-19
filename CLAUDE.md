# AnimeJing 仓库约定

## 工作纪律（user 2026-09-19 明确要求）

1. **git push 推不了不要硬推**，立即跳到下一步手头能做的事。
2. 每次 `edit` / `write` 后**立刻 grep 一下文件结构**确认没破坏（行数、括号配对、section 标记）。
3. workflow 文件的复杂改动用 `write` 重写，**不要用 `edit` 多次打补丁**——edit 极易产生重复/孤行。
4. 任务分两段：commit 之前 + push 之前。commit 后只看 git status；push 失败立刻停手。

## 当前架构状态

- **默认查词后端**: `JishoClient` (jisho.org API + 本地 SQLite 缓存, ~/.cache/animejing/jisho.sqlite)
- **日语分词**: `JapaneseTokenizer` (Kuromoji IPADIC 0.9.0, IPADIC 内嵌 jar)
- **JLPT 词库**: 用户要求 N1-N5 全部内置,目前**未生成**（v0.1 待办）
- **TTS**: 用户要求 kokoro-onnx,目前**未集成**
- **ASR**: 用户要求 SenseVoiceSmall ONNX,目前**未集成**
- **打包**: workflow 仅产 AppImage + RPM（删 DEB）
- **目标平台**: Fedora 44 KDE Plasma 6 Wayland
