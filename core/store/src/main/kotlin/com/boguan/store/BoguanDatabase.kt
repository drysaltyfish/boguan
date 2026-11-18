package com.boguan.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 本地数据库（ADR-0005 六张表）。
 *
 * 实现说明（见 ADR-0009）：首版用 `SQLiteOpenHelper` 而非 Room ——
 * 避免为骨架引入 KSP 代码生成链路，迁移到 Room 的触发条件写在 ADR-0009。
 */
class BoguanDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DB_NAME, null, DB_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE session (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                started_at_ms INTEGER NOT NULL,
                ended_at_ms INTEGER,
                state TEXT NOT NULL,
                audio_source TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE segment (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                start_ms INTEGER NOT NULL,
                end_ms INTEGER NOT NULL,
                text TEXT NOT NULL,
                confidence REAL NOT NULL DEFAULT 0,
                is_final INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (session_id) REFERENCES session(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_segment_session ON segment(session_id, start_ms)")
        db.execSQL(
            """
            CREATE TABLE micro_summary (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                start_ms INTEGER NOT NULL,
                end_ms INTEGER NOT NULL,
                payload_json TEXT NOT NULL,
                degraded INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (session_id) REFERENCES session(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE qa_pair (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                asked_at_ms INTEGER NOT NULL,
                question TEXT NOT NULL,
                answer TEXT NOT NULL,
                anchor_start_ms INTEGER,
                anchor_end_ms INTEGER,
                FOREIGN KEY (session_id) REFERENCES session(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE report (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                generated_at_ms INTEGER NOT NULL,
                session_type TEXT NOT NULL,
                schema_version INTEGER NOT NULL,
                payload_json TEXT NOT NULL,
                rendered_md TEXT NOT NULL,
                FOREIGN KEY (session_id) REFERENCES session(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE llm_call (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT,
                purpose TEXT NOT NULL,
                request_json TEXT NOT NULL,
                response_json TEXT,
                request_id TEXT,
                input_tokens INTEGER NOT NULL DEFAULT 0,
                output_tokens INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL,
                created_at_ms INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v0.1 尚无线上数据；后续升级按迁移脚本处理
    }

    companion object {
        const val DB_NAME = "boguan.db"
        const val DB_VERSION = 1
    }
}
