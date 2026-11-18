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
        }
        db.writableDatabase.insert("segment", null, values)
    }

    fun segmentsOf(sessionId: String): List<Segment> {
        val out = mutableListOf<Segment>()
        db.readableDatabase.rawQuery(
            "SELECT id, session_id, start_ms, end_ms, text, confidence, is_final " +
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
                    ),
                )
            }
        }
        return out
    }

    fun transcribeAll(sessionId: String): String =
        segmentsOf(sessionId).joinToString(separator = "\n") { it.text }

    private fun Session.toValues(): ContentValues = ContentValues().apply {
        put("id", id)
        put("type", type.name)
        put("title", title)
        put("started_at_ms", startedAtMs)
        put("ended_at_ms", endedAtMs)
        put("state", state.name)
        put("audio_source", audioSource)
    }

    companion object {
        fun typeLabels(): Map<SessionType, String> = mapOf(
            SessionType.LECTURE to "课堂",
            SessionType.MEETING to "会议",
        )
    }
}
