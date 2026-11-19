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
import gpr.greentune.unblock.ui.MainActivity
import gpr.greentune.unblock.vpn.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(
    val version: String,
    val code: Int,
    val changelog: String,
    val url: String,
    val mirror: String?,
    val size: Long,
    val beta: Boolean,
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

    fun abiAsset(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
        return if (abi != null) "GT-Unblock-%s-$abi.apk" else "GT-Unblock-%s-universal.apk"
    }

    suspend fun autoCheck(context: Context) {
        val s = SettingsStore.value
        val now = System.currentTimeMillis()
        if (now - s.lastUpdateCheck < 20 * 3600 * 1000L) return
        val info = check(context, manual = false) ?: return
        if (info.version == s.skippedVersion) return
        notifyAvailable(context, info)
    }

    suspend fun check(context: Context, manual: Boolean): UpdateInfo? {
        if (manual) _state.value = UpdateState.Checking
        val beta = SettingsStore.value.beta
        val info = withContext(Dispatchers.IO) { fromBackend(beta) ?: fromGithub(beta) }
        SettingsStore.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
        if (info == null) {
            if (manual) _state.value = UpdateState.Failed(context.getString(R.string.update_check_failed))
            return null
        }
        return if (isNewer(info)) {
            _state.value = UpdateState.Available(info)
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

    private suspend fun fromBackend(beta: Boolean): UpdateInfo? {
        val o = Backend.update(if (beta) "beta" else "stable") ?: return null
        val version = o.optString("version").ifBlank { return null }
        val assets = o.optJSONObject("assets")
        val name = abiAsset().format(version)
        val url = assets?.optString(name)?.takeIf { it.isNotBlank() }
            ?: assets?.optString(name.substringBeforeLast('-') + "-universal.apk")?.takeIf { it.isNotBlank() }
            ?: return null
        return UpdateInfo(
            version = version,
            code = o.optInt("code", 0),
            changelog = o.optString("changelog"),
            url = url,
            mirror = o.optString("mirror").takeIf { it.isNotBlank() },
            size = o.optLong("size", 0),
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
        val wanted = abiAsset().format(version)
        val universal = "GT-Unblock-$version-universal.apk"
        var url: String? = null
        var size = 0L
        for (name in listOf(wanted, universal)) {
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
            .setSmallIcon(R.drawable.ic_stat_wave)
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

    suspend fun download(context: Context, info: UpdateInfo) {
        _state.value = UpdateState.Downloading(info, 0f)
        val file = File(context.cacheDir, "update.apk")
        val ok = withContext(Dispatchers.IO) {
            fetch(info.url, file, info) || (info.mirror != null && fetch(info.mirror, file, info))
        }
        _state.value = if (ok) UpdateState.ReadyToInstall(info) else UpdateState.Failed(context.getString(R.string.update_download_failed))
    }

    private fun fetch(url: String, file: File, info: UpdateInfo): Boolean = try {
        val req = Request.Builder().url(url).header("User-Agent", "GTUnblock-Android").build()
        Http.download.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return false
            val body = resp.body ?: return false
            val total = body.contentLength().takeIf { it > 0 } ?: info.size
            file.outputStream().use { out ->
                val src = body.byteStream()
                val buf = ByteArray(65536)
                var done = 0L
                var lastEmit = 0L
                while (true) {
                    val n = src.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0 && done - lastEmit > 128 * 1024) {
                        lastEmit = done
                        _state.value = UpdateState.Downloading(info, (done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            total <= 0 || file.length() >= total
        }
    } catch (_: Exception) {
        false
    }

    fun install(context: Context) {
        val file = File(context.cacheDir, "update.apk")
        if (!file.exists()) return
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
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
            _state.value = UpdateState.Failed(e.message ?: context.getString(R.string.update_install_failed))
        }
    }

    fun setFailed(message: String) {
        _state.value = UpdateState.Failed(message)
    }
}
