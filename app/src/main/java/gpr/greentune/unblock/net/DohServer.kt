package gpr.greentune.unblock.net

import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class DnsProvider(val id: String, val name: String, val url: String, val host: String, val ips: List<String>)

object DnsProviders {
    val all = listOf(
        DnsProvider("cloudflare", "Cloudflare", "https://cloudflare-dns.com/dns-query", "cloudflare-dns.com", listOf("1.1.1.1", "1.0.0.1")),
        DnsProvider("google", "Google", "https://dns.google/dns-query", "dns.google", listOf("8.8.8.8", "8.8.4.4")),
        DnsProvider("quad9", "Quad9", "https://dns.quad9.net/dns-query", "dns.quad9.net", listOf("9.9.9.9", "149.112.112.112")),
        DnsProvider("adguard", "AdGuard", "https://dns.adguard-dns.com/dns-query", "dns.adguard-dns.com", listOf("94.140.14.14", "94.140.15.15")),
    )

    fun byId(id: String): DnsProvider = all.firstOrNull { it.id == id } ?: all[0]
}

class DohServer(private val provider: DnsProvider) {
    private var socket: DatagramSocket? = null
    private var thread: Thread? = null
    private val pool = Executors.newFixedThreadPool(6)
    private val mime = "application/dns-message".toMediaType()

    private val client: OkHttpClient = Http.client.newBuilder()
        .proxy(Proxy.NO_PROXY)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                if (hostname == provider.host) provider.ips.map { InetAddress.getByName(it) } else Dns.SYSTEM.lookup(hostname)
        })
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    val port: Int get() = socket?.localPort ?: 0

    fun start(): Int {
        val s = DatagramSocket(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        socket = s
        thread = Thread({ loop(s) }, "gt-doh").apply {
            isDaemon = true
            start()
        }
        return s.localPort
    }

    private fun loop(s: DatagramSocket) {
        val buf = ByteArray(4096)
        while (!s.isClosed) {
            val p = DatagramPacket(buf, buf.size)
            try {
                s.receive(p)
            } catch (_: Exception) {
                break
            }
            val query = p.data.copyOfRange(p.offset, p.offset + p.length)
            val from = p.socketAddress
            try {
                pool.execute {
                    val answer = resolve(query)
                    if (answer != null) {
                        try {
                            s.send(DatagramPacket(answer, answer.size, from))
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun resolve(query: ByteArray): ByteArray? {
        if (query.size < 12) return null
        val id0 = query[0]
        val id1 = query[1]
        val q = query.copyOf()
        q[0] = 0
        q[1] = 0
        val viaDoh = try {
            val req = Request.Builder().url(provider.url)
                .header("Accept", "application/dns-message")
                .post(q.toRequestBody(mime))
                .build()
            client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
        } catch (_: Exception) {
            null
        }
        val answer = viaDoh ?: udpFallback(q) ?: return null
        if (answer.size < 2) return null
        answer[0] = id0
        answer[1] = id1
        return answer
    }

    private fun udpFallback(q: ByteArray): ByteArray? {
        for (ip in provider.ips) {
            try {
                DatagramSocket().use { ds ->
                    ds.soTimeout = 2500
                    ds.send(DatagramPacket(q, q.size, InetAddress.getByName(ip), 53))
                    val buf = ByteArray(4096)
                    val p = DatagramPacket(buf, buf.size)
                    ds.receive(p)
                    return buf.copyOf(p.length)
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun stop() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        pool.shutdownNow()
    }
}
