package com.boguan.summarize

import com.boguan.model.SessionType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 所有发给 LLM 的 JSON Schema 必须是合法 JSON。
 *
 * 这些 schema 是手写字符串（部分字段用追加拼接维护），一次多余的引号或尾逗号
 * 就会让 strict 模式请求直接失败，而麦克风还在录音——所以用测试钉死。
 */
class PromptsSchemaTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    private fun assertValid(name: String, schema: String) {
        val error = runCatching { json.parseToJsonElement(schema) }.exceptionOrNull()
        assertTrue(error == null, name + " 不是合法 JSON：" + (error?.message ?: ""))
    }

    @Test
    fun all_prompt_schemas_are_valid_json() {
        for (type in SessionType.entries) {
            assertValid("micro/" + type, Prompts.microSummarySchema(type))
            assertValid("consolidated/" + type, Prompts.consolidatedSchema(type))
            assertValid("report/" + type, Prompts.reportSchema(type))
        }
        assertValid("correction", Prompts.correctionSchema())
    }

    @Test
    fun report_schema_requires_evidence_on_every_replayable_item() {
        for (type in SessionType.entries) {
            val schema = Prompts.reportSchema(type)
            val items = when (type) {
                SessionType.LECTURE -> listOf("key_points", "assignments", "emphasis", "questions", "topics", "unverified")
                SessionType.MEETING -> listOf("decisions", "risks", "action_items", "open_questions", "topics", "unverified")
            }
            val compact = schema.filterNot { it.isWhitespace() }
            for (key in items) {
                assertTrue(
                    compact.contains("\"" + key + "\":{\"type\":\"array\""),
                    type.toString() + " 缺少数组字段 " + key,
                )
            }
            // 每个条目都必须要求 evidence（ADR-0017）
            val requiredBlocks = Regex("\"required\":\\s*\\[([^]]*)\\]").findAll(schema).map { it.groupValues[1] }.toList()
            val withEvidence = requiredBlocks.count { it.contains("evidence") }
            assertTrue(
                withEvidence >= items.size,
                type.toString() + " 只有 " + withEvidence + " 个 required 段要求 evidence，期望 ≥" + items.size,
            )
        }
    }
}
