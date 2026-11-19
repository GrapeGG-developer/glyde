package gpr.greentune.unblock

import android.app.Application
import gpr.greentune.unblock.core.Presets
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.data.TuneStore
import gpr.greentune.unblock.net.NetWatch
import gpr.greentune.unblock.net.Device
import gpr.greentune.unblock.net.Live
import gpr.greentune.unblock.system.CrashGuard
import gpr.greentune.unblock.system.Telemetry
import gpr.greentune.unblock.vpn.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import gpr.greentune.unblock.update.Updater

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashGuard.install(this)
        SettingsStore.init(this)
        Device.init(this)
        Device.beta = SettingsStore.value.beta
        TuneStore.init(this)
        Presets.init(this)
        Notifications.createChannels(this)
        NetWatch.start(this)
        Live.start(this)
        if (SettingsStore.value.tgBridge) gpr.greentune.unblock.tg.TgBridge.start(this)
        reportUpdate()
        scope.launch {
            Telemetry.onAppStart(this@App)
            Presets.refresh(this@App)
        }
        scope.launch {
            while (true) {
                delay(60_000L)
                Updater.backgroundCheck(this@App)
            }
        }
    }

    private fun reportUpdate() {
        val sp = getSharedPreferences("device", MODE_PRIVATE)
        val last = sp.getString("lastVersion", null)
        if (last != null && last != BuildConfig.VERSION_NAME) Live.updateResult(last, BuildConfig.VERSION_NAME, "ok")
        if (last != BuildConfig.VERSION_NAME) sp.edit().putString("lastVersion", BuildConfig.VERSION_NAME).apply()
    }

    companion object {
        lateinit var instance: App
            private set
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
