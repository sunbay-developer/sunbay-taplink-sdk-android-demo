package com.sunmi.tapro.taplink.demo.service.cloud

import com.sunmi.tapro.taplink.demo.service.util.TLog
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * OkHttp-based HTTP client for Sunbay Cloud API.
 *
 * IMPORTANT: The Sunbay UAT API rejects requests with
 * "Content-Type: application/json; charset=utf-8" (returns C17 Bad Request).
 * OkHttp's default toRequestBody() automatically appends charset=utf-8.
 * This client uses a custom RequestBody + network interceptor to ensure
 * the Content-Type header is exactly "application/json" without charset.
 */
class CloudHttpClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val connectTimeoutMs: Long = 30_000L,
    private val readTimeoutMs: Long = 60_000L,
    private val maxRetries: Int = 3
) : AutoCloseable {
    companion object {
        private const val TAG = "CloudHttpClient"
        private const val RETRY_DELAY_BASE_MS = 1000L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addNetworkInterceptor(Interceptor { chain ->
            val original = chain.request()
            val ct = original.header("Content-Type")
            if (ct != null && ct.contains("charset", ignoreCase = true)) {
                chain.proceed(original.newBuilder().header("Content-Type", "application/json").build())
            } else {
                chain.proceed(original)
            }
        }).build()

    private fun jsonBody(json: String): RequestBody {
        val bytes = json.toByteArray(Charsets.UTF_8)
        return object : RequestBody() {
            override fun contentType(): MediaType = JSON_MEDIA_TYPE
            override fun contentLength(): Long = bytes.size.toLong()
            override fun writeTo(sink: BufferedSink) { sink.write(bytes) }
        }
    }
    fun post(path: String, body: ObjectNode): CloudResponse {
        return executeWithRetry(baseUrl + path, "POST", objectMapper.writeValueAsString(body), false)
    }

    fun get(path: String, params: ObjectNode): CloudResponse {
        val sb = StringBuilder(baseUrl + path)
        var first = true
        val fields = params.fieldNames()
        while (fields.hasNext()) {
            val key = fields.next()
            val v = params.get(key)
            if (v != null && !v.isNull) {
                sb.append(if (first) "?" else "&")
                sb.append(key).append("=").append(v.asText())
                first = false
            }
        }
        return executeWithRetry(sb.toString(), "GET", null, true)
    }
    private fun executeWithRetry(url: String, method: String, body: String?, retryable: Boolean): CloudResponse {
        var last: Exception? = null
        val n = if (retryable) maxRetries else 1
        for (i in 1..n) {
            try { return doExecute(url, method, body) }
            catch (e: CloudBusinessException) { throw e }
            catch (e: Exception) {
                last = e
                TLog.LogW(TAG, "Attempt " + i + "/" + n + " failed: " + e.message)
                if (i < n) Thread.sleep(RETRY_DELAY_BASE_MS * i)
            }
        }
        throw CloudNetworkException("Failed after " + n + " attempts: " + last?.message, last)
    }

    private fun doExecute(url: String, method: String, body: String?): CloudResponse {
        val startMs = System.currentTimeMillis()
        val rid = UUID.randomUUID().toString()
        val ts = startMs.toString()
        val mk = if (apiKey.length > 8) apiKey.take(4) + "****" + apiKey.takeLast(4) else "****"

        val b = Request.Builder().url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("X-Client-Request-Id", rid)
            .header("X-Timestamp", ts)
        if (method == "POST") b.post(jsonBody(body ?: "")) else b.get()
        val request = b.build()

        // --> REQUEST
        TLog.Log(TAG, "--> $method $url")
        TLog.Log(TAG, "Authorization: Bearer $mk")
        if (method == "POST") TLog.Log(TAG, "Content-Type: application/json")
        TLog.Log(TAG, "X-Client-Request-Id: $rid")
        TLog.Log(TAG, "X-Timestamp: $ts")
        if (body != null) {
            val bodyBytes = body.toByteArray(Charsets.UTF_8).size
            TLog.Log(TAG, "")
            TLog.Log(TAG, body)
            TLog.Log(TAG, "--> END $method (${bodyBytes}-byte body)")
        } else {
            TLog.Log(TAG, "--> END $method")
        }

        try {
            val resp = client.newCall(request).execute()
            val rb = resp.body?.string() ?: ""
            val elapsed = System.currentTimeMillis() - startMs

            // <-- RESPONSE
            TLog.Log(TAG, "<-- ${resp.code} $url (${elapsed}ms)")
            resp.headers.forEach { (name, value) -> TLog.Log(TAG, "$name: $value") }
            if (rb.isNotEmpty()) {
                TLog.Log(TAG, "")
                TLog.Log(TAG, rb)
            }
            TLog.Log(TAG, "<-- END HTTP (${rb.toByteArray(Charsets.UTF_8).size}-byte body)")

            if (!resp.isSuccessful) throw CloudNetworkException("HTTP ${resp.code}: $rb")
            return parseResponse(rb)
        } catch (e: CloudBusinessException) { throw e }
        catch (e: CloudNetworkException) { throw e }
        catch (e: SocketTimeoutException) { throw CloudNetworkException("Timeout: ${e.message}", e) }
        catch (e: IOException) { throw CloudNetworkException("Network error: ${e.message}", e) }
    }
    private fun parseResponse(body: String): CloudResponse {
        val j = objectMapper.readTree(body) as ObjectNode
        val code = j.get("code")?.asText() ?: "UNKNOWN"
        val msg = j.get("msg")?.asText() ?: ""
        val tid = j.get("traceId")?.asText()
        if (code != "0") {
            TLog.LogE(TAG, "API business error - code: " + code + ", msg: " + msg + ", traceId: " + tid)
            throw CloudBusinessException(code, msg, tid)
        }
        val data = if (j.has("data") && j.get("data").isObject) j.get("data") as ObjectNode else null
        return CloudResponse(code, msg, tid, data)
    }
    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        TLog.Log(TAG, "CloudHttpClient closed")
    }
}
data class CloudResponse(val code: String, val msg: String, val traceId: String?, val data: ObjectNode?)
class CloudBusinessException(val code: String, override val message: String, val traceId: String? = null) : Exception(message)
class CloudNetworkException(override val message: String, cause: Throwable? = null) : Exception(message, cause)
