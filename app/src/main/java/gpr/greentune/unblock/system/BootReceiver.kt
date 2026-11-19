package gpr.greentune.unblock.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import gpr.greentune.unblock.core.Engine
import gpr.greentune.unblock.data.SettingsStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val s = SettingsStore.value
        if (!s.autostart || !s.shouldRun) return
        if (Engine.needsVpnPermission(context)) return
        try {
            Engine.start(context)
        } catch (_: Exception) {
        }
    }
}
