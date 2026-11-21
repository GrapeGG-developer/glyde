package gpr.greentune.unblock.tg

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class RawWs private constructor(private val socket: Socket) {
    private val input = DataInputStream(BufferedInputStream(socket.getInputStream(), 65536))
    private val output: OutputStream = socket.getOutputStream()
    private val writeLock = Any()
    @Volatile
    var closed = false
        private set
    val created = System.currentTimeMillis()

    fun send(data: ByteArray) {
        val mask = ByteArray(4).also { rnd.nextBytes(it) }
        val n = data.size
        val header = when {
            n < 126 -> byteArrayOf(0x82.toByte(), (0x80 or n).toByte())
            n < 65536 -> byteArrayOf(0x82.toByte(), (0x80 or 126).toByte(), (n shr 8).toByte(), n.toByte())
            else -> ByteArray(10).also {
                it[0] = 0x82.toByte()
                it[1] = (0x80 or 127).toByte()
                for (i in 0 until 8) it[2 + i] = (n.toLong() shr (56 - 8 * i)).toByte()
            }
        }
        val frame = ByteArray(header.size + 4 + n)
        System.arraycopy(header, 0, frame, 0, header.size)
        System.arraycopy(mask, 0, frame, header.size, 4)
        val off = header.size + 4
        for (i in 0 until n) frame[off + i] = (data[i].toInt() xor mask[i and 3].toInt()).toByte()
        synchronized(writeLock) {
            output.write(frame)
            output.flush()
        }
    }

    private fun control(op: Int, payload: ByteArray) {
        val mask = ByteArray(4).also { rnd.nextBytes(it) }
        val frame = ByteArray(2 + 4 + payload.size)
        frame[0] = (0x80 or op).toByte()
        frame[1] = (0x80 or payload.size).toByte()
        System.arraycopy(mask, 0, frame, 2, 4)
        for (i in payload.indices) frame[6 + i] = (payload[i].toInt() xor mask[i and 3].toInt()).toByte()
        synchronized(writeLock) {
            output.write(frame)
            output.flush()
        }
    }

    fun recv(): ByteArray? {
        var message: java.io.ByteArrayOutputStream? = null
        while (true) {
            val b0 = input.read()
            if (b0 < 0) return null
            val b1 = input.readUnsignedByte()
            val fin = (b0 and 0x80) != 0
            val op = b0 and 0x0F
            var len = (b1 and 0x7F).toLong()
            if (len == 126L) len = input.readUnsignedShort().toLong()
            else if (len == 127L) len = input.readLong()
            if (len < 0 || len > MAX) throw EOFException("frame too large")
            val masked = (b1 and 0x80) != 0
            val mask = if (masked) ByteArray(4).also { input.readFully(it) } else null
            val payload = ByteArray(len.toInt())
            input.readFully(payload)
            if (mask != null) for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i and 3].toInt()).toByte()
            when (op) {
                0x8 -> {
                    try { control(0x8, ByteArray(0)) } catch (_: Exception) { }
                    return null
                }
                0x9 -> {
                    try { control(0xA, payload) } catch (_: Exception) { }
                }
                0xA -> {}
                0x0, 0x1, 0x2 -> {
                    if (fin && message == null) return payload
                    if (message == null) message = java.io.ByteArrayOutputStream()
                    message.write(payload)
                    if (fin) return message.toByteArray()
                }
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        try { control(0x8, byteArrayOf(0x03, 0xE8.toByte())) } catch (_: Exception) { }
        try { socket.close() } catch (_: Exception) { }
    }

    companion object {
        private const val MAX = 16L * 1024 * 1024
        private val rnd = SecureRandom()
        private val trustAll: SSLSocketFactory by lazy {
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }), SecureRandom())
            ctx.socketFactory
        }

        fun connect(target: String, domain: String, path: String = "/apiws", sni: String? = null, socksPort: Int = 0, timeoutMs: Int = 8000): RawWs {
            val plain = if (socksPort > 0) Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))) else Socket()
            try {
                val addr = if (socksPort > 0 && !target.first().isDigit()) InetSocketAddress.createUnresolved(target, 443) else InetSocketAddress(target, 443)
                plain.connect(addr, timeoutMs)
                plain.tcpNoDelay = true
                plain.soTimeout = timeoutMs
                val fronted = sni != null
                val factory = if (fronted) trustAll else SSLSocketFactory.getDefault() as SSLSocketFactory
                val ssl = factory.createSocket(plain, sni ?: domain, 443, true) as SSLSocket
                ssl.startHandshake()
                if (!fronted && !HttpsURLConnection.getDefaultHostnameVerifier().verify(domain, ssl.session)) {
                    ssl.close()
                    throw javax.net.ssl.SSLPeerUnverifiedException("hostname")
                }
                val key = android.util.Base64.encodeToString(ByteArray(16).also { rnd.nextBytes(it) }, android.util.Base64.NO_WRAP)
                val req = "GET $path HTTP/1.1\r\nHost: $domain\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: binary\r\nOrigin: https://web.telegram.org\r\nUser-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36\r\n\r\n"
                val out = ssl.outputStream
                out.write(req.toByteArray(Charsets.ISO_8859_1))
                out.flush()
                val ins = ssl.inputStream
                val head = StringBuilder()
                while (!head.endsWith("\r\n\r\n")) {
                    val ch = ins.read()
                    if (ch < 0) throw EOFException("handshake")
                    head.append(ch.toChar())
                    if (head.length > 8192) throw EOFException("header too long")
                }
                val status = head.lineSequence().first().split(' ').getOrNull(1)?.toIntOrNull() ?: 0
                if (status != 101) {
                    ssl.close()
                    throw java.io.IOException("ws status $status")
                }
                ssl.soTimeout = 0
                return RawWs(ssl)
            } catch (e: Exception) {
                try { plain.close() } catch (_: Exception) { }
                throw e
            }
        }
    }
}
