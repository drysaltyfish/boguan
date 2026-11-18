package com.boguan.summarize

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class OpenAiCompatibleClientTest {

    private class FakeTransport(
        private val status: Int = 200,
        private val body: String = "",
    ) : HttpTransport {
        var lastUrl: String? = null
        var lastHeaders: Map<String, String> = emptyMap()
        var lastBody: String? = null
        override suspend fun post(url: String, headers: Map<String, String>, jsonBody: String): HttpResult {
            lastUrl = url
            lastHeaders = headers
            lastBody = jsonBody
            return HttpResult(status, body)
        }
    }

    private val endpoint = LlmEndpoint(
        baseUrl = "https://api.example.com/v1/",
        apiKey = "sk-test",
        model = "test-model",
    )

    @Test
    fun `request body carries model strict schema and both messages`() {
        val client = OpenAiCompatibleClient(FakeTransport())
        val body = client.buildRequestBody(
            "test-model",
            LlmRequest(
                purpose = LlmPurpose.MICRO_SUMMARY,
                systemPrompt = "SYS",
                userContent = "USR",
                jsonSchema = """{"type":"object","additionalProperties":false,"required":[],"properties":{}}""",
            ),
        )
        assertTrue(body.contains("\"model\":\"test-model\""))
        assertTrue(body.contains("\"strict\":true"))
        assertTrue(body.contains("\"name\":\"micro_summary\""))
        assertTrue(body.contains("\"role\":\"system\""))
        assertTrue(body.contains("\"role\":\"user\""))
    }

    @Test
    fun `successful response is parsed with usage`() = runBlocking {
        val transport = FakeTransport(
            body = """
            {"id":"req_1","choices":[{"message":{"content":"{\"title\":\"t\"}"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":100,"completion_tokens":50}}
            """.trimIndent(),
        )
        val client = OpenAiCompatibleClient(transport)
        val res = client.complete(endpoint, LlmRequest(LlmPurpose.QA, "s", "u"))

        assertEquals("{\"title\":\"t\"}", res.content)
        assertEquals(100, res.inputTokens)
        assertEquals(50, res.outputTokens)
        assertEquals("req_1", res.requestId)
        assertEquals("https://api.example.com/v1/chat/completions", transport.lastUrl)
        assertEquals("Bearer sk-test", transport.lastHeaders["Authorization"])
    }

    @Test
    fun `refusal is surfaced even when finish_reason is stop`(): Unit = runBlocking {
        val transport = FakeTransport(
            body = """{"choices":[{"message":{"content":null,"refusal":"cannot help"},"finish_reason":"stop"}]}""",
        )
        val client = OpenAiCompatibleClient(transport)
        assertFailsWith<LlmRefusalException> {
            client.complete(endpoint, LlmRequest(LlmPurpose.QA, "s", "u"))
        }
    }

    @Test
    fun `length finish reason is reported as truncation`(): Unit = runBlocking {
        val transport = FakeTransport(
            body = """{"choices":[{"message":{"content":"{\"a\":1"},"finish_reason":"length"}]}""",
        )
        val client = OpenAiCompatibleClient(transport)
        assertFailsWith<LlmTruncatedException> {
            client.complete(endpoint, LlmRequest(LlmPurpose.QA, "s", "u"))
        }
    }

    @Test
    fun `http error carries status code`() = runBlocking {
        val transport = FakeTransport(status = 429, body = "rate limited")
        val client = OpenAiCompatibleClient(transport)
        val ex = assertFailsWith<LlmHttpException> {
            client.complete(endpoint, LlmRequest(LlmPurpose.QA, "s", "u"))
        }
        assertEquals(429, ex.statusCode)
    }
}
