package gpr.greentune.unblock.tg

import android.content.Context
import gpr.greentune.unblock.core.Logs
import gpr.greentune.unblock.net.Http
import gpr.greentune.unblock.net.Live
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

data class TgStats(val running: Boolean = false, val port: Int = 0, val active: Int = 0, val total: Int = 0, val ws: Int = 0, val tcp: Int = 0, val bytes: Long = 0)

object TgBridge {
    const val DEFAULT_PORT = 1443
    private const val HANDSHAKE = 64
    private val WS_IP = "149.154.167.220"
    private val DC_IPS = mapOf(1 to "149.154.175.50", 2 to "149.154.167.51", 3 to "149.154.175.100", 4 to "149.154.167.91", 5 to "149.154.171.5", 203 to "91.105.192.100")
    private val TAGS = setOf(0xEFEFEFEF.toInt(), 0xEEEEEEEE.toInt(), 0xDDDDDDDD.toInt())

    private val _stats = MutableStateFlow(TgStats())
    val stats: StateFlow<TgStats> get() = _stats
    private var server: ServerSocket? = null
    private val active = AtomicInteger()
    private val total = AtomicInteger()
    private val wsCount = AtomicInteger()
    private val tcpCount = AtomicInteger()
    private val bytes = AtomicLong()
    private val rnd = SecureRandom()
    @Volatile
    private var secret: ByteArray = ByteArray(16)
    @Volatile
    private var wsBroken = 0L
    private var appContext: Context? = null

    fun secretHex(context: Context): String {
        val sp = context.getSharedPreferences("tg", Context.MODE_PRIVATE)
        var s = sp.getString("secret", null)
        if (s == null || s.length != 32) {
            val b = ByteArray(16)
            rnd.nextBytes(b)
            s = b.joinToString("") { "%02x".format(it) }
            sp.edit().putString("secret", s).apply()
        }
        return s
    }

    fun link(context: Context): String {
        val port = _stats.value.port.takeIf { it > 0 } ?: DEFAULT_PORT
        return "tg://proxy?server=127.0.0.1&port=$port&secret=dd" + secretHex(context)
    }

