package com.boguan.summarize

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OkHttpTransport(
    connectTimeoutSec: Long = 5,
    readTimeoutSec: Long = 30,
) : HttpTransport {

    private val client = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun post(url: String, headers: Map<String, String>, jsonBody: String): HttpResult {
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(jsonMedia))
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        client.newCall(request).execute().use { response ->
            return HttpResult(response.code, response.body?.string().orEmpty())
        }
    }
}
