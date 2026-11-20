package com.boguan.summarize

import com.boguan.model.SessionType

/**
 * 提示词与 JSON Schema（ADR-0003 / ADR-0004）。
 *
 * 注意 strict 模式的硬约束：根节点必须是 object、**所有字段都必须进 required**、
 * 每个 object 必须 additionalProperties=false、不支持 oneOf/allOf/if-then-else。
 * 因此"可空"用 anyOf[string,null] 表达，"长度"写进 description。
 */
object Prompts {

    // ---------------- 微摘要 ----------------

    fun microSummarySchema(type: SessionType): String = when (type) {
        SessionType.LECTURE -> LECTURE_MICRO_SCHEMA
        SessionType.MEETING -> MEETING_MICRO_SCHEMA
    }

    fun microSummarySystemPrompt(type: SessionType): String = when (type) {
        SessionType.LECTURE ->
            "你是课堂实时摘要器。只依据给定转写片段抽取内容，禁止编造。" +
                "识别老师明确强调的内容（考点/重点/易错）；没有强调项就给空数组。"
        SessionType.MEETING ->
            "你是会议实时摘要器。只依据给定转写片段抽取内容，禁止编造。" +
                "区分「已达成的决定」与「待办事项」；负责人与截止日期不确定时给 null。"
    }

    fun microSummaryUserPrompt(previousSummary: String, windowText: String): String = buildString {
        appendLine("【上一段摘要】")
        appendLine(previousSummary.ifBlank { "（无）" })
        appendLine()
        appendLine("【本段转写】")
        appendLine(windowText)
    }

    private val LECTURE_MICRO_SCHEMA = """
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["title", "bullets", "emphasis"],
      "properties": {
        "title": { "type": "string", "description": "不超过 20 字的小节标题" },
        "bullets": {
          "type": "array",
          "description": "3-6 条要点，每条不超过 40 字",
          "items": { "type": "string" }
        },
        "emphasis": {
          "type": "array",
          "description": "老师明确强调的内容，没有则为空数组",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "kind"],
            "properties": {
              "text": { "type": "string" },
              "kind": { "type": "string", "enum": ["EXAM", "KEY", "PITFALL"] }
            }
          }
        }
      }
    }
    """.trimIndent()

    private val MEETING_MICRO_SCHEMA = """
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["title", "bullets", "decisions", "action_items"],
      "properties": {
        "title": { "type": "string", "description": "不超过 20 字的议题标题" },
        "bullets": {
          "type": "array",
          "description": "3-6 条要点，每条不超过 40 字",
          "items": { "type": "string" }
        },
        "decisions": {
          "type": "array",
          "description": "已达成的决定，没有则为空数组",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "status"],
            "properties": {
              "text": { "type": "string" },
              "status": { "type": "string", "enum": ["AGREED", "PENDING", "DISPUTED", "SHELVED"] }
            }
          }
        },
        "action_items": {
          "type": "array",
          "description": "待办事项，没有则为空数组",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "owner", "deadline"],
            "properties": {
              "text": { "type": "string" },
              "owner": { "anyOf": [ { "type": "string" }, { "type": "null" } ] },
              "deadline": { "anyOf": [ { "type": "string" }, { "type": "null" } ] }
            }
          }
        }
      }
    }
    """.trimIndent()

    // ---------------- 结构整理（每 10 分钟，ADR-0003） ----------------

    fun consolidatedSchema(type: SessionType): String = when (type) {
        SessionType.LECTURE -> LECTURE_CONSOLIDATED_SCHEMA
        SessionType.MEETING -> MEETING_CONSOLIDATED_SCHEMA
    }

    fun consolidatedSystemPrompt(type: SessionType): String = when (type) {
        SessionType.LECTURE ->
            "你是课堂阶段整理器。把若干条相邻的 2 分钟微摘要**合并去重**成一份阶段小结，只依据给定摘要，禁止编造。" +
                "重复的要点合并为一条；保留老师强调的考点/易错；摘要里没有的信息不要补。"
        SessionType.MEETING ->
            "你是会议阶段整理器。把若干条相邻的 2 分钟微摘要**合并去重**成一份阶段小结，只依据给定摘要，禁止编造。" +
                "同一个决定被多次提到时只保留一条并取最新状态；负责人与截止日期不确定时给 null。"
    }

