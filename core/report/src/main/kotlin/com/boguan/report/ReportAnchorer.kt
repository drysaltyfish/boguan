package com.boguan.report

import com.boguan.model.Anchor
import com.boguan.model.ReportEnvelope
import com.boguan.model.ReportPayload
import com.boguan.model.Segment
import com.boguan.model.Topic
import com.boguan.model.UnverifiedItem

/**
 * 给报告条目补时间锚点（ADR-0017）。
 *
 * LLM 只能给出文字，给不出「这是第几分钟」。因此报告 schema 要求每条可回听的
 * 内容附带 [evidence]（从转写里逐字复制的 6–20 字片段），这里用它反查段落，
 * 得到可回听的时间段。
 *
 * 找不到就留空——宁可没有锚点，也不要瞎指时间。
 */
object ReportAnchorer {

    fun anchor(payload: ReportPayload, segments: List<Segment>): ReportPayload {
        if (segments.isEmpty()) return payload
        val env = payload.envelope
        return payload.copy(
            envelope = ReportEnvelope(
                oneLiner = env.oneLiner,
                topics = env.topics.map { t ->
                    if (t.startMs != 0L || t.endMs != 0L) t
                    else lookup(t.evidence.ifBlank { t.title }, segments)
                        ?.let { Topic(t.title, it.startMs, it.endMs, t.evidence) }
                        ?: t
                },
                actionItems = env.actionItems.map { a ->
                    if (a.anchor != null) a
                    else a.copy(anchor = lookup(a.evidence.ifBlank { a.text }, segments))
                },
                questions = env.questions.map { q ->
                    if (q.anchor != null) q
                    else q.copy(anchor = lookup(q.evidence.ifBlank { q.text }, segments))
                },
                emphasis = env.emphasis.map { e ->
                    if (e.anchor != null) e
                    else e.copy(anchor = lookup(e.evidence.ifBlank { e.text }, segments))
                },
                keyPoints = env.keyPoints.map { k ->
                    if (k.anchor != null) k
                    else k.copy(anchor = lookup(k.evidence.ifBlank { k.title }, segments))
                },
            ),
            unverified = payload.unverified.map { u ->
                if (u.anchor != null) u
                else u.copy(anchor = lookup(u.evidence.ifBlank { u.text }, segments, probes = true))
            },
        )
    }

    /** 逐字片段优先；找不到再退一步用数字/英文片段（人名常带这些）。 */
    private fun lookup(text: String, segments: List<Segment>, probes: Boolean = false): Anchor? {
        val needle = normalize(text)
        if (needle.isEmpty()) return null
        segments.firstOrNull { normalize(it.displayText).contains(needle) }?.let {
            return Anchor(it.startMs, it.endMs)
        }
        if (!probes) return null
        for (key in probeKeys(needle)) {
            segments.firstOrNull { normalize(it.displayText).contains(key) }?.let {
                return Anchor(it.startMs, it.endMs)
            }
        }
        return null
    }

    /** 从文本里抽出较有辨识度的片段：连续数字、连续字母（长度 ≥2）。 */
    private fun probeKeys(needle: String): List<String> {
        val out = mutableListOf<String>()
        var current = StringBuilder()
        for (ch in needle) {
            if (ch.isDigit() || ch in 'a'..'z') {
                current.append(ch)
            } else {
                if (current.length >= 2) out += current.toString()
                current = StringBuilder()
            }
        }
        if (current.length >= 2) out += current.toString()
        return out.distinct().sortedByDescending { it.length }
    }

    private fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) if (ch.isLetterOrDigit()) sb.append(ch.lowercaseChar())
        return sb.toString()
    }
}

/** 报告里所有可回听条目（UI 用）：标题 + 文本 + 锚点。 */
data class ReportAnchorRow(
    val section: String,
    val text: String,
    val anchor: Anchor?,
)

fun ReportPayload.anchorRows(): List<ReportAnchorRow> = buildList {
    envelope.keyPoints.forEach {
        add(ReportAnchorRow("核心知识点", it.title, it.anchor))
    }
    envelope.emphasis.forEach {
        add(ReportAnchorRow("老师强调", it.text, it.anchor))
    }
    envelope.actionItems.forEach {
        add(ReportAnchorRow("作业与待办", it.text, it.anchor))
    }
    envelope.questions.forEach {
        add(ReportAnchorRow("问题与问答", it.text, it.anchor))
    }
    envelope.topics.forEach {
        if (it.startMs != 0L || it.endMs != 0L) {
            add(ReportAnchorRow("分主题", it.title, Anchor(it.startMs, it.endMs)))
        }
    }
    unverified.forEach {
        add(ReportAnchorRow("待确认", it.text, it.anchor))
    }
}
