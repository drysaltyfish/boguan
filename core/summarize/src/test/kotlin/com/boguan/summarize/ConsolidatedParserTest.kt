package com.boguan.summarize

import com.boguan.model.SessionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsolidatedParserTest {

    @Test
    fun parses_lecture_payload() {
        val raw = """
            {"title":"行列式展开","summary":"讲了按行展开与代数余子式。",
             "points":["余子式定义","代数余子式符号规律"],
             "questions":["n 阶的归纳法还没讲完"]}
        """.trimIndent()
        val p = ConsolidatedParser.parse(raw, SessionType.LECTURE)
        assertEquals("行列式展开", p.title)
        assertEquals(2, p.points.size)
        assertEquals(listOf("疑问：n 阶的归纳法还没讲完"), p.displayLines().filter { it.startsWith("疑问") })
    }

    @Test
    fun parses_meeting_payload_with_decisions_and_actions() {
        val raw = """
            {"title":"排期","summary":"确定了发布时间。",
             "decisions":[{"text":"11 月底发 v0.1","status":"AGREED"},{"text":"是否做导出","status":"SHELVED"}],
             "action_items":[{"text":"补测试","owner":"张三","deadline":null},
                             {"text":"写文档","owner":null,"deadline":"下周五"}]}
        """.trimIndent()
        val p = ConsolidatedParser.parse(raw, SessionType.MEETING)
        assertEquals(2, p.decisions.size)
        assertEquals("已达成", ConsolidatedPayload.decisionLabel(p.decisions[0].status))
        assertEquals(2, p.actionItems.size)
        assertEquals("张三", p.actionItems[0].owner)
        assertEquals("下周五", p.actionItems[1].deadline)
        val lines = p.displayLines()
        assertTrue(lines.any { it == "决定：11 月底发 v0.1（已达成）" })
        assertTrue(lines.any { it == "待办：写文档（负责人未定 / 下周五）" })
    }

    @Test
    fun tolerates_missing_arrays() {
        val p = ConsolidatedParser.parse("""{"title":"空阶段","summary":""}""", SessionType.LECTURE)
        assertEquals("空阶段", p.title)
        assertTrue(p.points.isEmpty())
        assertTrue(p.displayLines().isEmpty())
    }

    @Test
    fun rejects_non_object() {
        var threw = false
        try {
            ConsolidatedParser.parse("[]", SessionType.LECTURE)
        } catch (t: Throwable) {
            threw = true
        }
        assertTrue(threw)
    }
}
