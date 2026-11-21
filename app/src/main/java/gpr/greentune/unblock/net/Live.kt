package gpr.greentune.unblock.net

import android.content.Context
import android.os.SystemClock
import gpr.greentune.unblock.BuildConfig
import gpr.greentune.unblock.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class Announcement(val id: String, val title: String, val text: String, val level: String)

enum class LinkState { Off, Connecting, Live, Polling, Mirror }

object Live {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _link = MutableStateFlow(LinkState.Off)
    val link: StateFlow<LinkState> get() = _link
    private val _flags = MutableStateFlow(JSONObject())
    val flags: StateFlow<JSONObject> get() = _flags
    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    val announcements: StateFlow<List<Announcement>> get() = _announcements
    private val _ring = MutableStateFlow(BuildConfig.RING)
    val ring: StateFlow<String> get() = _ring
    private val _siege = MutableStateFlow(false)
    val siege: StateFlow<Boolean> get() = _siege
    var socksPort: Int
        get() = Ports.socks
        set(v) {
            Ports.socks = v
        }

    private var app: Context? = null
    private var sseJob: Job? = null
    private var pollJob: Job? = null
    private var call: Call? = null
    @Volatile
    private var foreground = false
    @Volatile
    private var lastEvent = 0L
    @Volatile
    private var lastMirror = 0L
    @Volatile
    private var serverIndex = 0
    @Volatile
    private var blockedUntil = 0L

    val servers: List<String> by lazy {
        (listOf(BuildConfig.SERVER_URL) + BuildConfig.BACKUP_URLS.split('|', ',', ' '))
            .map { it.trim().trimEnd('/') }
            .filter { it.startsWith("https://") }
            .distinct()
    }

    val mirrors: List<String> by lazy { BuildConfig.MIRRORS.split('|').map { it.trim() }.filter { it.isNotEmpty() } }

    val base: String get() = servers[serverIndex.coerceIn(0, servers.size - 1)]

