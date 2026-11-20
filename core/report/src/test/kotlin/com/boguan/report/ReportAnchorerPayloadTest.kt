package com.boguan.report

import com.boguan.model.Anchor
import com.boguan.model.Emphasis
import com.boguan.model.KeyPoint
import com.boguan.model.ReportEnvelope
import com.boguan.model.ReportPayload
import com.boguan.model.Segment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportAnchorerPayloadTest {

    private fun seg(id: Long, start: Long, end: Long, text: String) = Segment(
        id = id,
        sessionId = "s",
        startMs = start,
        endMs = end,
        text = text,
        correctedText = text,
    )

    private val segments = listOf(
        seg(1, 0, 5_000, "长征不是直线也不是注定的，而是一条曲线"),
        seg(2, 5_000, 12_000, "建立文化自信要立足中华民族伟大实践"),
        seg(3, 12_000, 20_000, "坚持百花齐放百家争鸣发扬学术民主"),
    )

    @Test
    fun anchors_key_point_by_verbatim_evidence() {
        val payload = ReportPayload(
            envelope = ReportEnvelope(
                keyPoints = listOf(
                    KeyPoint(
                        title = "长征的意义",
                        detail = "长征是一条曲线",
                        evidence = "长征不是直线也不是注定的",
                    ),
                ),
            ),
        )
        val out = ReportAnchorer.anchor(payload, segments)
        assertEquals(Anchor(0, 5_000), out.envelope.keyPoints.single().anchor)
    }

    @Test
    fun anchors_emphasis_and_topics_from_evidence() {
        val payload = ReportPayload(
            envelope = ReportEnvelope(
                emphasis = listOf(
                    Emphasis(text = "文化自信", kind = Emphasis.Kind.KEY, evidence = "建立文化自信要立足中华民族伟大实践"),
                ),
            ),
        )
        val out = ReportAnchorer.anchor(payload, segments)
        assertEquals(Anchor(5_000, 12_000), out.envelope.emphasis.single().anchor)
        val rows = out.anchorRows()
        assertEquals(1, rows.size)
        assertEquals("老师强调", rows.single().section)
    }

    @Test
    fun leaves_anchor_null_when_evidence_missing_and_text_not_found() {
        val payload = ReportPayload(
            envelope = ReportEnvelope(
                keyPoints = listOf(KeyPoint(title = "编造的知识点", detail = "原文没提过")),
            ),
        )
        val out = ReportAnchorer.anchor(payload, segments)
        assertNull(out.envelope.keyPoints.single().anchor)
    }
}
