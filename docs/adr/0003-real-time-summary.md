# ADR-0003：实时摘要 —— 类型化节拍、供应商抽象与降级

- **状态**：已接受
- **日期**：2026-11-17
- **相关**：ADR-0004（抽取与模板）、ADR-0005（重放与离线）

## 背景

- 行业里唯一公开的「会中自动滚动摘要」节奏是腾讯会议的 **2 分钟**；其余产品要么按需提问，要么会后一次性生成。本项目采用 2 分钟。
- LLM 成本不是约束：单节 90 分钟课堂约 **¥0.5–2**（按调用档位不同），100 节约 ¥50–200。选型应优先看**延迟、结构稳定性、可达性**，而不是抠单价。
- **OpenAI 官方不对中国大陆/香港提供服务**；直连在目标使用地大概率不可用。而产品形态是「用户自填 key、无后端」。

## 决策

### 1. 摘要节奏与分层

| 层 | 频率 | 输入 | 输出 |
|---|---|---|---|
| 微摘要 | **每 2 分钟** | 上一段微摘要 + 本窗口转写 | 类型化要点（3–5 条 bullet） |
| 结构整理 | **每 10 分钟** | 前若干条微摘要 | 合并去重后的阶段小结 |
| 终稿抽取 | 停录 + 终稿完成后 | 全量微摘要 + 按需回看原文 | 公共信封 + 类型载荷（ADR-0004） |

**按类型化**：微摘要的抽取目标随 `session.type` 不同（课堂=知识点/强调/疑问；会议=议题/疑似决议/待办）。因此微摘要 **每类型一套 Schema + prompt**（ADR-0004 的类型注册表）。

**逐段处理**：窗口内转写 + 上段摘要，不重算全量。跨窗口的长期脉络会被压缩——这是已知取舍，须在 README 的产品限制里如实写明。

### 2. 供应商抽象

- 协议层只认 **OpenAI 兼容**：`POST {base_url}/chat/completions` + `response_format`。
- 内置 provider profile：**OpenAI** / **中国大陆可直连的一家** / **自定义 base_url**。
- **模型名不硬编码**：写进配置；启动时用 `/v1/models` 校验，失败则回退到内置默认并提示。
- 档位：微摘要用轻档（`reasoning_effort: none`）、会中问答用最轻档、终稿报告用强档（可走 Flex/Batch 半价）。
- **不做自建 BFF 代理**（与「无后端、纯本地」冲突）。用户自备 key，存 Android Keystore。

### 3. 结构化输出

严格 JSON Schema（`strict: true`）的硬约束必须遵守，否则直接 400 且**不可重试**：

- 根节点必须是 `object`；每个 object 必须 `additionalProperties: false`；
- **每个字段都必须进 `required`**（strict 无“可选字段”，可空用 `anyOf: [{type:"string"},{type:"null"}]` 表达）；
- 不支持 `oneOf` / `allOf` / `if-then-else` / `minLength` 等关键字（长度/格式约束写进 `description`，本地校验兜底）。

### 4. 异常与降级（四级）

| 级别 | 策略 |
|---|---|
| L1 | 同模型重问 1 次（把解析错误回灌，扩容 `max_completion_tokens`） |
| L2 | 降级到 `json_object` 模式 + prompt 内联 Schema + 本地校验（zod / kotlinx-serialization） |
| L3 | 降级到纯文本 + 固定格式解析（至少保住标题与 bullet） |
| L4 | 该段标记 `degraded: true`，UI 显示「本段摘要不可用」+ 重试按钮；会中问答降级为「直接回显检索到的原文片段」 |

其它硬规则：

- `refusal` **先于** `finish_reason` 判定——拒答可能与 `stop` 同时出现，只判 `content_filter` 会把拒答文本当摘要渲染。
- `finish_reason == "length"` 时 `content` 是半截 JSON：先扩容再重试，不能当网络错误无限重试。
- 自带重试关闭（`maxRetries: 0`），重试统一在控制层：`min(20s, 500ms * 2^n)` × 全抖动；`429` 读 `Retry-After`；**400 / 401 / 403 / 404 绝不重试**；微摘要总预算 ≤ 90 s，超时即跳过该段并标记 gap，绝不阻塞录音。
- 每次调用落 `llm_call` 表（prompt / response / request-id / usage），支持幂等重放。

### 5. 网络策略

- 转写永不依赖网络（本地）。
- 摘要是**尽力而为 + 落后补做**：网络可用则正常推进；不可用则排队，恢复后按时间顺序补齐（允许每 3 段合并一次以省 token）。
- UI 明确显示「**摘要已更新至 mm:ss**」，不假装实时。

## 后果

**正面**：成本可控、供应商可换、结构可校验、断网不丢录音。

**负面**：类型化微摘要带来 2 套 Schema 与 prompt 的维护成本；弱网下摘要滞后是常态，需要 UI 诚实表达。

## 参考

- 腾讯会议 AI 纪要 2 分钟节奏：https://meeting.tencent.com/news/aijy20250820.html
- 结构化输出约束（Azure 同源文档）：https://learn.microsoft.com/en-us/azure/ai-foundry/openai/how-to/structured-outputs
- 429 与重试：https://help.openai.com/en/articles/5955604-how-can-i-solve-429-too-many-requests-errors
