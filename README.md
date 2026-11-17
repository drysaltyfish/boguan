# 博观约取 (boguan)

> 博观而约取，厚积而薄发。 —— 苏轼《稼说送张琥》

安卓端的**课堂 / 会议录音 → 端上实时转写 + 实时滚动摘要 → 会后结构化笔记**工具。

**本地优先**：音频与转写永不出设备；只有摘要文本会发送到你**自行配置**的 LLM 端点。

> ⚠️ **当前状态：设计完成，尚无代码。** 本仓库目前只有设计文档（ADR）与术语表。请勿期待可运行产物。

## 它想解决什么

课堂上你记不全，会议里你来不及记。现有产品要么把音频传到云端（Otter、飞书妙记、腾讯会议），要么只能录音、会后上传再出结果（Granola 手机端）。

本项目做一个目前没人做的组合：**手机端上实时 ASR + 会中滚动摘要**。

- 端上实时转写（Google Pixel Recorder 证明可行，但没有会议产品这么做）
- 每 2 分钟一条类型化微摘要，滚动显示
- 停录后在手机本地重跑一遍纠错，得到「终稿」
- 终稿抽取成结构化笔记：**课堂笔记**（知识点 / 考点 / 作业）或**会议纪要**（决议 / 待办 / 风险）
- 每个要点可**一键回听**对应原音；低置信度内容标「待确认」

## 核心取舍

| 取舍 | 选择 | 代价 |
|---|---|---|
| 隐私 vs 准确率 | 音频留在本机，终稿 CER 约 8–10% | 低于云端方案的 3–5%；用锚点回听兜住 |
| 实时 vs 准确 | 两遍转写，实时优先、事后纠错 | 首启须下载约 160 MB 模型 |
| 功能 vs 范围 | 不做云、账号、导出、编辑、插件、线上会议 | 见 [ADR-0007](docs/adr/0007-v0.1-scope-and-acceptance.md) |

## 技术栈

- **Kotlin + Jetpack Compose**，核心为纯 Kotlin 模块（无 Android 依赖）
- 实时 ASR：`sherpa-onnx` 流式 zipformer（端上）
- 终稿 ASR：`SenseVoice-Small`（端上离线二遍）
- LLM：**OpenAI 兼容协议**，供应商与模型可切换（OpenAI / 大陆可直连 / 自定义 `base_url`）
- 最低 Android 10（API 29）

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
| [GLOSSARY.md](GLOSSARY.md) | 术语表（先读这个） |

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
