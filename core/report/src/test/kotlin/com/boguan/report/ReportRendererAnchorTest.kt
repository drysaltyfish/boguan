package com.boguan.report

import com.boguan.model.Anchor
import com.boguan.model.KeyPoint
import com.boguan.model.ReportEnvelope
import com.boguan.model.ReportPayload
import com.boguan.model.Session
import com.boguan.model.SessionType
import kotlin.test.Test
import kotlin.test.assertTrue

class ReportRendererAnchorTest {

    @Test
    fun renders_time_anchor_for_key_point() {
        val payload = ReportPayload(
            envelope = ReportEnvelope(
                keyPoints = listOf(
                    KeyPoint(title = "长征", detail = "一条曲线", anchor = Anchor(65_000, 70_000)),
                ),
            ),
        )
        val session = Session(type = SessionType.LECTURE, title = "t")
        val md = ReportRenderer.render(session, payload)
        assertTrue(md.contains("[01:05]"), md)
    }
}