    /** [previous] 是上一阶段的小结（给模型维持脉络），[microSummaries] 是本阶段的微摘要列表。 */
    fun consolidatedUserPrompt(previous: String, microSummaries: String): String = buildString {
        appendLine("【上一阶段小结】")
        appendLine(previous.ifBlank { "（无）" })
        appendLine()
        appendLine("【本阶段微摘要（按时间顺序）】")
        appendLine(microSummaries)
    }

    private val LECTURE_CONSOLIDATED_SCHEMA = """
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["title", "summary", "points", "questions"],
      "properties": {
        "title": { "type": "string", "description": "阶段标题，不超过 20 字" },
        "summary": { "type": "string", "description": "2-3 句阶段小结，不超过 120 字" },
        "points": {
          "type": "array",
          "description": "合并去重后的关键要点，5-10 条，每条不超过 40 字",
          "items": { "type": "string" }
        },
        "questions": {
          "type": "array",
          "description": "尚未解决的疑问，没有则为空数组",
          "items": { "type": "string" }
        }
      }
    }
    """.trimIndent()

    private val MEETING_CONSOLIDATED_SCHEMA = """
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["title", "summary", "decisions", "action_items"],
      "properties": {
        "title": { "type": "string", "description": "阶段标题，不超过 20 字" },
        "summary": { "type": "string", "description": "2-3 句阶段小结，不超过 120 字" },
        "decisions": {
          "type": "array",
          "description": "合并去重后的决定，没有则为空数组",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "status"],
            "properties": {
              "text": { "type": "string" },
              "status": { "type": "string", "enum": ["AGREED", "PENDING", "DISPUTED", "SHELVED"] }
            }
          }
        },
        "action_items": {
          "type": "array",
          "description": "合并去重后的待办，没有则为空数组",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "owner", "deadline"],
            "properties": {
              "text": { "type": "string" },
              "owner": { "anyOf": [ { "type": "string" }, { "type": "null" } ] },
              "deadline": { "anyOf": [ { "type": "string" }, { "type": "null" } ] }
            }
          }
        }
      }
    }
    """.trimIndent()

    // ---------------- 终稿 LLM 校正（ADR-0011） ----------------

    fun correctionSchema(): String = CORRECTION_SCHEMA

    /**
     * 校正指令。要点：只改识别错误、不改写不总结、术语跟术语表、数学变量名写清楚。
     * 这条 prompt 是在真实课堂录音上验证过的（ADR-0011）。
     */
    fun correctionSystemPrompt(type: SessionType, glossary: String): String = buildString {
        appendLine("你是中文大学" + (if (type == SessionType.LECTURE) "课堂" else "会议") + "录音的转写校正器。")
        appendLine("用户会给出同一段音频的两份 ASR 结果（实时稿、离线稿，可能为空）。请输出**校正后的唯一文本**。")
        appendLine("规则：")
        appendLine("1. 只修正识别错误（同音字、术语、数字、英文变量名），不改变原意、不增删内容、不总结、不翻译；")
        appendLine("2. 数学/专业术语按术语表写法；变量名写成 A_ij、a_i、x_n 这类清晰形式；")
        appendLine("3. 两份稿都错但上下文能确定时按上下文修正（例如把「女子是」修正为「余子式」），不能确定就保留更通顺的一份；")
        appendLine("4. 保持口语原貌，不要润色成书面语；")
        appendLine("5. 只输出严格 JSON，不要解释。")
        if (glossary.isNotBlank()) {
            appendLine()
            appendLine("【本课术语表】")
            appendLine(glossary.trim())
        }
    }

    fun correctionUserPrompt(
        contextBefore: String,
        items: String,
        contextAfter: String,
    ): String = buildString {
        if (contextBefore.isNotBlank()) {
            appendLine("【前文（仅用于理解，不需要校正）】")
            appendLine(contextBefore)
            appendLine()
        }
        appendLine("【待校正段落】")
        appendLine(items)
        if (contextAfter.isNotBlank()) {
            appendLine()
            appendLine("【后文（仅用于理解，不需要校正）】")
            appendLine(contextAfter)
        }
        appendLine()
        appendLine("输出 JSON：{\"segments\":[{\"i\":段落编号,\"text\":\"校正后的文本\"}]}，编号必须与输入一致。")
    }

