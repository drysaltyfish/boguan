# 第三方组件与许可

本项目自身代码以 Apache-2.0 发布（见 `LICENSE`）。以下为本项目依赖或计划依赖的第三方组件及其许可。

> **模型权重不由本项目分发。** 应用在首次启动时从上游下载模型；本项目仓库与 Release 中不包含任何权重文件。

## 运行期依赖

| 组件 | 用途 | 许可 | 说明 |
|---|---|---|---|
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | 端上语音识别运行时（Kotlin AAR） | Apache-2.0 | 闭源/开源分发均允许，保留 LICENSE 与 NOTICE 即可 |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime) | 模型推理后端 | MIT | 随 sherpa-onnx 分发 |
| [Miuix](https://github.com/compose-miuix-ui/miuix) | UI 组件库（HyperOS 风格，ADR-0014） | Apache-2.0 | 全部界面组件来自该库 |
| [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer) | 报告 Markdown 渲染（ADR-0014） | Apache-2.0 | 颜色/字体交给 Miuix 主题 |
| [JetBrains markdown](https://github.com/JetBrains/markdown) | Markdown 解析（随渲染器引入） | Apache-2.0 | |
| [OkHttp](https://square.github.io/okhttp/) | LLM HTTP 客户端 | Apache-2.0 | |
| Kotlin / Kotlin Coroutines | 语言与并发 | Apache-2.0 | |
| Jetpack Compose / AndroidX | UI 基础设施（foundation、activity、lifecycle） | Apache-2.0 | |
| kotlinx.serialization | JSON 序列化 | Apache-2.0 | |

## 模型权重

| 模型 | 用途 | 许可状态 |
|---|---|---|
| **X-ASR** `sherpa-onnx-x-asr-480ms-streaming-zipformer-transducer-zh-en-punct-int8-2026-06-05`（及 1920ms 变体） | 实时流式转写 | **Apache-2.0**（仓库 LICENSE 与 HF 模型卡标签双重确认，来源：`Gilgamesh-J/X-ASR` / `GilgameshWind/X-ASR-zh-en`）。许可证允许再分发，但本项目仍按约定**不把权重放进 Release**，由应用从上游下载。 |

## 已弃用的组件 / 模型

| 组件 | 原因 |
|---|---|
| `sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30` | 上游未声明权重许可；且与 sherpa-onnx ≥2.0 的 metadata 不兼容、无法加载。已由 X-ASR 取代，见 [ADR-0008](docs/adr/0008-realtime-model-revision.md)。 |
| SenseVoice-Small int8 | 曾作为终稿二遍模型；实测劣于流式模型且需要 217 MB 模型空间，已随离线二遍一起移除（[ADR-0013](docs/adr/0013-remove-offline-pass.md)）。 |
| Room | 首版用 `SQLiteOpenHelper` 替代，见 [ADR-0009](docs/adr/0009-storage-implementation.md)。 |
| Material 3 | 界面组件统一改用 Miuix（[ADR-0014](docs/adr/0014-miuix-ui-and-markdown.md)）。 |

## 许可未覆盖 / 待定

- 若后续启用语音增强（GTCRN：MIT、DPDFNet：Apache-2.0）或自建「精修服务」（FireRedASR2：Apache-2.0），需在本文件补充。
- 本项目**不引入** AGPL-3.0 代码或权重；若将来引入，整个应用须改为 AGPL-3.0 发布。
