# ADR-0017：报告要点带证据片段与时间锚点，可一键回听

- **状态**：已接受；**细化 [ADR-0004](0004-report-extraction-and-templates.md) 的报告 schema、落地 ADR-0007 的「≥95% 要点可回听」门槛、收紧 [ADR-0016](0016-acceptance-without-human-transcript.md) §3 的「报告可信度」口径**
- **日期**：2026-11-20
- **相关**：ADR-0004（报告抽取与模板）、ADR-0007（范围与验收）、ADR-0011（终稿校正）、ADR-0016（验收口径）

## 背景

ADR-0007 把「专家/老师列出的要点中 ≥95% 能在录音里回听到对应片段」写进 v0.1 验收。但报告只有文字，LLM 给不出「这是第几分钟」：

- 待确认清单能用「实时稿 vs 校正稿差异最大的段落」反推锚点（旧实现），但知识要点/强调/作业同样需要回听核对，却没有锚点。
- ADR-0016 量到的「要点可定位率 47%」只是拿要点前 12 字去转写里做子串匹配，量的是**改写幅度**，不是「能不能回听」。ADR-0016 已诚实注明该行不能当作 95% 门槛的结论。

根因是报告 schema 缺一样东西：**条目的原文出处**。

## 决策

### 1. 报告 schema 给每条可回听内容加 `evidence`

`evidence` = **从转写里逐字复制的 6–20 字片段**（不是改写、不是标题）。

| 类型 | 带 evidence 的条目 |
|---|---|
| 课堂 | `key_points` / `assignments` / `emphasis` / `questions` / `topics` / `unverified` |
| 会议 | `decisions` / `risks` / `action_items` / `open_questions` / `topics` / `unverified` |

- 找不到逐字片段时**给空字符串**，不许编造。
- 微摘要 / 阶段小结**不加**（不用于回听，加了纯烧 token）。
- 解析在 `ReportParser`，与报告其余字段同一条路径；缺字段时回退空串，不抛错。

### 2. 锚点由本地反查产生，不让 LLM 猜时间

`core/report/ReportAnchorer`：

- 归一化（保留字母数字、小写）后，把 evidence 当作子串在每段的展示文本（`correctedText > text > offlineText`）里查找；命中即取该段 `[startMs, endMs]`。
- `topics` 没给 evidence 时退化为用标题查（分主题本来就是概括）。
- `unverified` 允许「探针」：从 evidence 里抽连续数字/英文片段（≥2 字符，长的优先）再查——人名、年份最易听错，全文匹配常失败但数字能命中。
- **查不到就留空**。宁可没有锚点，也不给一个错误的时间点。
- 锚点在**查看时重算**，不落库：段落数据完整、算法纯函数，重算比存一份会漂移的副本可靠。

### 3. 落库与渲染

- `report.payload_json` 存 LLM 原始 JSON（含 evidence）；`rendered_md` 存渲染文本。
- 渲染时每个能锚定的条目在行尾加 `（[mm:ss]）`；分主题在标题里给 `（mm:ss – mm:ss）`。Markdown 因此自带回听索引，复制出去也不丢。

### 4. UI：报告要点回听卡片

历史详情页在「结构化报告」**上方**新增 `报告要点回听（N）` 卡片：每条显示 `[mm:ss] 小节` + 文本 + 「回听」按钮，点击用 `playRange(startMs, endMs)` 播 WAV 片段（用负数伪 id 与段落播放互斥）。播放中该行按钮变「停止」、正文转主题色，再点即中止——一屏只有一个播放源，状态可感知。最多列 40 条（长报告先覆盖前 40）。

### 5. 验收口径改为「可回听率」

`tools/acceptance_report.py` 直接读 `payload_json`，用**与 App 相同的算法**逐条重算：

```
可回听率 = evidence（或兜底文本）能在转写段落中反查到的条目数 / 报告全部可回听条目数
```

阈值沿用 ADR-0007：**≥95%**。旧「前 12 字子串」指标降级为对照项，不再用于判定。

## 后果

**正面**

- 报告从「文字墙」变成可核对的东西：每条要点都能一键回到原话。
- 报告里的可疑点（数字、人名）在 Markdown 里也带时间戳，离线分享同样能索引。
- ADR-0007 的 95% 门槛第一次**可测**，而不是靠代理指标猜。

**负面 / 代价**

