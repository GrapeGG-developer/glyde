package gpr.greentune.unblock.core

import android.content.Context
import android.os.Build
import android.os.SystemClock
import gpr.greentune.unblock.App
import gpr.greentune.unblock.BuildConfig
import gpr.greentune.unblock.R
import gpr.greentune.unblock.net.Geo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

enum class Verdict { Ok, DnsBlocked, IpBlocked, Dpi, Down }

data class DiagItem(
    val serviceId: String,
    val name: String,
    val host: String,
    val dnsSystem: String?,
    val dnsDoh: String?,
    val dnsSpoofed: Boolean,
    val tcp: Boolean,
    val tcpMs: Long,
    val direct: Boolean,
    val directMs: Long,
    val bypass: Boolean?,
    val bypassMs: Long,
    val verdict: Verdict,
)

data class SpeedResult(val pingMs: Long, val mbps: Double, val jitterMs: Long)

data class DiagState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val items: List<DiagItem> = emptyList(),
    val speed: SpeedResult? = null,
    val speedRunning: Boolean = false,
    val speedProgress: Float = 0f,
    val finishedAt: Long = 0L,
    val error: String? = null,
)

object Diag {
    private val _state = MutableStateFlow(DiagState())
    val state: StateFlow<DiagState> get() = _state
    private var job: Job? = null
    private var speedJob: Job? = null

    fun isRunning(): Boolean = job?.isActive == true

    fun start(context: Context, only: Set<String>? = null) {
        if (isRunning()) return
        val app = context.applicationContext
        job = App.scope.launch {
            try {
                run(app, only)
            } catch (e: CancellationException) {
                _state.value = _state.value.copy(running = false)
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(running = false, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        speedJob?.cancel()
    }

    private fun bogon(a: InetAddress): Boolean {
        if (a !is Inet4Address) return false
        val b = a.address.map { it.toInt() and 0xFF }
        return b[0] == 0 || b[0] == 127 || b[0] == 10 || (b[0] == 172 && b[1] in 16..31) ||
            (b[0] == 192 && b[1] == 168) || (b[0] == 100 && b[1] in 64..127) || (b[0] == 169 && b[1] == 254) || b[0] >= 224
    }

    private fun tcp(addr: InetAddress, port: Int, timeout: Int): Long? = try {
        val t0 = SystemClock.elapsedRealtime()
        Socket().use { it.connect(InetSocketAddress(addr, port), timeout) }
        SystemClock.elapsedRealtime() - t0
    } catch (_: Exception) {
        null
    }

    private suspend fun run(context: Context, only: Set<String>?) {
        val all = Presets.active()
        val services = if (only == null) all.services else all.services.filter { it.id in only }
        val targets = services.mapNotNull { s -> s.tests.firstOrNull()?.let { s to it } }
        _state.value = DiagState(running = true, total = targets.size, speed = _state.value.speed)
        if (targets.isEmpty()) {
            _state.value = _state.value.copy(running = false, finishedAt = System.currentTimeMillis())
            return
        }
        val direct = Prober.client(null, 6000)
        val done = AtomicInteger(0)
        val sem = Semaphore(6)
        val items = coroutineScope {
            targets.map { (s, url) ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        val host = url.substringAfter("://").substringBefore('/').substringBefore(':')
                        val sys = try {
                            InetAddress.getAllByName(host).filterIsInstance<Inet4Address>()
                        } catch (_: Exception) {
                            emptyList()
                        }
                        val dohList = Prober.resolveDoh(host).orEmpty()
                        val spoofed = dohList.isNotEmpty() && (sys.isEmpty() || sys.all { bogon(it) })
                        val target = dohList.firstOrNull() ?: sys.firstOrNull { !bogon(it) }
                        val tcpMs = target?.let { tcp(it, 443, 4000) }
                        val r = if (tcpMs != null) Prober.probe(direct, url) else Prober.Result(false, 0)
                        val verdict = when {
                            target == null -> Verdict.Down
                            tcpMs == null -> Verdict.IpBlocked
                            r.ok && spoofed -> Verdict.DnsBlocked
                            r.ok -> Verdict.Ok
                            else -> Verdict.Dpi
                        }
                        val item = DiagItem(
                            s.id, s.name, host,
                            sys.firstOrNull()?.hostAddress, dohList.firstOrNull()?.hostAddress, spoofed,
                            tcpMs != null, tcpMs ?: 0, r.ok, r.ms, null, 0, verdict
                        )
                        val d = done.incrementAndGet()
                        _state.value = _state.value.copy(done = d, items = (_state.value.items + item))
                        item
                    }
                }
            }.awaitAll()
        }

        val needBypass = items.filter { it.verdict != Verdict.Ok }
        var finalItems = items
        if (needBypass.isNotEmpty()) {
            val port = Ciadpi.freePort()
            val proc = withContext(Dispatchers.IO) { Ciadpi.start(context, port, Engine.currentArgs(), false) }
            try {
                if (Ciadpi.awaitReady(port, proc, 3000)) {
                    val client = Prober.client(port, 6000)
                    val urls = targets.associate { it.first.id to it.second }
                    val res = coroutineScope {
                        needBypass.map { d ->
                            async(Dispatchers.IO) { d.serviceId to Prober.probe(client, urls[d.serviceId] ?: "https://${d.host}/") }
                        }.awaitAll().toMap()
                    }
                    finalItems = items.map { d ->
                        val b = res[d.serviceId]
                        if (b == null) d else d.copy(bypass = b.ok, bypassMs = b.ms)
                    }
                }
            } finally {
                withContext(Dispatchers.IO) { Ciadpi.stop(proc) }
            }
        }
        val order = targets.withIndex().associate { it.value.first.id to it.index }
        _state.value = _state.value.copy(
            running = false,
            items = finalItems.sortedBy { order[it.serviceId] ?: 0 },
            finishedAt = System.currentTimeMillis()
        )
    }

    fun speed(context: Context) {
        if (speedJob?.isActive == true) return
        speedJob = App.scope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(speedRunning = true, speedProgress = 0f)
            try {
                val host = "speed.cloudflare.com"
                val addr = Prober.resolveDoh(host)?.firstOrNull() ?: InetAddress.getByName(host)
                val pings = (0 until 5).mapNotNull { tcp(addr, 443, 3000) }
                val ping = if (pings.isEmpty()) 0L else pings.sorted()[pings.size / 2]
                val jitter = if (pings.size < 2) 0L else pings.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average().toLong()
                val client = Prober.client(null, 15000)
                val req = Request.Builder().url("https://$host/__down?bytes=50000000").header("User-Agent", Prober.UA).build()
                var total = 0L
                val window = 8000L
                val t0 = SystemClock.elapsedRealtime()
                var tStart = 0L
                try {
                    client.newCall(req).execute().use { resp ->
                        val src = resp.body?.byteStream() ?: return@use
                        val buf = ByteArray(65536)
                        tStart = SystemClock.elapsedRealtime()
                        while (true) {
                            val n = src.read(buf)
                            if (n < 0) break
                            total += n
                            val el = SystemClock.elapsedRealtime() - tStart
                            if (el >= window) break
                            _state.value = _state.value.copy(speedProgress = el.toFloat() / window)
                        }
                    }
                } catch (_: Exception) {
                }
                val elapsed = maxOf(1L, SystemClock.elapsedRealtime() - (if (tStart > 0) tStart else t0))
                val mbps = total * 8.0 / 1000.0 / elapsed
                _state.value = _state.value.copy(speed = SpeedResult(ping, mbps, jitter), speedRunning = false, speedProgress = 1f)
            } catch (e: Exception) {
                _state.value = _state.value.copy(speedRunning = false, error = e.message)
            }
        }
    }

