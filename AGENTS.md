# AGENTS.md

## Agent skills

### Issue tracker

Issues for this repo live in GitHub Issues, managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles map 1:1 to `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: one `GLOSSARY.md` plus `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## 博观约取（boguan）

Android 课堂 / 会议录音 → 端上实时转写 + 实时滚动摘要 → 会后结构化笔记（课堂笔记 / 会议纪要两种类型）。本地优先：音频永不离开设备；转写文本只在生成摘要与终稿校正时发往用户自行配置的 LLM 端点（可关闭，ADR-0011）。

**当前状态**：v0.1 主干已闭环并在真机验证——录音（前台服务/熄屏存活）→ 端上实时转写（默认 1920 ms 档，可选 480 ms，ADR-0012）→ WAV 落盘与锚点回听 → 2 分钟 LLM 微摘要 → 10 分钟阶段小结（ADR-0003 结构整理）→ 终稿（LLM 校正，实时稿与校正稿并存，ADR-0011 / ADR-0013）→ 结构化报告（含顶部待确认清单 + 时间锚点，DeepSeek 实调）。界面组件统一 Miuix 0.9.4、报告 Markdown 渲染（ADR-0014）；工具链 Gradle 9.6.0 / AGP 9.5.1 / compileSdk 37 / minSdk 31（Android 12），依赖走镜像源（ADR-0015）。待补：发布前把 API Key 迁到 Keystore；Android 12/13 真机验证。热词/同音替换已试过不可行，结论写在 ADR-0012。

- 设计决策：`docs/adr/`（0001 架构 · 0002 ASR 管线 · 0003 实时摘要 · 0004 报告抽取与模板 · 0005 本地数据与恢复 · 0006 发布与许可 · 0007 范围与验收 · 0008 实时模型改版 · 0009 存储实现 · 0010 二遍稿文本策略 · 0011 终稿 LLM 校正 · 0012 实时档位 · 0013 移除离线二遍 · 0014 UI 用 Miuix + Markdown 渲染 · 0015 工具链升级与 minSdk 31）
- 术语表：`GLOSSARY.md`
- 必办待办与已接受风险：ADR-0007 第 3、4 节

写代码前先读 ADR-0001 / 0002 / 0004。要推翻任何已决决策，请**新增**一份 ADR 并在其中声明它取代哪一份，不要直接改旧 ADR 的结论。