    private val direct: OkHttpClient by lazy {
        Http.client.newBuilder()
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun client(viaEngine: Boolean): OkHttpClient {
        val port = socksPort
        if (!viaEngine || port <= 0) return direct
        return direct.newBuilder().proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))).build()
    }

    private val shortClient: OkHttpClient by lazy { Http.poll }

    fun start(context: Context) {
        app = context.applicationContext
        if (sseJob?.isActive == true) return
        sseJob = scope.launch { sseLoop() }
        if (pollJob?.isActive != true) pollJob = scope.launch { pollLoop() }
    }

    fun setForeground(value: Boolean) {
        foreground = value
    }

    fun reconnect() {
        call?.cancel()
    }

    private suspend fun sseLoop() {
        var fails = 0
        var viaEngine = false
        while (scope.isActive) {
            val now = SystemClock.elapsedRealtime()
            if (now < blockedUntil) {
                delay(blockedUntil - now)
                continue
            }
            _link.value = if (_link.value == LinkState.Live) LinkState.Live else LinkState.Connecting
            val code = try {
                openStream(base, viaEngine)
            } catch (_: Exception) {
                -1
            }
            if (code == 200) {
                fails = 0
            } else {
                fails++
                if (code == 403) {
                    blockedUntil = SystemClock.elapsedRealtime() + 30 * 60_000L
                } else if (code == 429 || code == 503) {
                    blockedUntil = SystemClock.elapsedRealtime() + 60_000L
                } else {
                    if (socksPort > 0 && !viaEngine) {
                        viaEngine = true
                    } else {
                        viaEngine = false
                        if (servers.size > 1) serverIndex = (serverIndex + 1) % servers.size
                    }
                }
            }
            if (_link.value == LinkState.Live || _link.value == LinkState.Connecting) _link.value = LinkState.Polling
            val wait = if (fails == 0) 500L else minOf(1000L * (1L shl minOf(fails, 6)), 60_000L)
            delay(wait)
        }
    }

    private fun openStream(server: String, viaEngine: Boolean): Int {
        val rb = Request.Builder().url("$server/v3/live").header("Accept", "text/event-stream").header("User-Agent", "Glyde-Android")
        Device.headers().forEach { (k, v) -> rb.header(k, v) }
        val c = client(viaEngine).newCall(rb.build())
        call = c
        c.execute().use { resp ->
            if (resp.code != 200) {
                if (resp.code == 403) _link.value = LinkState.Off
                return resp.code
            }
            val src = resp.body?.source() ?: return -1
            _link.value = LinkState.Live
            lastEvent = SystemClock.elapsedRealtime()
            var event = "message"
            val data = StringBuilder()
            while (true) {
                val line = src.readUtf8Line() ?: break
                lastEvent = SystemClock.elapsedRealtime()
                when {
                    line.isEmpty() -> {
                        if (data.isNotEmpty()) dispatch(event, data.toString())
                        event = "message"
                        data.setLength(0)
                    }
                    line.startsWith(":") -> Unit
                    line.startsWith("event:") -> event = line.substring(6).trim()
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.substring(5).trimStart())
                    }
                }
            }
        }
        return 200
    }

    private fun dispatch(event: String, raw: String) {
        val o = try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
        when (event) {
            "hello" -> {
                o.optJSONObject("flags")?.let { _flags.value = it }
                o.optJSONArray("announcements")?.let { _announcements.value = parseAnnouncements(it) }
                o.optString("ring").takeIf { it.isNotBlank() }?.let { _ring.value = it }
                _siege.value = o.optBoolean("siege", false)
                offer(o.optJSONObject("update"))
            }
            "update" -> offer(o)
            "announce" -> {
                val a = parseAnnouncement(o) ?: return
                _announcements.value = _announcements.value.filter { it.id != a.id } + a
            }
            "announce_off" -> {
                val id = o.optString("id")
                _announcements.value = _announcements.value.filter { it.id != id }
            }
            "flags" -> _flags.value = o
            "ring" -> o.optString("ring").takeIf { it.isNotBlank() }?.let { _ring.value = it }
            "kick" -> {
                blockedUntil = SystemClock.elapsedRealtime() + if (o.optString("reason") == "ban") 30 * 60_000L else 15_000L
                call?.cancel()
            }
        }
    }

    private fun offer(o: JSONObject?) {
        val ctx = app ?: return
        scope.launch { Updater.offer(ctx, o) }
    }

    private suspend fun pollLoop() {
        var fails = 0
        while (scope.isActive) {
            val live = _link.value == LinkState.Live && SystemClock.elapsedRealtime() - lastEvent < 50_000L
            if (live) {
                delay(1000)
                continue
            }
            if (SystemClock.elapsedRealtime() < blockedUntil) {
                delay(1000)
                continue
            }
            if (!foreground) {
                delay(5000)
                if (!foreground && SystemClock.elapsedRealtime() - lastMirror < 60_000L) continue
            }
            val ok = check()
            if (ok) {
                fails = 0
                if (_link.value != LinkState.Live) _link.value = LinkState.Polling
            } else {
                fails++
                if (SystemClock.elapsedRealtime() - lastMirror > 60_000L || fails == 3) {
                    lastMirror = SystemClock.elapsedRealtime()
                    if (mirror()) _link.value = LinkState.Mirror
                }
            }
            delay(if (fails == 0) 200L else minOf(200L * (1L shl minOf(fails, 5)), 5_000L))
        }
    }

    suspend fun check(): Boolean = withContext(Dispatchers.IO) {
        val rb = Request.Builder().url("$base/v3/check").header("User-Agent", "Glyde-Android")
        Device.headers().forEach { (k, v) -> rb.header(k, v) }
        try {
            shortClient.newCall(rb.build()).execute().use { r ->
                if (r.code == 403) {
                    blockedUntil = SystemClock.elapsedRealtime() + 30 * 60_000L
                    return@withContext false
                }
                if (!r.isSuccessful) return@withContext false
                val o = JSONObject(r.body?.string() ?: return@withContext false)
                o.optJSONObject("flags")?.let { _flags.value = it }
                o.optJSONArray("announcements")?.let { _announcements.value = parseAnnouncements(it) }
                offer(o.optJSONObject("update"))
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun mirror(): Boolean = withContext(Dispatchers.IO) {
        for (url in mirrors) {
            val text = Http.getText(url) ?: continue
            val o = try {
                JSONObject(text)
            } catch (_: Exception) {
                continue
            }
            if (o.optString("ring", "stable") != "stable") continue
            val assets = o.optJSONObject("assets") ?: continue
            val name = Updater.assetName(o.optString("version"), assets)
            o.put("asset", name).put("url", assets.optString(name)).put("size", o.optJSONObject("sizes")?.optLong(name) ?: 0L)
            offer(o)
            return@withContext true
        }
        false
    }

    private fun parseAnnouncements(arr: JSONArray): List<Announcement> =
        (0 until arr.length()).mapNotNull { parseAnnouncement(arr.optJSONObject(it)) }

    private fun parseAnnouncement(o: JSONObject?): Announcement? {
        o ?: return null
        val id = o.opt("id")?.toString()?.takeIf { it.isNotBlank() } ?: return null
        return Announcement(id, o.optString("title"), o.optString("text"), o.optString("level", "info"))
    }

    private val json = "application/json; charset=utf-8".toMediaType()

    fun post(path: String, body: JSONObject) {
        scope.launch {
            for (attempt in 0 until 3) {
                val rb = Request.Builder().url(base + path).header("User-Agent", "Glyde-Android").post(body.toString().toRequestBody(json))
                Device.headers().forEach { (k, v) -> rb.header(k, v) }
                val ok = try {
                    Http.client.newCall(rb.build()).execute().use { it.isSuccessful || it.code in 400..499 }
                } catch (_: Exception) {
                    false
                }
                if (ok) return@launch
                delay(5000L * (attempt + 1))
            }
        }
    }

    private var lastStatus = ""

    fun status(state: String, strategy: String, services: JSONObject? = null) {
        val key = "$state|$strategy|${Device.busy}"
        if (key == lastStatus && services == null) return
        lastStatus = key
        val b = JSONObject().put("state", state).put("strategy", strategy).put("busy", Device.busy).put("net", Device.net())
        if (services != null) b.put("services", services)
        post("/v3/status", b)
    }

    private val busyCount = java.util.concurrent.atomic.AtomicInteger(0)

    suspend fun <T> busy(block: suspend () -> T): T {
        if (busyCount.incrementAndGet() == 1) setBusy(true)
        try {
            return block()
        } finally {
            if (busyCount.decrementAndGet() == 0) setBusy(false)
        }
    }

    fun setBusy(value: Boolean) {
        if (Device.busy == value) return
        Device.busy = value
        lastStatus = ""
    }

    fun updateResult(from: String, to: String, result: String, detail: String = "") {
        post("/v3/update-result", JSONObject().put("from", from).put("to", to).put("result", result).put("detail", detail.take(400)))
    }

    fun flag(name: String, default: Boolean = false): Boolean = _flags.value.optBoolean(name, default)
}
