package com.boguan.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranscriptMergeTest {

    @Test
    fun normazlie_drops_punctuation_and_spaces() {
        assertEquals("a一一在c中的余子式", TranscriptMerge.normalize("A 一一，在 C 中的余子式。"))
    }

    @Test
    fun similarity_identical_is_one() {
        assertEquals(1.0, TranscriptMerge.similarity("余子式", "余子式。"))
    }

    @Test
    fun similarity_drops_with_substitutions() {
        // 真机实测样例（ADR-0010）：实时稿正确，离线稿把「余子式」识别成「女子是」
        val realtime = "这个，那是不是正好就是 A 一一在 C 中的余子式"
        val offline = "这一那是不是最好就是 A 一一在 C 中的女子是"
        val s = TranscriptMerge.similarity(realtime, offline)
        assertTrue(s < TranscriptMerge.REVIEW_THRESHOLD, "similarity=$s")
        assertTrue(TranscriptMerge.needsReview(realtime, offline))
    }

    @Test
    fun needs_review_false_without_offline_text() {
        assertFalse(TranscriptMerge.needsReview("实时稿", ""))
    }

    @Test
    fun needs_review_true_when_texts_diverge() {
        assertTrue(TranscriptMerge.needsReview("这个是余子式", "这个是女子是"))
    }

    @Test
    fun needs_review_false_when_only_punctuation_differs() {
        assertFalse(TranscriptMerge.needsReview("我们看这个行列式。", "我们看这个行列式"))
    }

    @Test
    fun display_text_prefers_realtime_in_v01() {
        assertEquals("实时", TranscriptMerge.displayText("实时", "离线"))
        assertEquals("离线", TranscriptMerge.displayText("", "离线"))
    }

    @Test
    fun segment_exposes_display_text_and_review_flag() {
        val seg = Segment(
            sessionId = "s",
            startMs = 0,
            endMs = 1000,
            text = "这个是余子式",
            offlineText = "这个是女子是",
        )
        assertEquals("这个是余子式", seg.displayText)
        assertTrue(seg.needsReview)

        val clean = seg.copy(offlineText = "这个是余子式。")
        assertFalse(clean.needsReview)
    }
}
