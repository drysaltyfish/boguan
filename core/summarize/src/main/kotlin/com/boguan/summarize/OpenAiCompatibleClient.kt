package com.boguan.summarize

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * OpenAI 兼容客户端（ADR-0003）。
 *
 * - 模型名不硬编码：由 [LlmEndpoint.model] 决定；
 * - 支持严格 JSON Schema（`response_format.json_schema.strict = true`）；
 * - 显式处理拒答（`refusal`）与截断（`finish_reason == "length"`）——
 *   这两类都不该被当成"正常空结果"。
 */
class OpenAiCompatibleClient(
    private val transport: HttpTransport = OkHttpTransport(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {

    /**
     * 先按严格 JSON Schema 调用；若供应商不支持（如 DeepSeek 返回 400
     * "This response_format type is unavailable now"），自动降级为 json_object
     * 模式并把 Schema 内联进 prompt（ADR-0003 的 L2 降级）。
     */
    suspend fun complete(endpoint: LlmEndpoint, request: LlmRequest): LlmResponse {
        return try {
            call(endpoint, request, strictSchema = true)
        } catch (e: LlmHttpException) {
            val degradable = request.jsonSchema != null &&
                e.statusCode == 400 &&
                looksLikeSchemaUnsupported(e.snippet)
            if (!degradable) throw e
            call(endpoint, request, strictSchema = false)
        }
    }

    private fun looksLikeSchemaUnsupported(snippet: String): Boolean {
        val s = snippet.lowercase()
        return s.contains("response_format") || s.contains("json_schema") || s.contains("unavailable")
    }

    private suspend fun call(
        endpoint: LlmEndpoint,
        request: LlmRequest,
        strictSchema: Boolean,
    ): LlmResponse {
        val body = buildRequestBody(endpoint.model, request, strictSchema)
        val url = endpoint.baseUrl.trimEnd('/') + "/chat/completions"
        val result = transport.post(
            url = url,
            headers = mapOf(
                "Authorization" to "Bearer " + endpoint.apiKey,
                "Content-Type" to "application/json",
            ),
            jsonBody = body,
        )
        if (result.statusCode !in 200..299) {
            throw LlmHttpException(result.statusCode, result.body.take(500))
        }
        val root = json.parseToJsonElement(result.body).jsonObject
        val choice = root["choices"]?.let { it as? kotlinx.serialization.json.JsonArray }?.firstOrNull()?.jsonObject
            ?: throw LlmProtocolException("missing choices")
        val message = choice["message"]?.jsonObject ?: throw LlmProtocolException("missing message")
        val refusal = message["refusal"]?.jsonPrimitive?.contentOrNullSafe()
        if (!refusal.isNullOrBlank()) {
            throw LlmRefusalException(refusal)
        }
        if (choice["finish_reason"]?.jsonPrimitive?.contentOrNullSafe() == "length") {
            throw LlmTruncatedException()
        }
        val content = message["content"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
        val usage = root["usage"]?.jsonObject
        val requestId = root["id"]?.jsonPrimitive?.contentOrNullSafe()
        return LlmResponse(
            content = content,
            requestId = requestId,
            inputTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.contentOrNullSafe()?.toIntOrNull() ?: 0,
            outputTokens = usage?.get("completion_tokens")?.jsonPrimitive?.contentOrNullSafe()?.toIntOrNull() ?: 0,
        )
    }

    internal fun buildRequestBody(model: String, request: LlmRequest, strictSchema: Boolean = true): String {
        val systemPrompt = if (!strictSchema && request.jsonSchema != null) {
            request.systemPrompt + "\n\n必须只输出一个 JSON 对象，且严格符合以下 JSON Schema：\n" + request.jsonSchema
        } else {
            request.systemPrompt
        }
        val obj: JsonObject = buildJsonObject {
            put("model", model)
            put("temperature", request.temperature)
            put("max_completion_tokens", request.maxOutputTokens)
            if (request.jsonSchema != null) {
                if (strictSchema) {
                    putJsonObject("response_format") {
                        put("type", "json_schema")
                        putJsonObject("json_schema") {
                            put("name", schemaName(request.purpose))
                            put("strict", true)
                            put("schema", Json.parseToJsonElement(request.jsonSchema))
                        }
                    }
                } else {
                    putJsonObject("response_format") { put("type", "json_object") }
                }
            }
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", request.userContent)
                })
            })
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    private fun schemaName(purpose: LlmPurpose): String = when (purpose) {
        LlmPurpose.MICRO_SUMMARY -> "micro_summary"
        LlmPurpose.CONSOLIDATION -> "consolidated_summary"
        LlmPurpose.TRANSCRIPT_CORRECTION -> "transcript_correction"
        LlmPurpose.REPORT_EXTRACTION -> "report_extraction"
        LlmPurpose.QA -> "qa_answer"
    }
}

open class LlmException(message: String) : Exception(message)
class LlmHttpException(val statusCode: Int, val snippet: String) :
    LlmException("HTTP " + statusCode + ": " + snippet)
class LlmProtocolException(message: String) : LlmException(message)
class LlmRefusalException(val refusal: String) : LlmException("refused: " + refusal.take(200))
class LlmTruncatedException : LlmException("truncated by max tokens")

internal fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content