    @Synchronized
    fun start(context: Context) {
        if (server != null) return
        appContext = context.applicationContext
        secret = hex(secretHex(context))
        val ss = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), DEFAULT_PORT), 64)
            }
        } catch (e: Exception) {
            Logs.append(context, "tg bridge bind failed: ${e.message}")
            return
        }
        server = ss
        publish(true)
        thread(name = "tg-accept", isDaemon = true) {
            while (!ss.isClosed) {
                val c = try {
                    ss.accept()
                } catch (_: Exception) {
                    break
                }
                thread(name = "tg-client", isDaemon = true) { handle(c) }
            }
        }
    }

    @Synchronized
    fun stop() {
        try {
            server?.close()
        } catch (_: Exception) {
        }
        server = null
        publish(false)
    }

    private fun publish(running: Boolean = server != null) {
        _stats.value = TgStats(running, if (running) DEFAULT_PORT else 0, active.get(), total.get(), wsCount.get(), tcpCount.get(), bytes.get())
    }

    private fun aes(key: ByteArray, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }

    private fun sha256(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        parts.forEach { md.update(it) }
        return md.digest()
    }

    private class Ctx(val cltDec: Cipher, val cltEnc: Cipher, val tgEnc: Cipher, val tgDec: Cipher, val relayInit: ByteArray, val dc: Int, val media: Boolean, val proto: Int)

    private fun readFully(input: InputStream, n: Int): ByteArray? {
        val b = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(b, off, n - off)
            if (r < 0) return null
            off += r
        }
        return b
    }

    private fun setup(hs: ByteArray): Ctx? {
        val prekeyIv = hs.copyOfRange(8, 56)
        val decKey = sha256(prekeyIv.copyOfRange(0, 32), secret)
        val cltDec = aes(decKey, prekeyIv.copyOfRange(32, 48))
        val plain = cltDec.update(hs)
        val tag = (plain[56].toInt() and 0xFF) or ((plain[57].toInt() and 0xFF) shl 8) or ((plain[58].toInt() and 0xFF) shl 16) or ((plain[59].toInt() and 0xFF) shl 24)
        if (tag !in TAGS) return null
        val dcIdx = ((plain[60].toInt() and 0xFF) or (plain[61].toInt() shl 8)).toShort().toInt()
        var dc = kotlin.math.abs(dcIdx)
        val media = dcIdx < 0
        if (dc >= 10000) dc -= 10000
        val rev = prekeyIv.reversedArray()
        val cltEnc = aes(sha256(rev.copyOfRange(0, 32), secret), rev.copyOfRange(32, 48))
        val init = relayInit(plain.copyOfRange(56, 60), if (media) -dc else dc)
        val encKey = init.copyOfRange(8, 40)
        val encIv = init.copyOfRange(40, 56)
        val rrev = init.copyOfRange(8, 56).reversedArray()
        val tgEnc = aes(encKey, encIv)
        val tgDec = aes(rrev.copyOfRange(0, 32), rrev.copyOfRange(32, 48))
        tgEnc.update(ByteArray(64))
        return Ctx(cltDec, cltEnc, tgEnc, tgDec, init, dc, media, tag)
    }

    private fun relayInit(tag: ByteArray, dcIdx: Int): ByteArray {
        val r = ByteArray(64)
        while (true) {
            rnd.nextBytes(r)
            val b0 = r[0].toInt() and 0xFF
            if (b0 == 0xEF) continue
            val s = String(r, 0, 4, Charsets.ISO_8859_1)
            if (s in setOf("HEAD", "POST", "GET ", "OPTI", "\u00ee\u00ee\u00ee\u00ee", "\u00dd\u00dd\u00dd\u00dd", "\u0016\u0003\u0001\u0002")) continue
            if (r[4].toInt() == 0 && r[5].toInt() == 0 && r[6].toInt() == 0 && r[7].toInt() == 0) continue
            break
        }
        val enc = aes(r.copyOfRange(8, 40), r.copyOfRange(40, 56)).update(r)
        val tail = ByteArray(8)
        System.arraycopy(tag, 0, tail, 0, 4)
        tail[4] = (dcIdx and 0xFF).toByte()
        tail[5] = ((dcIdx shr 8) and 0xFF).toByte()
        tail[6] = rnd.nextInt().toByte()
        tail[7] = rnd.nextInt().toByte()
        val out = r.copyOf()
        for (i in 0 until 8) out[56 + i] = (tail[i].toInt() xor enc[56 + i].toInt() xor r[56 + i].toInt()).toByte()
        return out
    }

    private class Splitter(val proto: Int) {
        private val cipher = java.io.ByteArrayOutputStream()
        private val plain = java.io.ByteArrayOutputStream()
        private var disabled = false

        fun split(c: ByteArray, p: ByteArray): List<ByteArray> {
            if (disabled) return listOf(c)
            cipher.write(c)
            plain.write(p)
            val cb = cipher.toByteArray()
            val pb = plain.toByteArray()
            val out = ArrayList<ByteArray>()
            var off = 0
            while (off < cb.size) {
                val avail = cb.size - off
                val len = next(pb, off, avail) ?: break
                if (len <= 0) {
                    out.add(cb.copyOfRange(off, cb.size))
                    off = cb.size
                    disabled = true
                    break
                }
                out.add(cb.copyOfRange(off, off + len))
                off += len
            }
            cipher.reset()
            plain.reset()
            if (off < cb.size) {
                cipher.write(cb, off, cb.size - off)
                plain.write(pb, off, pb.size - off)
            }
            return out
        }

        fun flush(): ByteArray? = cipher.toByteArray().takeIf { it.isNotEmpty() }

        private fun next(p: ByteArray, off: Int, avail: Int): Int? {
            if (proto == 0xEFEFEFEF.toInt()) {
                val first = p[off].toInt() and 0xFF
                val header: Int
                val payload: Int
                if (first == 0x7F || first == 0xFF) {
                    if (avail < 4) return null
                    payload = ((p[off + 1].toInt() and 0xFF) or ((p[off + 2].toInt() and 0xFF) shl 8) or ((p[off + 3].toInt() and 0xFF) shl 16)) * 4
                    header = 4
                } else {
                    payload = (first and 0x7F) * 4
                    header = 1
                }
                if (payload <= 0) return 0
                return if (avail < header + payload) null else header + payload
            }
            if (avail < 4) return null
            val n = ((p[off].toInt() and 0xFF) or ((p[off + 1].toInt() and 0xFF) shl 8) or ((p[off + 2].toInt() and 0xFF) shl 16) or ((p[off + 3].toInt() and 0x7F) shl 24))
            if (n <= 0 || n > 16 * 1024 * 1024) return 0
            return if (avail < 4 + n) null else 4 + n
        }
    }

    private fun handle(client: Socket) {
        active.incrementAndGet()
        total.incrementAndGet()
        publish()
        try {
            client.tcpNoDelay = true
            client.soTimeout = 0
            val input = client.getInputStream()
            val output = client.getOutputStream()
            client.soTimeout = 15000
            val hs = readFully(input, HANDSHAKE) ?: return
            client.soTimeout = 0
            val ctx = setup(hs) ?: return
            val now = System.currentTimeMillis()
            if (now > wsBroken && viaWs(ctx, input, output)) return
            viaTcp(ctx, input, output)
        } catch (_: Exception) {
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
            active.decrementAndGet()
            publish()
        }
    }

    private fun wsClient(engine: Boolean): OkHttpClient {
        val b = Http.client.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .connectTimeout(8, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    if (hostname.endsWith(".web.telegram.org")) listOf(InetAddress.getByName(WS_IP)) else Dns.SYSTEM.lookup(hostname)
            })
        val port = Live.socksPort
        if (engine && port > 0) b.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
        return b.build()
    }

    private val directWs by lazy { wsClient(false) }

    private fun viaWs(ctx: Ctx, input: InputStream, output: OutputStream): Boolean {
        val domains = if (ctx.media) listOf("kws${ctx.dc}-1.web.telegram.org", "kws${ctx.dc}.web.telegram.org") else listOf("kws${ctx.dc}.web.telegram.org")
        for (engine in listOf(false, true)) {
            if (engine && Live.socksPort <= 0) continue
            val client = if (engine) wsClient(true) else directWs
            for (d in domains) {
                val ok = runWs(client, d, ctx, input, output)
                if (ok != null) return ok
            }
        }
        wsBroken = System.currentTimeMillis() + 60_000L
        return false
    }

    private fun runWs(client: OkHttpClient, domain: String, ctx: Ctx, input: InputStream, output: OutputStream): Boolean? {
        val opened = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        val outLock = Any()
        val req = Request.Builder().url("wss://$domain/apiws").header("Sec-WebSocket-Protocol", "binary").header("Origin", "https://web.telegram.org").build()
        val ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                ok.set(true)
                opened.countDown()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                TgBridge.bytes.addAndGet(data.size.toLong())
                val out = ctx.cltEnc.update(ctx.tgDec.update(data))
                try {
                    synchronized(outLock) {
                        output.write(out)
                        output.flush()
                    }
                } catch (_: Exception) {
                    webSocket.cancel()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                closed.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                opened.countDown()
                closed.countDown()
            }
        })
        if (!opened.await(10, TimeUnit.SECONDS) || !ok.get()) {
            ws.cancel()
            return null
        }
        wsCount.incrementAndGet()
        ws.send(ctx.relayInit.toByteString())
        val splitter = Splitter(ctx.proto)
        val pump = thread(name = "tg-up", isDaemon = true) {
            val buf = ByteArray(65536)
            try {
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    bytes.addAndGet(n.toLong())
                    val plain = ctx.cltDec.update(buf, 0, n) ?: continue
                    val enc = ctx.tgEnc.update(plain) ?: continue
                    for (part in splitter.split(enc, plain)) ws.send(part.toByteString())
                }
                splitter.flush()?.let { ws.send(it.toByteString()) }
            } catch (_: Exception) {
            }
            ws.close(1000, null)
        }
        closed.await()
        pump.interrupt()
        return true
    }

    private fun viaTcp(ctx: Ctx, input: InputStream, output: OutputStream) {
        val ip = DC_IPS[ctx.dc] ?: return
        val port = Live.socksPort
        val sock = if (port > 0) Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))) else Socket()
        sock.use { s ->
            s.connect(InetSocketAddress(ip, 443), 8000)
            s.tcpNoDelay = true
            tcpCount.incrementAndGet()
            val up = s.getOutputStream()
            val down = s.getInputStream()
            up.write(ctx.relayInit)
            up.flush()
            val t = thread(name = "tg-down", isDaemon = true) {
                val buf = ByteArray(65536)
                try {
                    while (true) {
                        val n = down.read(buf)
                        if (n < 0) break
                        bytes.addAndGet(n.toLong())
                        output.write(ctx.cltEnc.update(ctx.tgDec.update(buf, 0, n)))
                        output.flush()
                    }
                } catch (_: Exception) {
                }
                try {
                    s.close()
                } catch (_: Exception) {
                }
            }
            val buf = ByteArray(65536)
            try {
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    bytes.addAndGet(n.toLong())
                    up.write(ctx.tgEnc.update(ctx.cltDec.update(buf, 0, n)))
                    up.flush()
                }
            } catch (_: Exception) {
            }
            t.interrupt()
        }
    }

    private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
