package gpr.greentune.unblock.net

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import gpr.greentune.unblock.BuildConfig
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

object Device {
    @Volatile
    var id: String = ""
        private set
    @Volatile
    var ramMb: Long = 0
        private set
    @Volatile
    var lite: Boolean = false
    @Volatile
    var beta: Boolean = false
    @Volatile
    var busy: Boolean = false
    private var cm: ConnectivityManager? = null

    fun init(context: Context) {
        val sp = context.getSharedPreferences("device", Context.MODE_PRIVATE)
        var v = sp.getString("id", null)
        if (v.isNullOrBlank() || !Regex("^[A-Za-z0-9_-]{6,40}$").matches(v)) {
            val b = ByteArray(16)
            SecureRandom().nextBytes(b)
            v = b.joinToString("") { "%02x".format(it) }
            sp.edit().putString("id", v).apply()
        }
        id = v
        cm = context.getSystemService(ConnectivityManager::class.java)
        ramMb = try {
            val mi = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(mi)
            mi.totalMem / (1024 * 1024)
        } catch (_: Exception) {
            0
        }
    }

    val code: String
        get() {
            if (id.isEmpty()) return ""
            val h = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
            return "GLY-" + h.take(3).joinToString("") { "%02X".format(it) }
        }

    fun net(): String = try {
        val c = cm ?: return ""
        val caps = c.getNetworkCapabilities(c.activeNetwork) ?: return "none"
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cell"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "eth"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
    } catch (_: Exception) {
        ""
    }

    fun abi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: ""

    fun headers(): Map<String, String> = linkedMapOf(
        "X-Glyde-Id" to id,
        "X-Glyde-V" to BuildConfig.VERSION_NAME,
        "X-Glyde-Code" to BuildConfig.VERSION_CODE.toString(),
        "X-Glyde-Sdk" to Build.VERSION.SDK_INT.toString(),
        "X-Glyde-Model" to ascii(Build.MODEL),
        "X-Glyde-Maker" to ascii(Build.MANUFACTURER),
        "X-Glyde-Abi" to abi(),
        "X-Glyde-Lang" to Locale.getDefault().language,
        "X-Glyde-Ram" to ramMb.toString(),
        "X-Glyde-Lite" to if (lite) "1" else "0",
        "X-Glyde-Beta" to if (beta) "1" else "0",
        "X-Glyde-Net" to net(),
        "X-Glyde-Busy" to if (busy) "1" else "0",
    )

    private fun ascii(s: String?): String = (s ?: "").filter { it.code in 32..126 }.take(60)
}