    fun verdictText(context: Context, v: Verdict): String = context.getString(
        when (v) {
            Verdict.Ok -> R.string.diag_v_ok
            Verdict.DnsBlocked -> R.string.diag_v_dns
            Verdict.IpBlocked -> R.string.diag_v_ip
            Verdict.Dpi -> R.string.diag_v_dpi
            Verdict.Down -> R.string.diag_v_down
        }
    )

    fun report(context: Context): String {
        val st = _state.value
        val sb = StringBuilder()
        sb.append("GT Unblock ").append(BuildConfig.VERSION_NAME).append(" · Android ").append(Build.VERSION.RELEASE).append('\n')
        Geo.last.value?.let { sb.append(it.label).append('\n') }
        if (st.finishedAt > 0) sb.append(DateFormat.getDateTimeInstance().format(Date(st.finishedAt))).append('\n')
        sb.append('\n')
        for (i in st.items) {
            sb.append(i.name).append(" (").append(i.host).append(")\n")
            sb.append("  DNS: ").append(i.dnsSystem ?: "—").append(" / DoH: ").append(i.dnsDoh ?: "—")
            if (i.dnsSpoofed) sb.append(" !")
            sb.append('\n')
            sb.append("  TCP: ").append(if (i.tcp) "${i.tcpMs} ms" else "✗")
            sb.append(" · HTTPS: ").append(if (i.direct) "${i.directMs} ms" else "✗")
            if (i.bypass != null) sb.append(" · GT: ").append(if (i.bypass) "${i.bypassMs} ms" else "✗")
            sb.append('\n')
            sb.append("  → ").append(verdictText(context, i.verdict)).append('\n')
        }
        st.speed?.let {
            sb.append('\n').append(context.getString(R.string.diag_speed_line, "%.1f".format(it.mbps), it.pingMs, it.jitterMs)).append('\n')
        }
        return sb.toString()
    }
}
