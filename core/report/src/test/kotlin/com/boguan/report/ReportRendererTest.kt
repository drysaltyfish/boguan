package com.boguan.report

import com.boguan.model.ActionItem
import com.boguan.model.Anchor
import com.boguan.model.Emphasis
import com.boguan.model.KeyPoint
import com.boguan.model.ReportEnvelope
import com.boguan.model.ReportPayload
import com.boguan.model.Session
import com.boguan.model.SessionType
import com.boguan.model.UnverifiedItem
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ReportRendererTest {

    private fun lecturePayload() = ReportPayload(
        envelope = ReportEnvelope(
            oneLiner = "本节课讲了现在完成时的三种用法。",
            keyPoints = listOf(
                KeyPoint(
                    title = "现在完成时",
                    detail = "have/has + 过去分词",
                    status = KeyPoint.ReviewStatus.NEEDS_REVIEW,
                    anchor = Anchor(60_000, 90_000),
                ),
            ),
            actionItems = listOf(ActionItem(text = "完成练习册 P32", deadline = "周五")),
            emphasis = listOf(Emphasis("这个考点期末必考", Emphasis.Kind.EXAM)),
        ),
        unverified = listOf(UnverifiedItem("1500 元", "金额置信度低", Anchor(120_000, 125_000))),
    )

    @Test
    fun `lecture report renders core sections and badges`() {
        val md = ReportRenderer.render(Session(type = SessionType.LECTURE), lecturePayload())

        assertContains(md, "## 一句话结论")
        assertContains(md, "## 核心知识点")
        assertContains(md, "（需复习）")
        assertContains(md, "## 作业与截止日期")
        assertContains(md, "截止：周五")
        assertContains(md, "## 老师强调")
        assertContains(md, "[考点]")
        assertContains(md, "## 待确认清单")
    }

    @Test
    fun `meeting report renders owner and deadline placeholders`() {
        val payload = ReportPayload(
            envelope = ReportEnvelope(
                oneLiner = "确定 Q2 上线范围。",
                actionItems = listOf(ActionItem(text = "补齐接口文档")),
                emphasis = listOf(Emphasis("延期上线", Emphasis.Kind.RISK)),
            ),
        )
        val md = ReportRenderer.render(Session(type = SessionType.MEETING), payload)

        assertContains(md, "## 决议与待办")
        assertContains(md, "负责人：待确认")
        assertContains(md, "截止：待确认")
        assertContains(md, "[风险]")
    }

    @Test
    fun `transcript appendix is opt-in`() {
        val without = ReportRenderer.render(Session(), ReportPayload())
        val with = ReportRenderer.render(Session(), ReportPayload(), transcript = "这是原文")

        assertTrue("转写全文附录" !in without)
        assertContains(with, "转写全文附录")
        assertContains(with, "这是原文")
    }
}
