package com.boguan.store

import android.content.ContentValues
import com.boguan.model.Segment
import com.boguan.model.Session
import com.boguan.model.SessionState
import com.boguan.model.SessionType

/** 会话与转写的持久化入口。上层只依赖本类，便于将来替换实现。 */
class SessionRepository(private val db: BoguanDatabase) {

    fun createSession(session: Session) {
        db.writableDatabase.insertWithOnConflict(
            "session", null, session.toValues(), android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun updateState(sessionId: String, state: SessionState) {
        val values = ContentValues().apply { put("state", state.name) }
        db.writableDatabase.update("session", values, "id = ?", arrayOf(sessionId))
    }

    fun finishSession(sessionId: String, endedAtMs: Long, state: SessionState) {
        val values = ContentValues().apply {
            put("ended_at_ms", endedAtMs)
            put("state", state.name)
        }
        db.writableDatabase.update("session", values, "id = ?", arrayOf(sessionId))
    }

    fun insertSegment(segment: Segment) {
        val values = ContentValues().apply {
            put("session_id", segment.sessionId)
            put("start_ms", segment.startMs)
            put("end_ms", segment.endMs)
            put("text", segment.text)
            put("confidence", segment.confidence)
            put("is_final", if (segment.isFinal) 1 else 0)
            put("offline_text", segment.offlineText)
            put("corrected_text", segment.correctedText)
        }
        db.writableDatabase.insert("segment", null, values)
    }

    fun segmentsOf(sessionId: String): List<Segment> {
        val out = mutableListOf<Segment>()
        db.readableDatabase.rawQuery(
            "SELECT id, session_id, start_ms, end_ms, text, confidence, is_final, offline_text, corrected_text " +
                "FROM segment WHERE session_id = ? ORDER BY start_ms",
            arrayOf(sessionId),
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Segment(
                        id = c.getLong(0),
                        sessionId = c.getString(1),
                        startMs = c.getLong(2),
                        endMs = c.getLong(3),
                        text = c.getString(4),
                        confidence = c.getFloat(5),
                        isFinal = c.getInt(6) == 1,
                        offlineText = c.getString(7) ?: "",
                        correctedText = c.getString(8) ?: "",
                    ),
                )
            }
        }
        return out
    }

    /**
     * 终稿二遍：保存离线稿到独立列，**不覆盖**实时稿。
     * 展示文本的选择交给 [com.boguan.model.TranscriptMerge]（ADR-0010）。
     */
    fun updateSegmentOfflineText(segmentId: Long, offlineText: String) {
        val values = ContentValues().apply {
            put("offline_text", offlineText)
            put("is_final", 1)
        }
        db.writableDatabase.update("segment", values, "id = ?", arrayOf(segmentId.toString()))
    }

    /** 终稿 LLM 校正稿（ADR-0011）；不覆盖实时稿与离线稿，便于随时重跑。 */
    fun updateSegmentCorrectedText(segmentId: Long, correctedText: String) {
        val values = ContentValues().apply { put("corrected_text", correctedText) }
        db.writableDatabase.update("segment", values, "id = ?", arrayOf(segmentId.toString()))
    }

    /** 清空一场记录的所有校正稿（重跑终稿前调用）。 */
    fun clearCorrectedText(sessionId: String) {
        val values = ContentValues().apply { put("corrected_text", "") }
        db.writableDatabase.update("segment", values, "session_id = ?", arrayOf(sessionId))
    }

    /** 转写全文（供摘要/报告用），按展示策略选文本。 */
    fun transcribeAll(sessionId: String): String =
        segmentsOf(sessionId).joinToString(separator = "\n") { it.displayText }

    private fun Session.toValues(): ContentValues = ContentValues().apply {
        put("id", id)
        put("type", type.name)
        put("title", title)
        put("started_at_ms", startedAtMs)
        put("ended_at_ms", endedAtMs)
        put("state", state.name)
        put("audio_source", audioSource)
        put("audio_path", audioPath)
    }

