package gpr.greentune.unblock.core

import android.os.SystemClock
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object Prober {
    data class Result(val ok: Boolean, val ms: Long)

    const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(true)
            .followSslRedirects(true)
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }

    fun client(port: Int?, timeoutMs: Long): OkHttpClient {
        val b = base.newBuilder()
            .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        if (port != null) {
            b.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
        } else {
            b.proxy(Proxy.NO_PROXY)
        }
        return b.build()
    }

    fun probe(client: OkHttpClient, url: String, limit: Long = 160L * 1024): Result {
        val t0 = SystemClock.elapsedRealtime()
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,image/*,*/*;q=0.8")
                .header("Accept-Encoding", "identity")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body
                var total = 0L
                var declared = -1L
                if (body != null) {
                    declared = body.contentLength()
                    val src = body.byteStream()
                    val buf = ByteArray(16384)
                    while (total < limit) {
                        val n = src.read(buf)
                        if (n < 0) break
                        total += n
                    }
                }
                val complete = declared <= 0 || total >= minOf(declared, limit)
                Result(complete && resp.code < 500, SystemClock.elapsedRealtime() - t0)
            }
        } catch (_: Exception) {
            Result(false, SystemClock.elapsedRealtime() - t0)
        }
    }
}
