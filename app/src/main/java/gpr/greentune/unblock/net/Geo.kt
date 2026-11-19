package gpr.greentune.unblock.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

data class GeoInfo(
    val ip: String,
    val asn: String?,
    val isp: String,
    val country: String,
    val region: String,
    val city: String,
) {
    val label: String
        get() = listOf(isp, asn?.let { "AS$it" }, city.ifBlank { region })
            .filter { !it.isNullOrBlank() }.joinToString(" · ")
}

object Geo {
    private val _last = MutableStateFlow<GeoInfo?>(null)
    val last: StateFlow<GeoInfo?> get() = _last

    suspend fun lookup(): GeoInfo? = withContext(Dispatchers.IO) {
        val g = ipinfo() ?: ipwho()
        if (g != null) _last.value = g
        g
    }

    private fun ipinfo(): GeoInfo? {
        val o = Http.getJson("https://ipinfo.io/json") ?: return null
        val org = o.optString("org")
        val asn = org.substringBefore(' ').removePrefix("AS").takeIf { it.isNotBlank() && it.all(Char::isDigit) }
        return GeoInfo(
            ip = o.optString("ip"),
            asn = asn,
            isp = if (asn != null) org.substringAfter(' ') else org,
            country = o.optString("country"),
            region = o.optString("region"),
            city = o.optString("city"),
        )
    }

    private fun ipwho(): GeoInfo? {
        val o = Http.getJson("https://ipwho.is/") ?: return null
        if (!o.optBoolean("success", false)) return null
        val c = o.optJSONObject("connection")
        return GeoInfo(
            ip = o.optString("ip"),
            asn = c?.optLong("asn")?.takeIf { it > 0 }?.toString(),
            isp = c?.optString("isp") ?: c?.optString("org") ?: "",
            country = o.optString("country_code"),
            region = o.optString("region"),
            city = o.optString("city"),
        )
    }
}