    private val CORRECTION_SCHEMA = """
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["segments"],
      "properties": {
        "segments": {
          "type": "array",
          "description": "与输入段落一一对应",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["i", "text"],
            "properties": {
              "i": { "type": "integer", "description": "输入里的段落编号" },
              "text": { "type": "string", "description": "校正后的该段文本" }
            }
          }
        }
      }
    }
    """.trimIndent()

    // ---------------- 会中问答 ----------------

    const val QA_SYSTEM_PROMPT: String =
        "你是课堂/会议助手。只能基于给定上下文回答；上下文里没有的信息就直说\"记录里没有提到\"，不要猜测。"

    fun qaUserPrompt(context: String, question: String): String = buildString {
        appendLine("【上下文】")
        appendLine(context)
        appendLine()
        appendLine("【问题】")
        appendLine(question)
    }

    // ---------------- 终稿报告抽取 ----------------

    fun reportSchema(type: SessionType): String = when (type) {
        SessionType.LECTURE -> LECTURE_REPORT_SCHEMA
        SessionType.MEETING -> MEETING_REPORT_SCHEMA
    }

    fun reportSystemPrompt(type: SessionType): String = when (type) {
        SessionType.LECTURE ->
            "你是课堂笔记整理器。只依据给定的课堂转写与微摘要生成结构化笔记，禁止编造。" +
                "作业与截止日期只在原文明确提到时才填，否则给 null；掌握状态按内容判断：" +
                "老师讲清且无歧义用 CLEAR，提到需要复习/易错用 NEEDS_REVIEW，学生明确表示没听懂用 UNSURE。" +
                "另外把「转写可能不准、需要人耳核对」的片段放进 unverified：" +
                "数字、人名、书名、日期、金额、作业要求、关键术语——尤其是看起来像同音错或前后矛盾的表述；" +
                "不确定就不要硬写进正文，宁可放进 unverified。" +
                "每条可回听的内容（知识点/强调/作业/问题/主题/待确认）都必须给 evidence：" +
                "从转写里逐字复制的 6–20 字片段，用于反查回听时间；找不到逐字片段就给空字符串，不要编。"
        SessionType.MEETING ->
            "你是会议纪要整理器。只依据给定的转写与微摘要生成结构化纪要，禁止编造。" +
                "负责人与截止日期不确定时必须给 null，不要猜；决定状态只能是四个枚举值之一。" +
                "另外把「转写可能不准、需要人耳核对」的片段放进 unverified：" +
                "人名、数字、日期、金额、负责人、决议措辞——尤其是同音错或前后矛盾的表述。" +
                "每条可回听的内容（决议/待办/风险/问题/主题/待确认）都必须给 evidence：" +
                "从转写里逐字复制的 6–20 字片段；找不到就给空字符串，不要编。"
    }

    /**
     * [hints] 是「转写可疑点」提示：实时稿与 LLM 校正稿差异较大的段落，
     * 提示模型这些地方可能听错，应当重点核对（会进入 unverified）。
     */
    fun reportUserPrompt(transcript: String, summaries: String, hints: String = ""): String = buildString {
        if (hints.isNotBlank()) {
            appendLine("【转写可疑点（实时稿与校正稿差异较大的段落）】")
            appendLine(hints)
            appendLine()
        }
        appendLine("【微摘要】")
        appendLine(summaries.ifBlank { "（无）" })
        appendLine()
        appendLine("【完整转写】")
        appendLine(transcript)
    }

