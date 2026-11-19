# ADR-0008：实时模型改版 —— 采用 X-ASR 480ms（Apache-2.0）

- **状态**：已接受；**默认分块档位已被 [ADR-0012](0012-realtime-tier-1920ms.md) 取代**（默认改为 1920 ms，480 ms 保留为可选档）
- **日期**：2026-11-18
- **取代**：ADR-0002 中的「实时引擎 / 终稿引擎」选型（ADR-0002 的其余结论仍然有效）
- **依据**：技术尖峰在桌面与真机上的实测数据

## 背景

ADR-0002 选定 `sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30`（约 160 MB）作为实时引擎，并登记 R1：该权重在 Hugging Face 上**没有 `license` 字段**，开源分发存在真实风险。

技术尖峰（2026-11-18）在桌面与真机（一加 12 / 骁龙 8 Gen 3 / Android 16）上做了模型对比，发现三个事实：

1. **2026-06 发布的 X-ASR 系列**（上海交大 / 上海创智 / 复旦 / 华科，`Gilgamesh-J/X-ASR`，权重在 Hugging Face `GilgameshWind/X-ASR-zh-en`）在中文远场课堂上明显更好：**自带标点、保留中英混说**，这对下游 LLM 摘要与报告是数量级改善。
2. **许可证是 Apache-2.0**（仓库 LICENSE 文件 + HF 模型卡标签双重确认）→ **R1 关闭**。
3. **速度也更快**：同机同音频，480ms 变体比原模型快约 1.5 倍。

同时发现原选模型与现代 sherpa-onnx（≥2.0）**metadata 不兼容**，实测根本无法加载（详见「工程陷阱」）。

## 决策

| 用途 | 模型 | 体积 | 权重许可 |
|---|---|---|---|
| **实时（默认）** | `sherpa-onnx-x-asr-480ms-streaming-zipformer-transducer-zh-en-punct-int8-2026-06-05` | 127.7 MB（tar.bz2） | **Apache-2.0** |
| 实时（省电/低端机档） | 同上 `1920ms` 变体 | 127.7 MB | Apache-2.0 |
| 终稿二遍 | SenseVoice-Small int8（不变） | 约 228 MB | FunASR Model Open Source License（须署名） |

**不采用** `160ms` 变体：RTF 反而更高（0.126 桌面 / 真机未测）、首字延迟无改善。

### 实测数据

真机（一加 12 / 骁龙 8 Gen 3 / 2 线程 / CPU int8 / 200.71 s 中文课堂音频）：

| 变体 | 加载 | 解码 | RTF | 首字延迟 | 实时余量 |
|---|---|---|---|---|---|
| **480ms（默认）** | 3.43 s | 17.08 s | **0.085** | **1.60 s** | 11.8× |
| 1920ms（省电档） | 3.37 s | 9.29 s | 0.046 | 2.10 s | 21.6× |

桌面同模型（Intel Ultra X7 358H / 2 线程）：RTF 0.057 —— 真机仅慢约 1.5 倍。

**门槛对照（ADR-0007）**：首字 1.60 s < 2.0 s ✅；RTF 0.085 « 1.0 ✅。

## 工程陷阱（必须遵守，否则会静默崩溃）

1. **`OnlineModelConfig.modelType` 必须留空**，交给 ONNX metadata 自动识别。
   X-ASR 与 2025 模型的 metadata 都是 `model_type=zipformer2`；若硬写 `"zipformer"`，sherpa 会走 v1 zipformer 实现，报 `'attention_dims' does not exist in the metadata`，随后 **`exit(255)` 静默杀进程**。Android 丢弃应用 stderr，logcat 中看不到任何异常，只能通过 `dumpsys activity exit-info` 看到 `EXIT_SELF status=255`。
2. **模型目录权限**：`adb shell mkdir` 创建的目录属主为 `shell`、权限 0770，App 作为 other 无法遍历（`File.listFiles()` 返回 null）。推送后必须 `adb shell chmod -R 0777 <dir>`。这条只影响开发期侧载调试，正式实现应在 App 内自行创建目录。

## 后果

**正面**

- R1 关闭：实时与终稿模型权重均可随开源项目合法分发（仍按约定不把权重放进 Release）。
- 输出自带标点与中英混说，减少下游 LLM 的断句错误与术语丢失。
- RTF 余量 11.8 倍，为后续加二遍转写、说话人分离留出算力空间。
- 首字延迟 1.60 s 满足 <2 s 门槛。

**负面 / 代价**

- 首字延迟从原模型的 0.8 s 升到 1.60 s（chunk 更大以保证速度）。这是**用延迟换准确率与标点**，课堂字幕场景可接受。
- 模型体积与原来相当（编码器 int8 148 MB），首启下载成本不变。
- 依赖一个**较新的第三方学术项目**（非 k2-fsa 官方），需持续关注其维护状态；已锁定具体版本（`2026-06-05`）避免漂移。

## 备选方案

| 方案 | 否决理由 |
|---|---|
| 原 2025-06-30 模型 | 权重无 license（R1）；且与 sherpa-onnx ≥2.0 metadata 不兼容，实测无法加载 |
| X-ASR 160ms | RTF 更高、首字没有改善，纯亏 |
| X-ASR 1920ms 作默认 | 首字 2.10 s 超出 <2 s 门槛；保留为省电档 |
| 非 punct 版 X-ASR | 失去标点，下游收益消失 |
