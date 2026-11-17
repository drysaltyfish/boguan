# ADR-0002：ASR 管线 —— 两遍转写与模型选型

- **状态**：已接受
- **日期**：2026-11-17
- **相关**：ADR-0001（架构）、ADR-0005（数据）、ADR-0007（验收与风险）

## 背景

场景锁定：**教室、主讲人在讲台、手机在听众席约 4 m、室内安静、单一主讲、约 90 分钟**。

关键事实（2026-11 调研）：

- 公开数据集里**没有「4 m 单主讲」基准**。最接近的是 WenetSpeech `test_meeting`（远场、多说话人但重叠少）。
- **AliMeeting 的数字不能外推到单人主讲**：同一个流式 zipformer 在 `ws_meeting` 是 7.91%，在 AliMeeting 是 28.74%，差 3.6 倍，差的是「说话人重叠 + 即兴口语」，不是距离/混响。
- 会议类产品**无一家**把 ASR 放手机端；系统级 App（Google Pixel Recorder）有端上实时 ASR，但**不做会中滚动摘要**。本项目是这两者的组合，产品上无先例。

## 决策

**两遍转写**：实时流式出字（优先），会后本地非流式纠错（覆盖）。

| 用途 | 模型 | 体积 | 依据 / 许可 |
|---|---|---|---|
| 实时 | `sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30` | 约 160 MB | ws_meeting 7.91% / ws_net 8.54%；**权重无 license 声明（见 R1）** |
| 终稿 | `SenseVoice-Small` int8 | 约 228 MB | 远场表现优于 Whisper 一档；FunASR Model Open Source License v1.1（可商用，**须署名并保留模型名**） |

其余决定：

1. **采集**：`AudioRecord`，16 kHz 单声道 16-bit PCM。
2. **断句 / 端点检测**：用 `sherpa-onnx` 自带 endpoint detection，不引入额外 VAD 模型。
3. **不做激进降噪 / 去混响**：安静教室的退化主因是混响而非噪声，级联 SE 会引入伪影反而拉高 CER。预留 `AudioEnhancer` 接口，若实测证明需要再评估 GTCRN（523 KB，MIT）或 DPDFNet（Apache-2.0）。
4. **模型分发**：**不内置**，不随仓库/Release 分发；首次启动下载，支持断点续传与预计时长展示。
5. **终稿时机**：默认「充电 + 息屏 + 非移动网络」自动跑；可手动触发。
6. **二遍执行者**：手机本地。**不做默认云端上传**（隐私是产品命脉）。

## 后果

**正面**

- 实时可用性与终稿质量分离，不需要在「流式但差」和「准但慢」之间二选一。
- 音频不出机器，与「数据存本地」的承诺一致。

**负面 / 代价**

- **首启必须先下约 160 MB 才能录音**（不接受内置弱模型兜底：Moonshine Mandarin Tiny Streaming 虽只有 30–40 MB 且 MIT，但 CER 16.1%，会显著损害第一印象）。
- 终稿 CER 预期 **8–10%**，明显高于服务端方案的 3–5%。这是用隐私换来的，必须由「锚点回听 + 待确认清单」（ADR-0004）兜住。
- Android 真机 RTF 无一手数据（桌面 RTF 0.15 不能外推）→ 技术尖峰第一项。

## 备选方案

| 方案 | 否决理由 |
|---|---|
| `streaming-paraformer`（226 MB，Apache-2.0，ws_meeting 10.64%） | 许可干净但准确率低 2.7 个点；作为 R1 无法解决时的回退项保留 |
| FireRedASR2-AED 1.1B（1.18 GB int8，Apache-2.0，ws_meeting **4.53%**） | 准确率最优但非流式、体积远超手机端；**列为二期可选「自建精修服务」的模型** |
| Whisper large-v3 / turbo | 中文远场 18.4–18.9%，直接排除 |
| 端上 Moonshine Mandarin Tiny（MIT，30–40 MB） | 仅 16.1% CER，只适合作兜底，与「首启必须能用」冲突 |
| 云端 ASR（阿里云约 3.5 元/小时） | 音频出机器，与产品前提冲突；仅作二期可选增强 |

## 风险

**R1（已接受）**：实时模型权重在 HF 上**没有 `license` 字段**。处置：权重不随项目分发、以自用为主；**若转为对外公开发布，必须先清理**（联系作者、改用 paraformer、或让用户自行从上游下载且项目不承载分发）。

## 参考

- icefall 中文多数据集 CER：https://github.com/k2-fsa/icefall/blob/master/egs/multi-zh-hans/ASR/RESULTS.md
- sherpa-onnx 模型文档：https://k2-fsa.github.io/sherpa/onnx/
- FireRedASR2S 论文：https://arxiv.org/abs/2603.10420
- SenseVoice 权重许可：https://github.com/modelscope/FunASR/blob/main/MODEL_LICENSE
- Pixel Recorder 端上实时 ASR：https://research.google/blog/the-on-device-machine-learning-behind-recorder/