- 每条目多输出 6–20 字 evidence，报告 prompt 输出 token 增加（本场报告 7310 字符，较旧版多约 5%）。
- LLM 并不总是逐字复制：实测 46 条全部带了 evidence，但只有 41 条能反查到——**「有 evidence」≠「能回听」**，所以必须保留验收脚本兜底。
- 回听卡片最多 40 条；超长报告需要分页（v0.1 接受）。
- 探针只给 `unverified` 用；给所有条目放开容易锚到错误位置。

**遗留**

- 提高 evidence 逐字率：prompt 里已写死「逐字复制」，仍需更强约束（few-shot / 生成后校验 evidence 是否在转写里，不在就重试一次）。
- 锚点未落库：将来若清理历史段落，旧报告会失去锚点。

## 实测（2026-11-19 课堂，22:51，11:45–12:08）

旧报告 33 条要点 / 6964 字符；改用新 schema 重新生成后：

| 指标 | 数值 |
|---|---|
| 报告长度 | 7310 字符 |
| 可回听条目 | 46 条（分主题 7 / 核心知识点 13 / 老师强调 9 / 问题与问答 3 / 待确认 14） |
| 带 evidence | 46/46（100%） |
| 可反查到锚点 | **41/46（89%）** |
| Markdown 里的 `（[mm:ss]）` 标注 | 34 处（分主题另在标题内） |
| 旧口径对照 | 14/30（47%） |

未命中的 5 条都是 LLM 改写过、转写里没有逐字对应的概括句，例如「精神源于实践又指引实践」「文化发展道路」「如何评价张凌赫的粉底液将军现象？」。89% < 95%，**门槛仍未达成**，但已从「不可测」变成「差 6 个百分点且知道差在哪」。


## 设备验收（2026-11-20，一加 12 / Android 16）

在真机上按用户路径走了一遍（历史列表 → 11-19 11:45 场次 → 详情）：

| 检查项 | 结果 |
|---|---|
| 详情页卡片顺序 | ✅ 阶段小结（3）→ 报告要点回听（40）→ 结构化报告 |
| 卡片标题 / N | ✅ 「报告要点回听（40）」，与「最多 40 条」上限一致 |
| 锚点行 | ✅ 形如 `[00:00] 核心知识点`「长征精神与文化自信」+「回听」；抽查 [00:00] / [00:59] / [05:34] 均落在正确段落 |
| 点击回听 | ✅ `dumpsys audio` 显示 MediaPlayer started，16 kHz 单声道（与 WAV 一致），片段播完自动停止 |
| 播放态 | 首轮 ❌ 无任何可见反馈、也无法中止 → **已修**：行内按钮变「停止」、正文转主题色；二轮复验 ✅（点击后 400 ms 内变「停止」，再点回「回听」且音频 stopped/released） |
| 崩溃 / 遮挡 | ✅ 无 AndroidRuntime 崩溃，前台稳定在 `com.boguan.app/MainActivity` |

限制：设备媒体音量为 0，只能靠 `dumpsys audio` 证明起播/停止，未做人耳听感确认。

**另一处顺手修掉的回归**：给报告 schema 加 evidence 的那次批量改动，误伤了微摘要/阶段小结 schema（多了一个引号、留下尾逗号），会让录制中的微摘要请求直接失败。已修复，并新增 `PromptsSchemaTest`（所有 schema 必须是合法 JSON；报告 schema 每个条目必须 required evidence），把这个坑钉死。

## 复审触发条件

1. 可回听率连续两场 <90%（说明 prompt 的 evidence 约束失效）→ 加生成后校验/重试；
2. 报告条目数稳定 >40 → 回听卡片改分页或按小节折叠；
3. 引入「段落清理/压缩」策略 → 锚点必须落库。

## 参考

- 模型：`core/model/src/main/kotlin/com/boguan/model/Report.kt`（`evidence` 字段）
- 锚定：`core/report/src/main/kotlin/com/boguan/report/ReportAnchorer.kt`
- 渲染：`core/report/src/main/kotlin/com/boguan/report/ReportRenderer.kt`
- prompt：`core/summarize/src/main/kotlin/com/boguan/summarize/Prompts.kt`
- UI：`app/src/main/java/com/boguan/app/MainActivity.kt`（`ReportRowsCard`）
- 验收：`tools/acceptance_report.py`（`anchor_coverage`）
- 测试：`ReportAnchorerPayloadTest` / `ReportRendererAnchorTest` / `ReportParserEvidenceTest` / `PromptsSchemaTest`
- 数据：`docs/acceptance/2026-11-19-class-summary.md`
