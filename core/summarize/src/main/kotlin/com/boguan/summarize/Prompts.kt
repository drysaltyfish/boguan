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
}
