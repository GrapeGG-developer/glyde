package gpr.greentune.unblock.update

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import gpr.greentune.unblock.BuildConfig
import gpr.greentune.unblock.R
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.net.Backend
import gpr.greentune.unblock.net.Http
import gpr.greentune.unblock.net.Device
import gpr.greentune.unblock.net.LinkState
import gpr.greentune.unblock.net.Live
import gpr.greentune.unblock.ui.MainActivity
import gpr.greentune.unblock.vpn.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import android.os.SystemClock
import androidx.core.content.FileProvider
import gpr.greentune.unblock.App
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class UpdateInfo(
    val version: String,
    val code: Int,
    val changelog: String,
    val url: String,
    val mirror: String?,
    val size: Long,
    val beta: Boolean,
    val display: String = version,
    val sha256: String? = null,
)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState()
    data class ReadyToInstall(val info: UpdateInfo) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

object Updater {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> get() = _state
    private var downloadJob: Job? = null
    private val checkLock = Mutex()
    var pendingInstall: UpdateInfo? = null

    fun abiAsset(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
        return if (abi != null) "Glyde-%s-$abi.apk" else "Glyde-%s-universal.apk"
    }

    fun assetName(version: String, assets: JSONObject): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") } ?: "universal"
        val names = listOf("Glyde-$version-$abi.apk", "Glyde-$version-universal.apk", "GT-Unblock-$version-$abi.apk", "GT-Unblock-$version-universal.apk")
        return names.firstOrNull { assets.optString(it).isNotBlank() } ?: names[0]
    }

    private fun parseOffer(o: JSONObject?): UpdateInfo? {
        o ?: return null
        val version = o.optString("version").ifBlank { return null }
        val assets = o.optJSONObject("assets") ?: JSONObject()
        val name = o.optString("asset").takeIf { it.isNotBlank() && assets.optString(it).isNotBlank() } ?: assetName(version, assets)
        val url = o.optString("url").takeIf { it.isNotBlank() && o.optString("asset") == name } ?: assets.optString(name).ifBlank { return null }
        return UpdateInfo(
            version = version,
            code = o.optInt("code", 0),
            changelog = o.optString("changelog"),
            url = url,
            mirror = o.optString("mirror").takeIf { it.isNotBlank() },
            size = o.optJSONObject("sizes")?.optLong(name, 0L)?.takeIf { it > 0 } ?: o.optLong("size", 0L),
            beta = o.optString("ring", "stable") != "stable",
            display = o.optString("display").ifBlank { version },
            sha256 = o.optJSONObject("sums")?.optString(name)?.takeIf { it.length == 64 },
        )
    }

    suspend fun offer(context: Context, o: JSONObject?) {
        val info = parseOffer(o) ?: return
        if (!isNewer(info)) return
        val st = _state.value
        if (st is UpdateState.Downloading || st is UpdateState.Checking) return
        if (current()?.version == info.version) return
        lastInfo = info
        _state.value = if (apkFile(context, info).let { it.exists() && valid(context, it, info) }) UpdateState.ReadyToInstall(info) else UpdateState.Available(info)
        if (!foreground) {
            val s = SettingsStore.value
            if (info.version != s.skippedVersion && info.version != s.notifiedVersion) {
                SettingsStore.update { it.copy(notifiedVersion = info.version) }
                notifyAvailable(context, info)
            }
        }
    }

    fun current(): UpdateInfo? = when (val s = _state.value) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.ReadyToInstall -> s.info
        else -> null
    }

    private var remoteVersion: String? = null
    private var lastFull = 0L
    private var lastOk = false
    private var pollFails = 0
    var dismissed: String? = null
    var lastInfo: UpdateInfo? = null
    private val _confirm = MutableStateFlow<Intent?>(null)
    val confirm: StateFlow<Intent?> get() = _confirm

    fun requestConfirm(intent: Intent?) {
        _confirm.value = intent
    }

    fun pollDelay(): Long = if (pollFails == 0) 200L else minOf(200L * (1L shl minOf(pollFails, 5)), 5_000L)

    suspend fun poll(context: Context): UpdateInfo? {
        if (!checkLock.tryLock()) return current()
        try {
            Live.check()
            return current()
        } finally {
            checkLock.unlock()
        }
    }

    suspend fun autoCheck(context: Context): UpdateInfo? = poll(context)

    @Volatile
    var foreground = false

    suspend fun backgroundCheck(context: Context) {
        if (foreground) return
        if (Live.link.value != LinkState.Live) poll(context)
        val info = current() ?: return
        val s = SettingsStore.value
        if (info.version == s.skippedVersion || info.version == s.notifiedVersion) return
        SettingsStore.update { it.copy(notifiedVersion = info.version) }
        notifyAvailable(context, info)
    }

    suspend fun check(context: Context, manual: Boolean): UpdateInfo? {
        val now = _state.value
        if (now is UpdateState.Downloading || (now is UpdateState.ReadyToInstall && downloadJob?.isActive == true)) return current()
        if (manual) _state.value = UpdateState.Checking
        val beta = SettingsStore.value.beta
        val info = withContext(Dispatchers.IO) { fromV3() ?: fromBackend(beta) ?: fromMirrors() ?: fromGithub(beta) }
        if (info == null) {
            if (manual) _state.value = UpdateState.Failed(context.getString(R.string.update_check_failed))
            return null
        }
        lastOk = true
        if (manual) SettingsStore.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
        return if (isNewer(info)) {
            lastInfo = info
            val cur = current()
            val failedRecently = !manual && _state.value is UpdateState.Failed && SystemClock.elapsedRealtime() - failedAt < 15_000L
            if (cur?.version != info.version && !failedRecently) {
                _state.value = if (apkFile(context, info).let { it.exists() && valid(context, it, info) }) UpdateState.ReadyToInstall(info) else UpdateState.Available(info)
            } else if (manual && cur == null) {
                _state.value = UpdateState.Available(info)
            }
            info
        } else {
            if (manual) _state.value = UpdateState.UpToDate
            null
        }
    }

    private fun isNewer(info: UpdateInfo): Boolean {
        if (info.code > 0) return info.code > BuildConfig.VERSION_CODE
        return compare(info.version, BuildConfig.VERSION_NAME) > 0
    }

    fun compare(a: String, b: String): Int {
        fun parts(v: String) = v.removePrefix("v").split('-')[0].split('.').map { it.toIntOrNull() ?: 0 }
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        val sa = a.contains('-')
        val sb = b.contains('-')
        return when {
            sa && !sb -> -1
            !sa && sb -> 1
            else -> a.compareTo(b)
        }
    }

    private fun fromV3(): UpdateInfo? {
        for (server in Live.servers) {
            val o = Http.getJson("$server/v3/check", Device.headers()) ?: continue
            val u = o.optJSONObject("update")
            return parseOffer(u) ?: UpdateInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, "", "", null, 0, false)
        }
        return null
    }

    private fun fromMirrors(): UpdateInfo? {
        for (url in Live.mirrors) {
            val o = Http.getJson(url) ?: continue
            return parseOffer(o) ?: continue
        }
        return null
    }

    private suspend fun fromBackend(beta: Boolean): UpdateInfo? {
        val o = Backend.update(if (beta) "beta" else "stable") ?: return null
        val version = o.optString("version").ifBlank { return null }
        val assets = o.optJSONObject("assets")
        val name = assetName(version, assets ?: JSONObject())
        val url = assets?.optString(name)?.takeIf { it.isNotBlank() } ?: return null
        return UpdateInfo(
            version = version,
            code = o.optInt("code", 0),
            changelog = o.optString("changelog"),
            url = url,
            mirror = o.optString("mirror").takeIf { it.isNotBlank() },
            size = o.optJSONObject("sizes")?.optLong(url.substringAfterLast('/'), 0L) ?: 0L,
            beta = o.optBoolean("beta", false),
        )
    }

    private fun fromGithub(beta: Boolean): UpdateInfo? {
        val path = if (beta) "releases?per_page=10" else "releases/latest"
        val text = Http.getText(
            "https://api.github.com/repos/${Backend.repo}/$path",
            mapOf("Accept" to "application/vnd.github+json")
        ) ?: return null
        val rel = try {
            if (beta) {
                val arr = org.json.JSONArray(text)
                (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { !it.optBoolean("draft") }
            } else JSONObject(text)
        } catch (_: Exception) {
            null
        } ?: return null
        val version = rel.optString("tag_name").removePrefix("v").ifBlank { return null }
        val assets = rel.optJSONArray("assets") ?: return null
        var url: String? = null
        var size = 0L
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") } ?: "universal"
        for (name in listOf("Glyde-$version-$abi.apk", "Glyde-$version-universal.apk", "GT-Unblock-$version-$abi.apk", "GT-Unblock-$version-universal.apk")) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == name) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
            if (url != null) break
        }
        if (url == null) return null
        return UpdateInfo(version, 0, rel.optString("body"), url, null, size, rel.optBoolean("prerelease"))
    }

    private fun notifyAvailable(context: Context, info: UpdateInfo) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val pi = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_stat_glyde)
            .setContentTitle(context.getString(R.string.update_available_title, info.version))
            .setContentText(context.getString(R.string.update_available_text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            nm.notify(Notifications.ID_UPDATE, n)
        } catch (_: SecurityException) {
        }
    }

    fun skip(info: UpdateInfo) {
        SettingsStore.update { it.copy(skippedVersion = info.version) }
        _state.value = UpdateState.Idle
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    private fun dir(context: Context): File = File(context.cacheDir, "updates").apply { mkdirs() }

    fun apkFile(context: Context, info: UpdateInfo): File = File(dir(context), "Glyde-${info.version}.apk")

    private fun valid(context: Context, file: File, info: UpdateInfo): Boolean = try {
        val pi = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        pi != null && pi.packageName == context.packageName && (info.sha256 == null || sha256(file).equals(info.sha256, true))
    } catch (_: Exception) {
        false
    }

    fun sha256(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { inp ->
            val buf = ByteArray(65536)
            while (true) {
                val n = inp.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun startDownload(context: Context, info: UpdateInfo, installAfter: Boolean = true) {
        val app = context.applicationContext
        if (downloadJob?.isActive == true) return
        if (!canInstall(app)) {
            pendingInstall = info
            try {
                context.startActivity(installPermissionIntent(app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
            return
        }
        pendingInstall = null
        downloadJob = App.scope.launch {
            download(app, info)
            if (installAfter && _state.value is UpdateState.ReadyToInstall) {
                withContext(Dispatchers.Main) { install(app) }
            }
        }
    }

    fun resumePending(context: Context) {
        val info = pendingInstall ?: return
        if (canInstall(context)) startDownload(context, info)
    }

    suspend fun download(context: Context, info: UpdateInfo) = gpr.greentune.unblock.net.Live.busy { downloadInner(context, info) }

    private suspend fun downloadInner(context: Context, info: UpdateInfo) {
        val file = apkFile(context, info)
        if (file.exists() && valid(context, file, info)) {
            _state.value = UpdateState.ReadyToInstall(info)
            return
        }
        dir(context).listFiles()?.forEach { it.delete() }
        _state.value = UpdateState.Downloading(info, 0f)
        val part = File(dir(context), file.name + ".part")
        val ok = withContext(Dispatchers.IO) {
            val sources = listOfNotNull(info.url, info.mirror)
            var good = false
            for (src in sources) {
                for (attempt in 0 until 2) {
                    if (fetch(src, part, info) && part.renameTo(file) && valid(context, file, info)) {
                        good = true
                        break
                    }
                    file.delete()
                }
                if (good) break
            }
            good
        }
        if (ok) {
            _state.value = UpdateState.ReadyToInstall(info)
            notifyReady(context, info)
        } else {
            part.delete()
            file.delete()
            Live.updateResult(BuildConfig.VERSION_NAME, info.version, "failed", "download")
            setFailed(context.getString(R.string.update_download_failed))
        }
    }

    private fun fetch(url: String, file: File, info: UpdateInfo): Boolean = try {
        val have = if (file.exists()) file.length() else 0L
        val rb = Request.Builder().url(url).header("User-Agent", "Glyde-Android")
        if (have > 0) rb.header("Range", "bytes=$have-")
        Http.download.newCall(rb.build()).execute().use { resp ->
            if (!resp.isSuccessful) return false
            val body = resp.body ?: return false
            val append = have > 0 && resp.code == 206
            val start = if (append) have else 0L
            val total = (body.contentLength().takeIf { it > 0 }?.plus(start)) ?: info.size
            FileOutputStream(file, append).use { out ->
                val src = body.byteStream()
                val buf = ByteArray(65536)
                var done = start
                var lastEmit = 0L
                while (true) {
                    val n = src.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val now = SystemClock.elapsedRealtime()
                    if (total > 0 && now - lastEmit > 150) {
                        lastEmit = now
                        _state.value = UpdateState.Downloading(info, (done.toFloat() / total).coerceIn(0f, 0.99f))
                    }
                }
                out.flush()
                out.fd.sync()
            }
            if (total > 0) _state.value = UpdateState.Downloading(info, 1f)
            total <= 0 || file.length() >= total
        }
    } catch (_: Exception) {
        false
    }

    fun install(context: Context) {
        val info = current() ?: lastInfo ?: return
        val file = apkFile(context, info)
        if (!file.exists()) {
            _state.value = UpdateState.Available(info)
            return
        }
        if (!canInstall(context)) {
            pendingInstall = info
            try {
                context.startActivity(installPermissionIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            installSession(context, file)
        }
    }

    private fun installSession(context: Context, file: File) {
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, InstallReceiver::class.java)
                val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                else PendingIntent.FLAG_UPDATE_CURRENT
                val pi = PendingIntent.getBroadcast(context, id, intent, flags)
                session.commit(pi.intentSender)
            }
        } catch (e: Exception) {
            setFailed(e.message ?: context.getString(R.string.update_install_failed))
        }
    }

    private fun notifyReady(context: Context, info: UpdateInfo) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val pi = PendingIntent.getActivity(
            context, 4,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_stat_glyde)
            .setContentTitle(context.getString(R.string.update_ready))
            .setContentText(context.getString(R.string.update_ready_text, info.version))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            nm.notify(Notifications.ID_UPDATE, n)
        } catch (_: SecurityException) {
        }
    }

    private var failedAt = 0L

    fun restoreReady(context: Context) {
        val info = current() ?: lastInfo ?: return
        if (apkFile(context, info).exists()) _state.value = UpdateState.ReadyToInstall(info)
    }

    fun setFailed(message: String) {
        failedAt = SystemClock.elapsedRealtime()
        _state.value = UpdateState.Failed(message)
    }

    fun changelogFor(context: Context, version: String): String? = try {
        val text = context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
        val lines = text.lines()
        val start = lines.indexOfFirst { it.trim() == "## $version" }
        if (start < 0) null else {
            val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
            lines.subList(start + 1, end).joinToString("\n").trim().ifBlank { null }
        }
    } catch (_: Exception) {
        null
    }
}
