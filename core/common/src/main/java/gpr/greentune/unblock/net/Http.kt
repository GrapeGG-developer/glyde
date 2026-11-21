package gpr.greentune.unblock.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.Proxy
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val download: OkHttpClient by lazy {
        client.newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    val poll: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    fun pollText(url: String): String? = try {
        val b = Request.Builder().url(url).header("User-Agent", "Glyde-Android").build()
        poll.newCall(b).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    } catch (_: Exception) {
        null
    }

    private val json = "application/json; charset=utf-8".toMediaType()

    fun getText(url: String, headers: Map<String, String> = emptyMap()): String? = try {
        val b = Request.Builder().url(url).header("User-Agent", "Glyde-Android")
        headers.forEach { (k, v) -> b.header(k, v) }
        client.newCall(b.build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    } catch (_: Exception) {
        null
    }

    fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject? =
        getText(url, headers)?.let { t -> try { JSONObject(t) } catch (_: Exception) { null } }

    fun postJson(url: String, body: JSONObject): Boolean = try {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Glyde-Android")
            .post(body.toString().toRequestBody(json))
            .build()
        client.newCall(req).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }
}
