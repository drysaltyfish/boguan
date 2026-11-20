package com.boguan.model

/**
 * 报告 = 公共信封 + 类型载荷（ADR-0004）。
 * 报告是「投影」：只从结构化数据渲染，不做就地编辑。
 *
 * 每条可回听的内容都带 [evidence]：从转写里**逐字复制**的短片段，
 * 由 [com.boguan.report.ReportAnchorer] 反查出时间锚点（ADR-0017）。
 */

/** 公共信封：与类型无关。 */
data class ReportEnvelope(
    val oneLiner: String = "",
    val topics: List<Topic> = emptyList(),
    val actionItems: List<ActionItem> = emptyList(),
    val questions: List<Question> = emptyList(),
    val emphasis: List<Emphasis> = emptyList(),
    val keyPoints: List<KeyPoint> = emptyList(),
)

data class Topic(
    val title: String,
    val startMs: Long = 0,
    val endMs: Long = 0,
    val evidence: String = "",
)

/** 时间锚点：指向原文时间段，用于回听。 */
data class Anchor(val startMs: Long, val endMs: Long)

data class ActionItem(
    val text: String,
    val owner: String? = null,
    val deadline: String? = null,
    val anchor: Anchor? = null,
    val evidence: String = "",
)

data class Question(
    val text: String,
    val answer: String? = null,
    val anchor: Anchor? = null,
    val evidence: String = "",
)

data class Emphasis(
    val text: String,
    val kind: Kind,
    val anchor: Anchor? = null,
    val evidence: String = "",
) {
    enum class Kind { EXAM, KEY, PITFALL, DECISION, RISK }
}

/** 课堂知识点 / 会议讨论要点。 */
data class KeyPoint(
    val title: String,
    val detail: String,
    val status: ReviewStatus = ReviewStatus.CLEAR,
    val anchor: Anchor? = null,
    val lowConfidence: Boolean = false,
    val evidence: String = "",
) {
    enum class ReviewStatus { CLEAR, NEEDS_REVIEW, UNSURE }
}

/** 低置信度实体 -> 待确认清单。 */
data class UnverifiedItem(
    val text: String,
    val reason: String,
    val anchor: Anchor? = null,
    val evidence: String = "",
)

data class ReportPayload(
    val envelope: ReportEnvelope = ReportEnvelope(),
    val unverified: List<UnverifiedItem> = emptyList(),
    val qaPairs: List<QaPair> = emptyList(),
)
