package com.boguan.report

import com.boguan.model.Anchor
import com.boguan.model.ReportPayload
import com.boguan.model.Segment
import com.boguan.model.UnverifiedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportAnchorerTest {

    private fun seg(id: Long, start: Long, end: Long, text: String) = Segment(
        id = id,
        sessionId = "s",
        startMs = start,
        endMs = end,
        text = text,
    )

    private val segments = listOf(
        seg(1, 0, 5_000, "这节课的作业是第三章习题，下周五交"),
        seg(2, 5_000, 10_000, "参考书是《线性代数》第二版，作者李永乐"),
        seg(3, 10_000, 15_000, "考试时间大概是 1500 秒这种说法"),
    )

    @Test
    fun anchors_item_by_exact_phrase() {
        val payload = ReportPayload(unverified = listOf(UnverifiedItem("下周五交", "日期可疑")))
        val out = ReportAnchorer.anchor(payload, segments)
        assertEquals(Anchor(0, 5_000), out.unverified.single().anchor)
    }

    @Test
    fun anchors_item_that_spans_punctuation() {
        val payload = ReportPayload(unverified = listOf(UnverifiedItem("李永乐", "人名")))
        assertEquals(Anchor(5_000, 10_000), ReportAnchorer.anchor(payload, segments).unverified.single().anchor)
    }

    @Test
    fun leaves_anchor_null_when_not_found() {
        val payload = ReportPayload(unverified = listOf(UnverifiedItem("王小明", "人名")))
        assertNull(ReportAnchorer.anchor(payload, segments).unverified.single().anchor)
    }

    @Test
    fun keeps_preexisting_anchor() {
        val preset = Anchor(1, 2)
        val payload = ReportPayload(unverified = listOf(UnverifiedItem("下周五交", "日期", preset)))
        assertEquals(preset, ReportAnchorer.anchor(payload, segments).unverified.single().anchor)
    }
}
