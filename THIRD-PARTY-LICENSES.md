# 第三方组件与许可

本项目自身代码以 Apache-2.0 发布（见 `LICENSE`）。以下为本项目依赖或计划依赖的第三方组件及其许可。

> **模型权重不由本项目分发。** 应用在首次启动时从上游下载模型；本项目仓库与 Release 中不包含任何权重文件。

## 运行期依赖

| 组件 | 用途 | 许可 | 说明 |
|---|---|---|---|
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | 端上语音识别运行时（Kotlin AAR） | Apache-2.0 | 闭源/开源分发均允许，保留 LICENSE 与 NOTICE 即可 |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime) | 模型推理后端 | MIT | 随 sherpa-onnx 分发 |
| [SenseVoice](https://github.com/FunAudioLLM/SenseVoice)（FunASR） | 终稿二遍识别（端上离线） | **FunASR Model Open Source License v1.1**（自定义、非 OSI） | 允许商用，但**必须署名并保留模型名称**；含「不得诋毁」条款。详见 [MODEL_LICENSE](https://github.com/modelscope/FunASR/blob/main/MODEL_LICENSE) |
| Kotlin / Kotlin Coroutines | 语言与并发 | Apache-2.0 | |
| Jetpack Compose / AndroidX | UI | Apache-2.0 | |
| Room | 本地数据库 | Apache-2.0 | |
| kotlinx.serialization | JSON 序列化 | Apache-2.0 | |

## 模型权重

| 模型 | 用途 | 许可状态 |
|---|---|---|
| **X-ASR** `sherpa-onnx-x-asr-480ms-streaming-zipformer-transducer-zh-en-punct-int8-2026-06-05`（及 1920ms 变体） | 实时流式转写 | **Apache-2.0**（仓库 LICENSE 与 HF 模型卡标签双重确认，来源：`Gilgamesh-J/X-ASR` / `GilgameshWind/X-ASR-zh-en`）。许可证允许再分发，但本项目仍按约定**不把权重放进 Release**，由应用从上游下载。 |
| SenseVoice-Small int8 | 终稿二遍识别 | 见上表 FunASR Model Open Source License v1.1，须署名 |

## 已弃用的模型

| 模型 | 原因 |
|---|---|
| `sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30` | 上游未声明权重许可；且与 sherpa-onnx ≥2.0 的 metadata 不兼容、无法加载。已由 X-ASR 取代，见 [ADR-0008](docs/adr/0008-realtime-model-revision.md)。 |

## 许可未覆盖 / 待定

- 若后续启用语音增强（GTCRN：MIT、DPDFNet：Apache-2.0）或自建「精修服务」（FireRedASR2：Apache-2.0），需在本文件补充。
- 本项目**不引入** AGPL-3.0 代码或权重；若将来引入，整个应用须改为 AGPL-3.0 发布。
