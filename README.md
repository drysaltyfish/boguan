# 博观约取 (boguan)

> 博观而约取，厚积而薄发。 —— 苏轼《稼说送张琥》

安卓端的**课堂 / 会议录音 → 端上实时转写 + 实时滚动摘要 → 会后结构化笔记**工具。

**本地优先**：音频**永不**出设备；转写文本只会在生成摘要与终稿校正时发往你**自行配置**的 LLM 端点（可关闭），转写本身完全在端上完成。

> **当前状态：v0.1 主干已跑通，并在真机（一加 12 / Android 16）逐项验证。**
> 录音（前台服务/熄屏存活）→ 端上实时转写 → WAV 落盘与锚点回听 → 2 分钟微摘要 → 10 分钟阶段小结 → 会后二遍转写 → 结构化报告（课堂笔记 / 会议纪要）。
> 待补：待确认清单进报告、发布前 API Key 迁 Keystore、测试与 CI。

## 它想解决什么

课堂上你记不全，会议里你来不及记。现有产品要么把音频传到云端（Otter、飞书妙记、腾讯会议），要么只能录音、会后上传再出结果（Granola 手机端）。

本项目做一个目前没人做的组合：**手机端上实时 ASR + 会中滚动摘要**。

- 端上实时转写（Google Pixel Recorder 证明可行，但没有会议产品这么做）
- 每 2 分钟一条类型化微摘要，滚动显示；每 10 分钟合并去重成阶段小结
- 停录后用 LLM 校正转写（实时稿保留、校正稿另存），分歧大的段落进「待确认」；报告顶部汇总待确认清单并带时间锚点
- 终稿抽取成结构化笔记：**课堂笔记**（知识点 / 考点 / 作业）或**会议纪要**（决议 / 待办 / 风险）
- 每个要点可**一键回听**对应原音；两稿不一致的内容标「待确认」

## 核心取舍

| 取舍 | 选择 | 代价 |
|---|---|---|
| 隐私 vs 准确率 | 音频留在本机，终稿 CER 约 8–10% | 低于云端方案的 3–5%；用锚点回听兜住 |
| 实时 vs 准确 | 两遍转写，实时优先、事后纠错 | 首启须下载约 160 MB 模型 |
| 功能 vs 范围 | 不做云、账号、导出、编辑、插件、线上会议 | 见 [ADR-0007](docs/adr/0007-v0.1-scope-and-acceptance.md) |

## 技术栈

