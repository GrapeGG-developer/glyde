package gpr.greentune.unblock.core

import android.os.SystemClock
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.net.DnsProviders
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

object Prober {
    data class Result(val ok: Boolean, val ms: Long)

    const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(true)
            .followSslRedirects(true)
            .protocols(listOf(Protocol.HTTP_1_1))
            .proxy(Proxy.NO_PROXY)
            .build()
    }

    private val dnsCache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()

    private val doh: DnsOverHttps by lazy {
        val p = DnsProviders.byId(SettingsStore.value.dns)
        DnsOverHttps.Builder()
            .client(base.newBuilder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build())
            .url(p.url.toHttpUrl())
            .bootstrapDnsHosts(p.ips.map { InetAddress.getByName(it) })
            .includeIPv6(false)
            .resolvePrivateAddresses(false)
            .build()
    }

    fun resolveDoh(host: String): List<InetAddress>? = try {
        doh.lookup(host).filter { it.address.size == 4 }.ifEmpty { null }
    } catch (_: Exception) {
        null
    }

    private val resolver: Dns by lazy {
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val now = SystemClock.elapsedRealtime()
                dnsCache[hostname]?.let { if (now - it.first < 300_000) return it.second }
                val list = resolveDoh(hostname) ?: Dns.SYSTEM.lookup(hostname).filter { it.address.size == 4 }.ifEmpty { Dns.SYSTEM.lookup(hostname) }
                dnsCache[hostname] = now to list
                return list
            }
        }
    }

    private class SocksFactory(port: Int) : SocketFactory() {
        private val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
        override fun createSocket(): Socket = Socket(proxy)
        override fun createSocket(host: String, port: Int): Socket = Socket(proxy).apply { connect(InetSocketAddress(host, port)) }
        override fun createSocket(host: String, port: Int, localHost: InetAddress?, localPort: Int): Socket = createSocket(host, port)
        override fun createSocket(host: InetAddress, port: Int): Socket = Socket(proxy).apply { connect(InetSocketAddress(host, port)) }
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress?, localPort: Int): Socket = createSocket(address, port)
    }

    fun client(port: Int?, timeoutMs: Long): OkHttpClient {
        val b = base.newBuilder()
            .dns(resolver)
            .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        if (port != null) b.socketFactory(SocksFactory(port))
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
