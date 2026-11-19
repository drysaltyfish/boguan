package com.boguan.model

/**
 * 实时稿与离线二遍稿的合并策略（ADR-0010）。
 *
 * 背景：真机实测发现 SenseVoice-Small int8 在中文专业课的远场录音上并不优于
 * 实时流式模型（例：余子式 → 女子是 / 鱼子式），且不输出标点。因此 v0.1
 * **不**用二遍稿直接覆盖实时稿，而是：
 *  1. 两份文本都留在库里（segment.text 实时稿 / segment.offline_text 二遍稿）；
 *  2. 展示文本由本类 [displayText] 单点决定，将来换到更好的离线模型时只改这里；
 *  3. 两稿分歧大的段落标记「待确认」，作为回听核对与报告（ADR-0004）的输入。
 */
object TranscriptMerge {

    /**
     * 相似度低于它就认为两稿分歧到需要人工确认。
     *
     * 标定依据（ADR-0010，17 段真实课堂录音）：标点/大小写差异归一化后相似度 ≈1.0，
     * 而术语被识别错（例：余子式 → 女子是）会掉到 0.80 以下；取 0.85 相当于
     * 「约 15% 以上的字不一致」，能把换词级别的错误挑出来而不淹没标点差异。
     */
    const val REVIEW_THRESHOLD = 0.85

    /**
     * 展示用文本的优先级：LLM 校正稿 > 实时稿 > 离线稿（ADR-0010 / ADR-0011）。
     *
     * 校正稿是唯一被验证为「显著更好」的一稿（16 段真实课堂录音上修掉
     * 余子式/公式/变量名等同音错误），因此优先展示它；没有校正稿时
     * 仍用实时稿，离线稿只在实时稿为空时兜底。
     */
    fun displayText(realtime: String, offline: String, corrected: String = ""): String = when {
        corrected.isNotBlank() -> corrected
        realtime.isNotBlank() -> realtime
        else -> offline
    }

    /**
     * 两稿是否分歧到需要回听确认。
     * 离线稿为空（还没跑二遍）不算待确认。
     */
    fun needsReview(
        realtime: String,
        offline: String,
        threshold: Double = REVIEW_THRESHOLD,
    ): Boolean {
        if (offline.isBlank()) return false
        if (normalize(realtime).isEmpty() || normalize(offline).isEmpty()) return false
        return similarity(realtime, offline) < threshold
    }

    /** 归一化：去掉空白与标点，只留下文字和数字，忽略全半角与大小写差异。 */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            if (ch.isLetterOrDigit()) sb.append(ch.lowercaseChar())
        }
        return sb.toString()
    }

    /** 字级相似度 0..1 = 1 - 编辑距离 / 较长串长度。 */
    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() && y.isEmpty()) return 1.0
        if (x.isEmpty() || y.isEmpty()) return 0.0
        val longer = maxOf(x.length, y.length)
        return 1.0 - levenshtein(x, y).toDouble() / longer
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            val ca = a[i - 1]
            for (j in 1..b.length) {
                val cost = if (ca == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