- **Kotlin + Jetpack Compose**，核心为纯 Kotlin 模块（无 Android 依赖）
- UI 组件统一用 **[Miuix](https://github.com/compose-miuix-ui/miuix)**（HyperOS 风格，见 [ADR-0014](docs/adr/0014-miuix-ui-and-markdown.md)）；报告用 **multiplatform-markdown-renderer** 渲染，配色/字体取自 Miuix 主题
- 实时 ASR：`sherpa-onnx` + **X-ASR 流式 zipformer（默认 1920 ms chunk，可选 480 ms，Apache-2.0）**（端上）
- 终稿校正：把「实时稿 + 术语表 + 上下文」交给用户配置的 LLM 做同音字/术语校正（[ADR-0011](docs/adr/0011-llm-transcript-correction.md)）；不再内置离线 ASR 模型（[ADR-0013](docs/adr/0013-remove-offline-pass.md)）
- LLM：**OpenAI 兼容协议**，供应商与模型可切换（OpenAI / 大陆可直连 / 自定义 `base_url`）
- 最低 Android 10（API 29）

## 验证状态

技术尖峰（2026-11-18）在**一加 12 / 骁龙 8 Gen 3 / Android 16** 上实测，200.7 秒中文课堂音频、2 线程 CPU：

| 模型变体 | RTF | 首字延迟 | 实时余量 |
|---|---|---|---|
| X-ASR 480ms（可选档，低延迟） | 0.085 | 1.60 s | 11.8× |
| **X-ASR 1920ms（默认）** | **0.046** | **2.10 s** | 21.6× |

参考文本为 LLM 校正稿时，两档的字符错误率：1920 ms **10.6%**、480 ms **15.9%**（下降 33%，见 [ADR-0012](docs/adr/0012-realtime-tier-1920ms.md)）。

结论：端上实时转写**可行**；默认用更准的 1920 ms 档，需要更低首字延迟时可在设置里切回 480 ms。

## 文档

| 文档 | 内容 |
|---|---|
| [ADR-0001](docs/adr/0001-application-architecture.md) | 应用架构：共享核心 + 薄 UI 壳 |
| [ADR-0002](docs/adr/0002-asr-pipeline.md) | ASR 管线：两遍转写与模型选型 |
| [ADR-0003](docs/adr/0003-real-time-summary.md) | 实时摘要：类型化节拍、供应商抽象、四级降级 |
| [ADR-0004](docs/adr/0004-report-extraction-and-templates.md) | 报告抽取：公共信封 + 类型载荷 |
| [ADR-0005](docs/adr/0005-local-data-and-recovery.md) | 本地数据与失败恢复 |
| [ADR-0006](docs/adr/0006-release-and-licensing.md) | 发布、许可与供应链 |
| [ADR-0007](docs/adr/0007-v0.1-scope-and-acceptance.md) | v0.1 范围、验收门槛、风险登记 |
| [ADR-0008](docs/adr/0008-realtime-model-revision.md) | 实时模型改版：X-ASR 480ms、Apache-2.0、真机实测 |
| [ADR-0009](docs/adr/0009-storage-implementation.md) | 存储实现：首版 SQLiteOpenHelper，Room 作迁移目标 |
| [ADR-0010](docs/adr/0010-two-pass-text-policy.md) | 二遍稿文本策略：两稿并存不覆盖，以分歧驱动待确认 |
| [ADR-0011](docs/adr/0011-llm-transcript-correction.md) | 终稿 LLM 校正：用语言模型修同音错（并更新隐私表述） |
| [ADR-0012](docs/adr/0012-realtime-tier-1920ms.md) | 实时档位：默认 1920 ms（错误率 10.6% vs 15.9%） |
| [ADR-0013](docs/adr/0013-remove-offline-pass.md) | 移除离线二遍 ASR：终稿只保留 LLM 校正 |
| [ADR-0014](docs/adr/0014-miuix-ui-and-markdown.md) | 界面统一 Miuix + 报告 Markdown 渲染（版本受 compileSdk 36 约束） |
| [GLOSSARY.md](GLOSSARY.md) | 术语表（先读这个） |

## 工程结构

```
app/                 Compose 薄壳：界面、权限、录音调度
core/model/          纯 Kotlin 领域模型（Session / Segment / ReportPayload / 状态机）
core/audio/          麦克风采集（16 kHz 单声道 PCM，100 ms 帧）
core/asr/            ASR 边界 + sherpa-onnx 流式实现（AAR 自托管于 libs/）
core/summarize/      LLM 边界与数据结构（OpenAI 兼容协议，模型名不硬编码）
core/store/          本地 SQLite（六表）+ SessionRepository
core/report/         Markdown 渲染器（课堂 / 会议两种模板投影）
```

模块边界与依赖方向见 [ADR-0001](docs/adr/0001-application-architecture.md)；存储实现选择见 [ADR-0009](docs/adr/0009-storage-implementation.md)。

## 数据流向

```
麦克风 ──► 本机录音(WAV) ──► 本机转写 ─┬─► 本机数据库（转写/摘要/报告）
                                      └─► 摘要文本 ──► 你配置的 LLM 端点
```

- 音频**从不**离开设备
- 转写**从不**离开设备
- 只有用于生成摘要的文本会发往你配置的端点
- API key 存于 Android Keystore，不经任何本项目服务器
- 无账号、无云同步、无遥测

## 构建前提（供将来参考）

- JDK 21
- Android SDK（platform-tools / platforms / build-tools）
- `ANDROID_HOME` 指向 SDK 根目录

## 已知限制

- 仅支持**线下**场景；**无法录制其他 App 的声音**（Android `AudioPlaybackCapture` 被多数会议 App 禁用）→ 线上会议不在范围内
- 远场（约 4 m）中文识别准确率仍有限，转写错误不可避免；报告请配合「待确认清单」核对
- 仅 Android；桌面端为二期计划
- 说话人分离未实现（二期）

## 许可

代码以 [Apache-2.0](LICENSE) 发布。第三方组件与模型权重许可见 [THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md) 与 [NOTICE](NOTICE)。
