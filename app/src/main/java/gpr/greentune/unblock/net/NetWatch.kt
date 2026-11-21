package gpr.greentune.unblock.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import gpr.greentune.unblock.App
import gpr.greentune.unblock.core.Engine
import gpr.greentune.unblock.core.Tuner
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.data.TuneStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object NetWatch {
    private var pending: Job? = null
    private var lastNetwork: Network? = null

    fun start(context: Context) {
        val app = context.applicationContext
        App.scope.launch {
            while (true) {
                delay(15 * 60 * 1000L)
                try {
                    repair(app)
                } catch (_: Exception) {
                }
            }
        }
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (network == lastNetwork) return
                    lastNetwork = network
                    schedule(app)
                }
            })
        } catch (_: Exception) {
        }
    }

    private fun schedule(context: Context) {
        pending?.cancel()
        pending = App.scope.launch {
            delay(5000)
            handle(context)
        }
    }

    private suspend fun handle(context: Context) {
        val geo = Geo.lookup()
        if (!Engine.isActive()) return
        val s = SettingsStore.value
        if (!s.tuneOnNetworkChange || s.customArgsEnabled && s.expert) return
        val key = NetKey.key(context, geo?.asn)
        if (key == TuneStore.activeKey) return
        if (TuneStore.activate(key)) {
            Engine.reload(context)
        } else if (!Tuner.isRunning()) {
            Tuner.start(context, full = false)
        }
    }

    private suspend fun repair(context: Context) {
        val s = SettingsStore.value
        if (!s.autoRepair || s.customArgsEnabled && s.expert) return
        if (Engine.status.value != gpr.greentune.unblock.core.EngineStatus.On || Tuner.isRunning()) return
        val port = Live.socksPort.takeIf { it > 0 } ?: return
        val result = TuneStore.current.value ?: return
        val services = gpr.greentune.unblock.core.Presets.active(gpr.greentune.unblock.core.Presets.data.value, s).services.associateBy { it.id }
        val targets = result.choices.filter { it.ok }.mapNotNull { ch -> services[ch.serviceId]?.tests?.firstOrNull()?.let { ch.serviceId to it } }.take(12)
        if (targets.isEmpty()) return
        val failed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val client = gpr.greentune.unblock.core.Prober.client(port, 8000)
            targets.filter { (_, url) ->
                !gpr.greentune.unblock.core.Prober.probe(client, url).ok && !gpr.greentune.unblock.core.Prober.probe(client, url).ok
            }.map { it.first }
        }
        if (failed.isNotEmpty() && failed.size < targets.size && !Tuner.isRunning()) {
            Tuner.start(context, full = false, only = failed.toSet())
        }
    }
}
