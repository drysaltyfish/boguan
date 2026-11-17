# ADR-0004：报告抽取与模板 —— 公共信封 + 类型载荷

- **状态**：已接受
- **日期**：2026-11-17
- **修订记录**：初版误设为「一套 IR + 两套纯渲染模板」；因**总结内容本身随类型不同**，改为两层抽取。
- **相关**：ADR-0003（摘要）、ADR-0005（存储）

## 背景

产品要**同时**支持「课堂笔记」与「会议纪要」。二者不只是显示不同：**LLM 被要求抽取的东西不同**（课堂要知识点/考点/作业；会议要决议/负责人/待办/风险）。因此不能只做渲染层的分叉。

## 决策

### 1. 两层数据

**公共信封（类型无关）**

```
meta         { type, title, startedAt, durationSec, audioSource, lecturerOrHost }
topics[]     { title, startMs, endMs }
transcript   { segments[] { text, startMs, endMs, confidence } }   // 终稿版
anchors      { [itemId]: { startMs, endMs } }                      // 条目→原文时间段
qa_pairs[]   { question, answer, askedAtMs, anchor }
```

**类型载荷（每类型独立 JSON Schema + prompt）**

`lecture`：

```
key_points[]   { title, detail, emphasis_level, mastery_status }   // mastery: 已讲清 | 需复习 | 有疑问
assignments[]  { text, deadline|null, anchor }
emphasis[]     { text, kind }                                      // kind: 考点 | 重点 | 易错
my_questions[] { text, answer|null }
```

`meeting`：

```
decisions[]    { text, status }                                    // status: 已达成 | 待讨论 | 有分歧 | 搁置
action_items[] { text, owner|null, deadline|null, anchor }
risks[]        { text, kind }
open_questions[]{ text }
```

### 2. 类型注册表

每类型 = `{ schema, prompt, renderer }`，注册即用。**v0.1 只注册 lecture / meeting 两类**。加第三类（访谈、复盘、答辩）只加文件，不改核心。

> 这是**内部注册表**，不是插件系统（插件已明确不做）。

### 3. 类型的确定与切换

- 录音开始时选择（默认记住上次；首次安装默认 `lecture`）。
- 会中切换：**只影响之后的段**，已生成的微摘要不回溯。
- 会后切换：**重跑类型抽取**（LLM 调用，约几十次、几十秒、¥0.2–0.5），之后渲染免费。

### 4. 报告是投影

报告 = 公共信封 + 类型载荷 → 渲染。终稿生成后**重新渲染覆盖**，**不做就地编辑**。

### 5. 可信度三件套（核心差异化）

1. **锚点回听**：每个结构化条目映射到原文时间段，一键回放。
2. **低置信度实体高亮**：数字、人名、日期、金额、作业要求等实体若置信度低，渲染为「待确认」。
3. **待确认清单**：报告顶部汇总所有待确认项，供课后核对。

依据：Granola 的「放大镜回溯原文」证明这是同类产品验证过的核心价值；在 8–10% CER 的现实下，它是报告唯一的可信度来源。

## 后果

**正面**：一套校验/降级/重放机制服务全部类型；新增类型成本低；报告可无损再生成。

**负面**：Schema 数量 = 2 类型 × 2 阶段 = 4 套；会后切换类型要花 LLM 费用与时间（UI 必须提示）。

## 备选方案

| 方案 | 否决理由 |
|---|---|
| 单一通用 Schema + 两套渲染 | 抽不出来「考点/作业」与「决议/负责人」这种类型独有的东西 |
| 完全独立的两套管线 | 校验、降级、重放、渲染全部重复，维护成本 ≈ 2× |
| 让 LLM 自动识别类型 | 识别错会产出结构完全错误的文档，用户未必能立刻察觉 |
