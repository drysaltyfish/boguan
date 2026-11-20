package com.boguan.summarize

import com.boguan.model.SessionType
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportParserEvidenceTest {

    @Test
    fun parses_evidence_for_lecture_items() {
        val raw = """
            {"one_liner":"讲长征","key_points":[
              {"title":"长征是曲线","detail":"不是直线","status":"CLEAR","evidence":"长征不是直线也不是注定的"}],
             "assignments":[],"emphasis":[],
             "questions":[{"text":"为什么走曲线","answer":null,"evidence":"而是一条曲线"}],
             "topics":[{"title":"长征","evidence":"长征不是直线"}],
             "unverified":[{"text":"一百零五周年","reason":"数字矛盾","evidence":"一百零五周年"}]}
        """.trimIndent()
        val p = ReportParser.parse(raw, SessionType.LECTURE)
        assertEquals("长征不是直线也不是注定的", p.envelope.keyPoints.single().evidence)
        assertEquals("而是一条曲线", p.envelope.questions.single().evidence)
        assertEquals("长征不是直线", p.envelope.topics.single().evidence)
        assertEquals("一百零五周年", p.unverified.single().evidence)
    }

    @Test
    fun missing_evidence_is_blank_not_error() {
        val raw = """
            {"one_liner":"x","key_points":[{"title":"t","detail":"d","status":"CLEAR"}],
             "assignments":[],"emphasis":[],"questions":[],"topics":[],"unverified":[]}
        """.trimIndent()
        val p = ReportParser.parse(raw, SessionType.LECTURE)
        assertEquals("", p.envelope.keyPoints.single().evidence)
    }
}
