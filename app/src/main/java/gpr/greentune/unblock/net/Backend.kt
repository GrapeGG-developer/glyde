package gpr.greentune.unblock.net

import android.os.Build
import gpr.greentune.unblock.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object Backend {
    private val base = BuildConfig.SERVER_URL.trimEnd('/')
    val repo: String = BuildConfig.GITHUB_REPO
    val repoUrl: String get() = "https://github.com/$repo"

    fun deviceInfo(): JSONObject = JSONObject()
        .put("v", BuildConfig.VERSION_NAME)
        .put("code", BuildConfig.VERSION_CODE)
        .put("sdk", Build.VERSION.SDK_INT)
        .put("model", Build.MODEL ?: "")
        .put("maker", Build.MANUFACTURER ?: "")
        .put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "")

    suspend fun presets(): String? = withContext(Dispatchers.IO) {
        Http.getText("$base/v2/presets")
            ?: Http.getText("https://raw.githubusercontent.com/$repo/main/presets/presets.json")
    }

    suspend fun best(asn: String?): List<String> = withContext(Dispatchers.IO) {
        if (asn.isNullOrBlank()) return@withContext emptyList()
        val o = Http.getJson("$base/v1/best?asn=" + URLEncoder.encode(asn, "UTF-8")) ?: return@withContext emptyList()
        val arr = o.optJSONArray("strategies") ?: JSONArray()
        (0 until arr.length()).map { arr.getString(it) }
    }

    suspend fun update(channel: String): JSONObject? = withContext(Dispatchers.IO) {
        Http.getJson("$base/v1/update?channel=$channel&abi=" + (Build.SUPPORTED_ABIS.firstOrNull() ?: ""))
    }

    suspend fun report(body: JSONObject): Boolean = withContext(Dispatchers.IO) {
        Http.postJson("$base/v1/report", body.put("device", deviceInfo()))
    }

    suspend fun ping(body: JSONObject): Boolean = withContext(Dispatchers.IO) {
        Http.postJson("$base/v1/ping", body.put("device", deviceInfo()))
    }

    suspend fun crash(trace: String): Boolean = withContext(Dispatchers.IO) {
        Http.postJson("$base/v1/crash", JSONObject().put("trace", trace).put("device", deviceInfo()))
    }

    suspend fun feedback(text: String, contact: String): Boolean = withContext(Dispatchers.IO) {
        Http.postJson(
            "$base/v1/feedback",
            JSONObject().put("text", text).put("contact", contact).put("device", deviceInfo())
        )
    }

    fun mirrorUrl(tag: String, asset: String): String = "$base/v1/apk/$tag/$asset"
}
