package com.boguan.app.settings

import android.content.Context
import com.boguan.summarize.LlmEndpoint

/**
 * 本地设置。仅本机保存，不出设备。
 *
 * TODO(安全)：API key 目前存 SharedPreferences（明文）。
 * ADR-0006 要求存 Android Keystore；接入 Keystore 加密后再发布。
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("boguan_settings", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(v) = prefs.edit().putString(KEY_BASE_URL, v.trim()).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(v) = prefs.edit().putString(KEY_API_KEY, v.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = prefs.edit().putString(KEY_MODEL, v.trim()).apply()

    /** 摘要间隔（毫秒）。默认 2 分钟，ADR-0003。 */
    var summaryIntervalMs: Long
        get() = prefs.getLong(KEY_INTERVAL_MS, DEFAULT_INTERVAL_MS)
        set(v) = prefs.edit().putLong(KEY_INTERVAL_MS, v).apply()

    /**
     * 实时模型档位（chunk 毫秒）：1920 = 更准（默认），480 = 更低延迟。
     * 实测（ADR-0012）：1920ms 对校正稿的 CER 0.106，480ms 为 0.159。
     * 改动后需重启应用（引擎在启动时加载）。
     */
    var realtimeChunkMs: Int
        get() = prefs.getInt(KEY_REALTIME_CHUNK_MS, DEFAULT_REALTIME_CHUNK_MS)
        set(v) = prefs.edit().putInt(KEY_REALTIME_CHUNK_MS, v).apply()

    /** 本课/本会术语表（每行或逗号分隔），喂给终稿 LLM 校正（ADR-0011）。 */
    var glossary: String
        get() = prefs.getString(KEY_GLOSSARY, "") ?: ""
        set(v) = prefs.edit().putString(KEY_GLOSSARY, v.trim()).apply()

    /** 终稿是否用 LLM 校正。默认开：实测能把「女子是」修成「余子式」（ADR-0011）。 */
    var correctionEnabled: Boolean
        get() = prefs.getBoolean(KEY_CORRECTION_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_CORRECTION_ENABLED, v).apply()

    /** 结构整理间隔（毫秒）。默认 10 分钟，ADR-0003。 */
    var consolidationIntervalMs: Long
        get() = prefs.getLong(KEY_CONSOLIDATION_INTERVAL_MS, DEFAULT_CONSOLIDATION_INTERVAL_MS)
        set(v) = prefs.edit().putLong(KEY_CONSOLIDATION_INTERVAL_MS, v).apply()

    /** 配置完整才返回 endpoint；否则返回 null，调度器会跳过摘要。 */
    fun endpointOrNull(): LlmEndpoint? {
        if (apiKey.isBlank()) return null
        if (baseUrl.isBlank()) return null
        val normalized = if (baseUrl.endsWith("/chat/completions")) baseUrl.removeSuffix("/chat/completions") else baseUrl
        return LlmEndpoint(baseUrl = normalized, apiKey = apiKey, model = model.ifBlank { DEFAULT_MODEL })
    }

    fun save(
        baseUrl: String,
        apiKey: String,
        model: String,
        glossary: String = this.glossary,
        correctionEnabled: Boolean = this.correctionEnabled,
    ) {
        this.baseUrl = baseUrl
        this.apiKey = apiKey
        this.model = model
        this.glossary = glossary
        this.correctionEnabled = correctionEnabled
    }

    companion object {
        private const val KEY_BASE_URL = "llm_base_url"
        private const val KEY_API_KEY = "llm_api_key"
        private const val KEY_MODEL = "llm_model"
        private const val KEY_INTERVAL_MS = "summary_interval_ms"
        private const val KEY_CONSOLIDATION_INTERVAL_MS = "consolidation_interval_ms"
        private const val KEY_REALTIME_CHUNK_MS = "realtime_chunk_ms"
        private const val KEY_GLOSSARY = "glossary_terms"
        private const val KEY_CORRECTION_ENABLED = "llm_correction_enabled"
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val DEFAULT_INTERVAL_MS = 120_000L
        const val DEFAULT_CONSOLIDATION_INTERVAL_MS = 600_000L
        const val DEFAULT_REALTIME_CHUNK_MS = 1920
    }
}
