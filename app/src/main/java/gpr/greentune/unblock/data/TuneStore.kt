package gpr.greentune.unblock.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ServiceChoice(
    val serviceId: String,
    val strategyId: String?,
    val direct: Boolean,
    val ok: Boolean,
    val ms: Long,
    val route: String = if (direct) "direct" else if (strategyId != null && ok) "dpi" else "none",
)

data class TuneResult(
    val networkKey: String,
    val label: String,
    val time: Long,
    val full: Boolean,
    val defaultStrategy: String?,
    val choices: List<ServiceChoice>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("key", networkKey)
        .put("label", label)
        .put("time", time)
        .put("full", full)
        .put("default", defaultStrategy ?: JSONObject.NULL)
        .put("choices", JSONArray().apply {
            choices.forEach {
                put(
                    JSONObject()
                        .put("s", it.serviceId)
                        .put("st", it.strategyId ?: JSONObject.NULL)
                        .put("d", it.direct)
                        .put("ok", it.ok)
                        .put("ms", it.ms)
                        .put("r", it.route)
                )
            }
        })

    companion object {
        fun fromJson(o: JSONObject): TuneResult {
            val arr = o.optJSONArray("choices") ?: JSONArray()
            val list = (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                val base = ServiceChoice(
                    serviceId = c.getString("s"),
                    strategyId = if (c.isNull("st")) null else c.getString("st"),
                    direct = c.optBoolean("d"),
                    ok = c.optBoolean("ok"),
                    ms = c.optLong("ms"),
                )
                c.optString("r").takeIf { it.isNotEmpty() }?.let { base.copy(route = it) } ?: base
            }
            return TuneResult(
                networkKey = o.getString("key"),
                label = o.optString("label"),
                time = o.optLong("time"),
                full = o.optBoolean("full"),
                defaultStrategy = if (o.isNull("default")) null else o.optString("default"),
                choices = list,
            )
        }
    }
}

object TuneStore {
    private lateinit var sp: SharedPreferences
    private val results = mutableMapOf<String, TuneResult>()
    private val _current = MutableStateFlow<TuneResult?>(null)
    val current: StateFlow<TuneResult?> get() = _current
    var activeKey: String = ""
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("tune", Context.MODE_PRIVATE)
        try {
            val all = JSONObject(sp.getString("results", "{}") ?: "{}")
            all.keys().forEach { k -> results[k] = TuneResult.fromJson(all.getJSONObject(k)) }
        } catch (_: Exception) {
        }
        activeKey = sp.getString("active", "") ?: ""
        _current.value = results[activeKey] ?: results.values.maxByOrNull { it.time }
    }

    @Synchronized
    fun save(result: TuneResult) {
        results[result.networkKey] = result
        activeKey = result.networkKey
        _current.value = result
        persist()
    }

    @Synchronized
    fun get(key: String): TuneResult? = results[key]

    @Synchronized
    fun activate(key: String): Boolean {
        val r = results[key] ?: return false
        activeKey = key
        _current.value = r
        persist()
        return true
    }

    @Synchronized
    fun setDefault(strategyId: String?) {
        val cur = _current.value ?: TuneResult("manual", "", System.currentTimeMillis(), false, null, emptyList())
        save(cur.copy(defaultStrategy = strategyId))
    }

    @Synchronized
    fun clear() {
        results.clear()
        _current.value = null
        activeKey = ""
        persist()
    }

    private fun persist() {
        val all = JSONObject()
        results.forEach { (k, v) -> all.put(k, v.toJson()) }
        sp.edit().putString("results", all.toString()).putString("active", activeKey).apply()
    }
}
