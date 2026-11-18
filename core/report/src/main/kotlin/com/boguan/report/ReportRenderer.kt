package com.boguan.report

import com.boguan.model.Anchor
import com.boguan.model.ReportPayload
import com.boguan.model.Session
import com.boguan.model.SessionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 报告渲染器：把「公共信封 + 类型载荷」渲染成 Markdown（ADR-0004）。
 * 纯函数、无 IO、无 Android 依赖 —— 因此可以在桌面端复用与单测。
 */
object ReportRenderer {

    fun render(session: Session, payload: ReportPayload, transcript: String = ""): String {
        val sb = StringBuilder()
        sb.appendLine("# " + (session.title.ifBlank { defaultTitle(session) }))
        sb.appendLine()
        renderMeta(sb, session)
        sb.appendLine()
        if (payload.envelope.oneLiner.isNotBlank()) {
            sb.appendLine("## 一句话结论")
            sb.appendLine()
            sb.appendLine(payload.envelope.oneLiner)
            sb.appendLine()
        }
        when (session.type) {
            SessionType.LECTURE -> renderLecture(sb, payload)
            SessionType.MEETING -> renderMeeting(sb, payload)
        }
        renderUnverified(sb, payload)
        renderQa(sb, payload)
        if (transcript.isNotBlank()) {
            sb.appendLine("## 转写全文附录")
            sb.appendLine()
            sb.appendLine("<details><summary>展开原文</summary>")
            sb.appendLine()
            sb.appendLine(transcript)
            sb.appendLine()
            sb.appendLine("</details>")
        }
        return sb.toString()
    }

    private fun defaultTitle(session: Session): String =
        if (session.type == SessionType.LECTURE) "课堂记录" else "会议记录"

    private fun renderMeta(sb: StringBuilder, session: Session) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sb.appendLine("- 类型：" + if (session.type == SessionType.LECTURE) "课堂" else "会议")
        sb.appendLine("- 开始：" + fmt.format(Date(session.startedAtMs)))
        session.endedAtMs?.let { end ->
            val minutes = (end - session.startedAtMs) / 60000
            sb.appendLine("- 时长：" + minutes + " 分钟")
        }
        sb.appendLine("- 音频源：" + session.audioSource)
    }

    private fun renderLecture(sb: StringBuilder, payload: ReportPayload) {
        if (payload.envelope.emphasis.isNotEmpty()) {
            sb.appendLine("## 老师强调")
            sb.appendLine()
            payload.envelope.emphasis.forEach { e ->
                sb.appendLine("- [" + emphasisLabel(e.kind) + "] " + e.text + anchorSuffix(e.text, null))
            }
            sb.appendLine()
        }
        if (payload.envelope.keyPoints.isNotEmpty()) {
            sb.appendLine("## 核心知识点")
            sb.appendLine()
            payload.envelope.keyPoints.forEach { kp ->
                sb.appendLine("### " + kp.title + statusBadge(kp.status))
                sb.appendLine()
                sb.appendLine(kp.detail)
                sb.appendLine()
            }
        }
        if (payload.envelope.actionItems.isNotEmpty()) {
            sb.appendLine("## 作业与截止日期")
            sb.appendLine()
            payload.envelope.actionItems.forEach { a ->
                sb.appendLine("- " + a.text + (a.deadline?.let { "（截止：" + it + "）" } ?: ""))
            }
            sb.appendLine()
        }
        renderTopicsAndQuestions(sb, payload)
    }

    private fun renderMeeting(sb: StringBuilder, payload: ReportPayload) {
        if (payload.envelope.emphasis.isNotEmpty()) {
            sb.appendLine("## 关键决议与风险")
            sb.appendLine()
            payload.envelope.emphasis.forEach { e ->
                sb.appendLine("- [" + emphasisLabel(e.kind) + "] " + e.text)
            }
            sb.appendLine()
        }
        if (payload.envelope.actionItems.isNotEmpty()) {
            sb.appendLine("## 决议与待办")
            sb.appendLine()
            payload.envelope.actionItems.forEach { a ->
                val owner = a.owner ?: "待确认"
                val deadline = a.deadline ?: "待确认"
                sb.appendLine("- " + a.text + " — 负责人：" + owner + "，截止：" + deadline)
            }
            sb.appendLine()
        }
        renderTopicsAndQuestions(sb, payload)
    }

    private fun renderTopicsAndQuestions(sb: StringBuilder, payload: ReportPayload) {
        if (payload.envelope.topics.isNotEmpty()) {
            sb.appendLine("## 分主题要点")
            sb.appendLine()
            payload.envelope.topics.forEach { t ->
                sb.appendLine("### " + t.title + "（" + fmtMs(t.startMs) + " – " + fmtMs(t.endMs) + "）")
                sb.appendLine()
            }
        }
        if (payload.envelope.questions.isNotEmpty()) {
            sb.appendLine("## 未决问题与问答")
            sb.appendLine()
            payload.envelope.questions.forEach { q ->
                sb.appendLine("- " + q.text + (q.answer?.let { " → " + it } ?: "（未解答）"))
            }
            sb.appendLine()
        }
    }

    private fun renderUnverified(sb: StringBuilder, payload: ReportPayload) {
        if (payload.unverified.isEmpty()) return
        sb.appendLine("## 待确认清单")
        sb.appendLine()
        sb.appendLine("> 以下内容转写置信度较低，请回听核对后再采信。")
        sb.appendLine()
        payload.unverified.forEach { u ->
            val anchor = u.anchor?.let { "（[" + fmtMs(it.startMs) + "]）" } ?: ""
            sb.appendLine("- " + u.text + " — " + u.reason + anchor)
        }
        sb.appendLine()
    }

    private fun renderQa(sb: StringBuilder, payload: ReportPayload) {
        if (payload.qaPairs.isEmpty()) return
        sb.appendLine("## 会中问答记录")
        sb.appendLine()
        payload.qaPairs.forEach { q ->
            sb.appendLine("- **问：** " + q.question)
            sb.appendLine("  **答：** " + q.answer)
        }
        sb.appendLine()
    }

    private fun emphasisLabel(kind: com.boguan.model.Emphasis.Kind): String = when (kind) {
        com.boguan.model.Emphasis.Kind.EXAM -> "考点"
        com.boguan.model.Emphasis.Kind.KEY -> "重点"
        com.boguan.model.Emphasis.Kind.PITFALL -> "易错"
        com.boguan.model.Emphasis.Kind.DECISION -> "决议"
        com.boguan.model.Emphasis.Kind.RISK -> "风险"
    }

    private fun statusBadge(status: com.boguan.model.KeyPoint.ReviewStatus): String = when (status) {
        com.boguan.model.KeyPoint.ReviewStatus.CLEAR -> ""
        com.boguan.model.KeyPoint.ReviewStatus.NEEDS_REVIEW -> "（需复习）"
        com.boguan.model.KeyPoint.ReviewStatus.UNSURE -> "（有疑问）"
    }

    private fun anchorSuffix(text: String, anchor: Anchor?): String =
        anchor?.let { "（[" + fmtMs(it.startMs) + "]）" } ?: ""

    private fun fmtMs(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }
}
