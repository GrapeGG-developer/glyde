package gpr.greentune.unblock.core

import android.content.Context
import gpr.greentune.unblock.App
import gpr.greentune.unblock.data.TuneStore
import gpr.greentune.unblock.net.Live
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HealthState(val lastCheck: Long = 0L, val healed: Int = 0, val failing: Set<String> = emptySet())

object Health {
    private const val FIRST_DELAY = 90_000L
    private const val PERIOD = 7 * 60_000L
    private const val HEAL_COOLDOWN = 60 * 60_000L
    private var job: Job? = null
    private val strikes = HashMap<String, Int>()
    private val lastHeal = HashMap<String, Long>()
    private val _state = MutableStateFlow(HealthState())
    val state: StateFlow<HealthState> get() = _state

    @Synchronized
    fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = App.scope.launch {
            delay(6_000L)
            try {
                repairForced(app)
            } catch (_: Exception) {
            }
            delay(FIRST_DELAY - 6_000L)
            while (true) {
                try {
                    check(app)
                } catch (_: Exception) {
                }
                delay(PERIOD)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        strikes.clear()
    }

    private fun repairForced(context: Context) {
        if (Engine.status.value != EngineStatus.On || Tuner.isRunning()) return
        val result = TuneStore.current.value ?: return
        val now = System.currentTimeMillis()
        val ids = Presets.active().services.filter { it.force }.map { it.id }.filter { id ->
            result.choices.none { it.serviceId == id && !it.direct && it.ok } && now - (lastHeal[id] ?: 0L) > HEAL_COOLDOWN
        }.toSet()
        if (ids.isEmpty()) return
        ids.forEach { lastHeal[it] = now }
        Logs.append(context, "health: re-tune " + ids.joinToString(","))
        Tuner.start(context, false, ids)
    }

    private suspend fun check(context: Context) {
        if (Engine.status.value != EngineStatus.On || Tuner.isRunning()) return
        val port = Live.socksPort
        if (port <= 0) return
        val presets = Presets.active()
        val result = TuneStore.current.value ?: return
        val targets = result.choices.filter { it.ok && it.route == "dpi" }.mapNotNull { presets.service(it.serviceId) }
        if (targets.isEmpty()) return
        val client = Prober.client(port, 7000)
        val bad = coroutineScope {
            targets.map { s ->
                async(Dispatchers.IO) {
                    val url = s.tests.firstOrNull() ?: return@async null
                    val ok = Prober.probe(client, url, 64L * 1024).ok || Prober.probe(client, url, 64L * 1024).ok
                    if (ok) null else s.id
                }
            }.awaitAll().filterNotNull().toSet()
        }
        for (s in targets) if (s.id !in bad) strikes.remove(s.id)
        for (id in bad) strikes[id] = (strikes[id] ?: 0) + 1
        val now = System.currentTimeMillis()
        val heal = bad.filter { (strikes[it] ?: 0) >= 2 && now - (lastHeal[it] ?: 0L) > HEAL_COOLDOWN }.toSet()
        _state.value = _state.value.copy(lastCheck = now, failing = bad)
        if (heal.isNotEmpty() && Engine.status.value == EngineStatus.On && !Tuner.isRunning()) {
            heal.forEach {
                lastHeal[it] = now
                strikes.remove(it)
            }
            Logs.append(context, "health: re-tune " + heal.joinToString(","))
            _state.value = _state.value.copy(healed = _state.value.healed + heal.size)
            Tuner.start(context, false, heal)
        }
    }
}
