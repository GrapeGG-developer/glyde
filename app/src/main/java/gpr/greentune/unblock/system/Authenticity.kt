package gpr.greentune.unblock.system

import android.content.Context
import android.content.pm.PackageManager
import gpr.greentune.unblock.BuildConfig
import gpr.greentune.unblock.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

enum class BuildCheck { Checking, Genuine, Modified, Unknown, Dev }

object Authenticity {
    private const val CERT = "504E7F87A0394E35EE30CF142301594F7CF62F575AA002E153102501FF093790"
    private val _state = MutableStateFlow(BuildCheck.Checking)
    val state: StateFlow<BuildCheck> get() = _state
    var tag: String = ""
        private set

    private fun hex(b: ByteArray) = b.joinToString("") { "%02X".format(it) }

    fun certOk(context: Context): Boolean = try {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signers = info.signingInfo?.apkContentsSigners.orEmpty()
        signers.isNotEmpty() && signers.all { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) == CERT }
    } catch (_: Exception) {
        false
    }

    private fun apkSha(context: Context): String {
        val md = MessageDigest.getInstance("SHA-256")
        File(context.applicationInfo.sourceDir).inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return hex(md.digest()).lowercase()
    }

    suspend fun check(context: Context): BuildCheck = withContext(Dispatchers.IO) {
        if (_state.value != BuildCheck.Checking && _state.value != BuildCheck.Unknown) return@withContext _state.value
        val result = run {
            if (!certOk(context)) return@run BuildCheck.Modified
            val v = BuildConfig.VERSION_NAME
            if (v == "1.0.0" || v.contains("dev")) return@run BuildCheck.Dev
            tag = if (v.startsWith("Y") || v.count { it == '.' } >= 3) v else "v$v"
            val sha = try { apkSha(context) } catch (_: Exception) { return@run BuildCheck.Unknown }
            val sums = fetchSums(tag) ?: return@run BuildCheck.Unknown
            if (sums.containsValue(sha)) BuildCheck.Genuine else BuildCheck.Modified
        }
        _state.value = result
        result
    }

    private fun fetchSums(tag: String): Map<String, String>? {
        val base = BuildConfig.SERVER_URL.trimEnd('/')
        try {
            val o = Http.getJson("$base/v3/build?tag=$tag")?.optJSONObject("sums")
            if (o != null && o.length() > 0) return o.keys().asSequence().associateWith { o.getString(it).lowercase() }
        } catch (_: Exception) {
        }
        return try {
            val txt = Http.getText("https://github.com/${BuildConfig.GITHUB_REPO}/releases/download/$tag/SHA256SUMS.txt") ?: return null
            txt.lines().mapNotNull { l ->
                val m = Regex("^([0-9a-fA-F]{64})\\s+\\*?(.+)$").find(l.trim()) ?: return@mapNotNull null
                m.groupValues[2] to m.groupValues[1].lowercase()
            }.toMap().ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    fun releaseUrl(): String = "https://github.com/${BuildConfig.GITHUB_REPO}/releases/tag/$tag"
    fun actionsUrl(): String = "https://github.com/${BuildConfig.GITHUB_REPO}/actions"
}