    private val LECTURE_REPORT_SCHEMA = """
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["one_liner", "key_points", "assignments", "emphasis", "questions", "topics", "unverified"],
      "properties": {
        "one_liner": { "type": "string", "description": "一句话概括整节课，不超过 40 字" },
        "key_points": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["title", "detail", "status", "evidence"],
            "properties": {
              "title": { "type": "string", "description": "知识点名称，不超过 15 字" },
              "detail": { "type": "string", "description": "解释，不超过 120 字" },
              "status": { "type": "string", "enum": ["CLEAR", "NEEDS_REVIEW", "UNSURE"] },
              "evidence": { "type": "string", "description": "从转写中逐字复制的 6-20 字片段；找不到给空字符串" }
            }
          }
        },
        "assignments": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "deadline", "evidence"],
            "properties": {
              "text": { "type": "string" },
              "deadline": { "anyOf": [ { "type": "string" }, { "type": "null" } ] },
              "evidence": { "type": "string", "description": "从转写中逐字复制的 6-20 字片段；找不到给空字符串" }
            }
          }
        },
        "emphasis": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "kind", "evidence"],
            "properties": {
              "text": { "type": "string" },
              "kind": { "type": "string", "enum": ["EXAM", "KEY", "PITFALL"] },
              "evidence": { "type": "string", "description": "从转写中逐字复制的 6-20 字片段；找不到给空字符串" }
            }
          }
        },
        "questions": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "answer", "evidence"],
            "properties": {
              "text": { "type": "string" },
              "answer": { "anyOf": [ { "type": "string" }, { "type": "null" } ] },
              "evidence": { "type": "string", "description": "从转写中逐字复制的 6-20 字片段；找不到给空字符串" }
            }
          }
        },
        "topics": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["title", "evidence"],
            "properties": {
              "title": { "type": "string" },
              "evidence": { "type": "string", "description": "从转写中逐字复制的 6-20 字片段；找不到给空字符串" }
            }
          }
        },
        "unverified": {
          "type": "array",
          "description": "转写可能不准、需要人耳核对的条目（数字/人名/日期/金额/作业要求/术语），没有则为空数组",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "reason", "evidence"],
            "properties": {
              "text": { "type": "string", "description": "原文里的可疑片段，尽量照抄" },
              "reason": { "type": "string", "description": "为什么可疑，不超过 30 字" },
              "evidence": { "type": "string", "description": "从转写中逐字复制的 6-20 字片段；找不到给空字符串" }
            }
          }
        }
      }
    }
    """.trimIndent()

    private val MEETING_REPORT_SCHEMA = """
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["one_liner", "decisions", "action_items", "risks", "open_questions", "topics", "unverified"],
      "properties": {
        "one_liner": { "type": "string", "description": "一句话概括会议结论，不超过 40 字" },
        "decisions": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "status", "evidence"],
            "properties": {
              "text": { "type": "string" },
              "status": { "type": "string", "enum": ["AGREED", "PENDING", "DISPUTED", "SHELVED"] },
              "evidence": { "type": "string", "description": "从转写中逐字复制的 6-20 字片段；找不到给空字符串" }
            }
          }
        },
        "action_items": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "owner", "deadline", "evidence"],
            "properties": {
              "text": { "type": "string" },
              "owner": { "anyOf": [ { "type": "string" }, { "type": "null" } ] },
              "deadline": { "anyOf": [ { "type": "string" }, { "type": "null" } ] },
              "evidence": { "type": "string", "description": "从转写中逐字复制的 6-20 字片段；找不到给空字符串" }
            }
          }
        },
        "risks": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "evidence"],
            "properties": {
              "text": { "type": "string" },
              "evidence": { "type": "string", "description": "从转写中逐字复制的 6-20 字片段；找不到给空字符串" }
            }
          }
        },
        "open_questions": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "evidence"],
            "properties": {
              "text": { "type": "string" },
              "evidence": { "type": "string", "description": "从转写中逐字复制的 6-20 字片段；找不到给空字符串" }
            }
          }
        },
        "topics": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["title", "evidence"],
            "properties": {
              "title": { "type": "string" },
              "evidence": { "type": "string", "description": "从转写中逐字复制的 6-20 字片段；找不到给空字符串" }
            }
          }
        },
        "unverified": {
          "type": "array",
          "description": "转写可能不准、需要人耳核对的条目（数字/人名/日期/金额/负责人/术语），没有则为空数组",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "reason", "evidence"],
            "properties": {
              "text": { "type": "string", "description": "原文里的可疑片段，尽量照抄" },
              "reason": { "type": "string", "description": "为什么可疑，不超过 30 字" },
              "evidence": { "type": "string", "description": "从转写中逐字复制的 6-20 字片段；找不到给空字符串" }
            }
          }
        }
      }
    }
    """.trimIndent()
}

