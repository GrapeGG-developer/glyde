package gpr.greentune.unblock.system

import android.os.Build
import gpr.greentune.unblock.data.AppSettings
import gpr.greentune.unblock.net.Device

object Perf {
    val weak: Boolean get() = (Device.ramMb in 1..3500) || Build.VERSION.SDK_INT < 33

    fun lite(s: AppSettings): Boolean = when (s.lite) {
        "on" -> true
        "off" -> false
        else -> weak
    }

    fun effects(s: AppSettings): Boolean = s.effects3d && !lite(s)
}
