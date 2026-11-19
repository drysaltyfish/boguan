package com.boguan.summarize

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CorrectionParserTest {

    @Test
    fun parses_segments() {
        val raw = """
            {"segments":[{"i":3,"text":"A_11 在 C 中的余子式"},{"i":4,"text":"你总共得了多少公式了"}]}
        """.trimIndent()
        val out = CorrectionParser.parse(raw)
        assertEquals(2, out.size)
        assertEquals(3, out[0].index)
        assertEquals("A_11 在 C 中的余子式", out[0].text)
    }

    @Test
    fun skips_items_without_index_or_text() {
        val raw = """{"segments":[{"i":1},{"text":"没有编号"},{"i":2,"text":"  "},{"i":5,"text":"有效"}]}"""
        val out = CorrectionParser.parse(raw)
        assertEquals(listOf(5), out.map { it.index })
    }

    @Test
    fun throws_when_segments_missing() {
        var threw = false
        try {
            CorrectionParser.parse("""{"foo":1}""")
        } catch (t: Throwable) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun correction_prompt_carries_glossary_and_context() {
        val system = Prompts.correctionSystemPrompt(com.boguan.model.SessionType.LECTURE, "余子式、代数余子式")
        assertTrue(system.contains("余子式、代数余子式"))
        val user = Prompts.correctionUserPrompt("[0] 前文", "[0] 实时：a\n  离线：b", "[2] 后文")
        assertTrue(user.contains("前文") && user.contains("待校正段落") && user.contains("后文"))
    }
}
