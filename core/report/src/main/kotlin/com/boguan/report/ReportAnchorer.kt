package com.boguan.report

import com.boguan.model.Anchor
import com.boguan.model.ReportPayload
import com.boguan.model.Segment
import com.boguan.model.UnverifiedItem

/**
 * 给「待确认清单」条目补时间锚点（ADR-0004）。
 *
 * LLM 只能给出可疑文本片段，不能给出时间；这里用它在转写里的出现位置反查段落，
 * 得到可回听的时间段。找不到就留空——宁可没有锚点，也不要瞎指时间。
 */
object ReportAnchorer {

    fun anchor(payload: ReportPayload, segments: List<Segment>): ReportPayload {
        if (payload.unverified.isEmpty() || segments.isEmpty()) return payload
        val anchored = payload.unverified.map { item ->
            if (item.anchor != null) item else item.copy(anchor = findAnchor(item.text, segments))
        }
        return payload.copy(unverified = anchored)
    }

    private fun findAnchor(text: String, segments: List<Segment>): Anchor? {
        val needle = normalize(text)
        if (needle.isEmpty()) return null
        segments.firstOrNull { normalize(it.displayText).contains(needle) }?.let {
            return Anchor(it.startMs, it.endMs)
        }
        // 整段没命中时退一步：用数字/英文片段单独找（人名往往带数字或缩写）
        for (key in probes(needle)) {
            segments.firstOrNull { normalize(it.displayText).contains(key) }?.let {
                return Anchor(it.startMs, it.endMs)
            }
        }
        return null
    }

    /** 从可疑文本里抽出较有辨识度的片段：连续数字、连续字母（长度 ≥2）。 */
    private fun probes(needle: String): List<String> {
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
