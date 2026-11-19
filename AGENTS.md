# AGENTS.md

## Agent skills

### Issue tracker

Issues for this repo live in GitHub Issues, managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles map 1:1 to `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: one `GLOSSARY.md` plus `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## 博观约取（boguan）

Android 课堂 / 会议录音 → 端上实时转写 + 实时滚动摘要 → 会后结构化笔记（课堂笔记 / 会议纪要两种类型）。本地优先：音频与转写永不出设备，仅摘要文本发往用户自行配置的 LLM 端点。

**当前状态**：v0.1 主干已闭环并在真机验证——录音（前台服务/熄屏存活）→ 端上实时转写 → WAV 落盘与锚点回听 → 2 分钟 LLM 微摘要 → 10 分钟阶段小结（ADR-0003 结构整理）→ 终稿（Paraformer 离线二遍 + LLM 校正，三份文本并存，ADR-0010 / ADR-0011）→ 结构化报告（DeepSeek 实调）。待补：ASR 热词/同音替换让实时稿也能看、待确认清单进报告、发布前把 API Key 迁到 Keystore。

- 设计决策：`docs/adr/`（0001 架构 · 0002 ASR 管线 · 0003 实时摘要 · 0004 报告抽取与模板 · 0005 本地数据与恢复 · 0006 发布与许可 · 0007 范围与验收 · 0008 实时模型改版 · 0009 存储实现 · 0010 二遍稿文本策略 · 0011 终稿 LLM 校正）
- 术语表：`GLOSSARY.md`
- 必办待办与已接受风险：ADR-0007 第 3、4 节

写代码前先读 ADR-0001 / 0002 / 0004。要推翻任何已决决策，请**新增**一份 ADR 并在其中声明它取代哪一份，不要直接改旧 ADR 的结论。
