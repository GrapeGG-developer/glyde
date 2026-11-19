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
        val key = NetKey.key(context, geo)
        if (key == TuneStore.activeKey) return
        if (TuneStore.activate(key)) {
            Engine.reload(context)
        } else if (!Tuner.isRunning()) {
            Tuner.start(context, full = false)
        }
    }
}
