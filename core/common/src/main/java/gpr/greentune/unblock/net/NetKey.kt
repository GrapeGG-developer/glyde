package gpr.greentune.unblock.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager

object NetKey {
    fun transport(context: Context): String {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "unknown"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val tm = context.getSystemService(TelephonyManager::class.java)
                "cell:" + (tm?.networkOperatorName ?: "")
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "eth"
            else -> "other"
        }
    }

    fun key(context: Context, asn: String?): String =
        if (asn != null) "AS$asn" else transport(context)
}
