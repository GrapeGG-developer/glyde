package gpr.greentune.unblock.core

import android.content.Context
import gpr.greentune.unblock.net.Backend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Service(
    val id: String,
    val name: String,
    val color: Long,
    val domains: List<String>,
    val ipset: List<String>,
    val tests: List<String>,
)

data class Strategy(val id: String, val args: String)

data class PresetData(
    val version: Int,
    val fakeSni: String,
    val services: List<Service>,
    val generalTests: List<String>,
    val strategies: List<Strategy>,
) {
    fun service(id: String): Service? = services.firstOrNull { it.id == id }
    fun strategy(id: String?): Strategy? = if (id == null) null else strategies.firstOrNull { it.id == id }
}

object Presets {
    private val _data = MutableStateFlow(PresetData(0, "google.com", emptyList(), emptyList(), emptyList()))
    val data: StateFlow<PresetData> get() = _data

    private fun cacheFile(context: Context) = File(context.filesDir, "presets.json")

    fun init(context: Context) {
        val bundled = try {
            parse(context.assets.open("presets.json").bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            null
        }
        val cached = try {
            val f = cacheFile(context)
            if (f.exists()) parse(f.readText()) else null
        } catch (_: Exception) {
            null
        }
        val best = listOfNotNull(bundled, cached).maxByOrNull { it.version }
        if (best != null) _data.value = best
    }

    suspend fun refresh(context: Context): Boolean {
        val text = Backend.presets() ?: return false
        val parsed = try {
            parse(text)
        } catch (_: Exception) {
            return false
        }
        if (parsed.version > _data.value.version && parsed.strategies.isNotEmpty() && parsed.services.isNotEmpty()) {
            cacheFile(context).writeText(text)
            _data.value = parsed
            return true
        }
        return false
    }

    fun parse(text: String): PresetData {
        val o = JSONObject(text)
        val services = o.getJSONArray("services").let { arr ->
            (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                Service(
                    id = s.getString("id"),
                    name = s.getString("name"),
                    color = parseColor(s.optString("color", "#27B35A")),
                    domains = s.optJSONArray("domains").strings(),
                    ipset = s.optJSONArray("ipset").strings(),
                    tests = s.optJSONArray("tests").strings(),
                )
            }
        }
        val strategies = o.getJSONArray("strategies").let { arr ->
            (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                Strategy(s.getString("id"), s.getString("args"))
            }
        }
        return PresetData(
            version = o.optInt("version", 0),
            fakeSni = o.optString("fakeSni", "google.com"),
            services = services,
            generalTests = o.optJSONArray("generalTests").strings(),
            strategies = strategies,
        )
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private fun parseColor(hex: String): Long = try {
        val h = hex.removePrefix("#")
        val v = h.toLong(16)
        if (h.length <= 6) v or 0xFF000000L else v
    } catch (_: Exception) {
        0xFF27B35AL
    }
}