    fun insertReport(sessionId: String, type: com.boguan.model.SessionType, payloadJson: String, renderedMd: String) {
        val values = ContentValues().apply {
            put("session_id", sessionId)
            put("generated_at_ms", System.currentTimeMillis())
            put("session_type", type.name)
            put("schema_version", 1)
            put("payload_json", payloadJson)
            put("rendered_md", renderedMd)
        }
        db.writableDatabase.insertWithOnConflict(
            "report", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun latestReport(sessionId: String): String? {
        db.readableDatabase.rawQuery(
            "SELECT rendered_md FROM report WHERE session_id = ? ORDER BY generated_at_ms DESC LIMIT 1",
            arrayOf(sessionId),
        ).use { c -> return if (c.moveToFirst()) c.getString(0) else null }
    }

    fun insertMicroSummary(summary: com.boguan.model.MicroSummary) {
        val values = ContentValues().apply {
            put("session_id", summary.sessionId)
            put("kind", summary.kind.name)
            put("start_ms", summary.startMs)
            put("end_ms", summary.endMs)
            put("payload_json", summary.payloadJson)
            put("degraded", if (summary.degraded) 1 else 0)
        }
        db.writableDatabase.insert("micro_summary", null, values)
        db.writableDatabase.execSQL(
            "DELETE FROM micro_summary WHERE session_id = ? AND id NOT IN " +
                "(SELECT id FROM micro_summary WHERE session_id = ? ORDER BY end_ms DESC LIMIT 50)",
            arrayOf(summary.sessionId, summary.sessionId),
        )
    }

    fun microSummariesOf(sessionId: String): List<com.boguan.model.MicroSummary> {
        val out = mutableListOf<com.boguan.model.MicroSummary>()
        db.readableDatabase.rawQuery(
            "SELECT id, session_id, kind, start_ms, end_ms, payload_json, degraded " +
                "FROM micro_summary WHERE session_id = ? ORDER BY end_ms",
            arrayOf(sessionId),
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    com.boguan.model.MicroSummary(
                        id = c.getLong(0),
                        sessionId = c.getString(1),
                        kind = runCatching { com.boguan.model.MicroSummary.Kind.valueOf(c.getString(2)) }
                            .getOrDefault(com.boguan.model.MicroSummary.Kind.MICRO),
                        startMs = c.getLong(3),
                        endMs = c.getLong(4),
                        payloadJson = c.getString(5),
                        degraded = c.getInt(6) == 1,
                    ),
                )
            }
        }
        return out
    }

    /** 历史场次（新的在前）。 */
    fun listSessions(limit: Int = 100): List<Session> {
        val out = mutableListOf<Session>()
        db.readableDatabase.rawQuery(
            "SELECT id, type, title, started_at_ms, ended_at_ms, state, audio_source, audio_path " +
                "FROM session ORDER BY started_at_ms DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val type = runCatching { SessionType.valueOf(c.getString(1)) }.getOrDefault(SessionType.LECTURE)
                val state = runCatching { SessionState.valueOf(c.getString(5)) }.getOrDefault(SessionState.DONE)
                out.add(
                    Session(
                        id = c.getString(0),
                        type = type,
                        title = c.getString(2) ?: "",
                        startedAtMs = c.getLong(3),
                        endedAtMs = if (c.isNull(4)) null else c.getLong(4),
                        state = state,
                        audioSource = c.getString(6) ?: "microphone",
                        audioPath = c.getString(7),
                    ),
                )
            }
        }
        return out
    }

    /** 读取一场记录的类型（终稿校正按类型选措辞）。 */
    fun typeOf(sessionId: String): SessionType? {
        db.readableDatabase.rawQuery(
            "SELECT type FROM session WHERE id = ?", arrayOf(sessionId),
        ).use { c ->
            if (!c.moveToFirst()) return null
            return runCatching { SessionType.valueOf(c.getString(0)) }.getOrDefault(SessionType.LECTURE)
        }
    }

    /** 读取一场记录的音频路径。 */
    fun audioPathOf(sessionId: String): String? {
        db.readableDatabase.rawQuery(
            "SELECT audio_path FROM session WHERE id = ?", arrayOf(sessionId),
        ).use { c -> return if (c.moveToFirst()) c.getString(0) else null }
    }

    companion object {
        fun typeLabels(): Map<SessionType, String> = mapOf(
            SessionType.LECTURE to "课堂",
            SessionType.MEETING to "会议",
        )
    }
}
