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
                "老师讲清且无歧义用 CLEAR，提到需要复习/易错用 NEEDS_REVIEW，学生明确表示没听懂用 UNSURE。"
        SessionType.MEETING ->
            "你是会议纪要整理器。只依据给定的转写与微摘要生成结构化纪要，禁止编造。" +
                "负责人与截止日期不确定时必须给 null，不要猜；决定状态只能是四个枚举值之一。"
    }

    fun reportUserPrompt(transcript: String, summaries: String): String = buildString {
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
      "required": ["one_liner", "key_points", "assignments", "emphasis", "questions", "topics"],
      "properties": {
        "one_liner": { "type": "string", "description": "一句话概括整节课，不超过 40 字" },
        "key_points": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["title", "detail", "status"],
            "properties": {
              "title": { "type": "string", "description": "知识点名称，不超过 15 字" },
              "detail": { "type": "string", "description": "解释，不超过 120 字" },
              "status": { "type": "string", "enum": ["CLEAR", "NEEDS_REVIEW", "UNSURE"] }
            }
          }
        },
        "assignments": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "deadline"],
            "properties": {
              "text": { "type": "string" },
              "deadline": { "anyOf": [ { "type": "string" }, { "type": "null" } ] }
            }
          }
        },
        "emphasis": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "kind"],
            "properties": {
              "text": { "type": "string" },
              "kind": { "type": "string", "enum": ["EXAM", "KEY", "PITFALL"] }
            }
          }
        },
        "questions": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text", "answer"],
            "properties": {
              "text": { "type": "string" },
              "answer": { "anyOf": [ { "type": "string" }, { "type": "null" } ] }
            }
          }
        },
        "topics": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["title"],
            "properties": { "title": { "type": "string" } }
          }
        }
      }
    }
    """.trimIndent()

    private val MEETING_REPORT_SCHEMA = """
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["one_liner", "decisions", "action_items", "risks", "open_questions", "topics"],
      "properties": {
        "one_liner": { "type": "string", "description": "一句话概括会议结论，不超过 40 字" },
        "decisions": {
          "type": "array",
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
        },
        "risks": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text"],
            "properties": { "text": { "type": "string" } }
          }
        },
        "open_questions": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["text"],
            "properties": { "text": { "type": "string" } }
          }
        },
        "topics": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["title"],
            "properties": { "title": { "type": "string" } }
          }
        }
      }
    }
    """.trimIndent()
}

