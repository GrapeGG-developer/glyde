package gpr.greentune.unblock.core

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.data.TuneStore
import gpr.greentune.unblock.system.GtTileService
import gpr.greentune.unblock.system.OrbWidget
import gpr.greentune.unblock.vpn.GtVpnService
import gpr.greentune.unblock.vpn.ProxyService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class EngineStatus {
    data object Off : EngineStatus()
    data object Starting : EngineStatus()
    data object On : EngineStatus()
    data class Error(val message: String) : EngineStatus()
}

object Engine {
    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Off)
    val status: StateFlow<EngineStatus> get() = _status
    private val _since = MutableStateFlow(0L)
    val since: StateFlow<Long> get() = _since

    fun isActive(): Boolean = _status.value == EngineStatus.On || _status.value == EngineStatus.Starting

    fun setStatus(context: Context, status: EngineStatus) {
        if (status == EngineStatus.On && _status.value != EngineStatus.On) _since.value = System.currentTimeMillis()
        val prev = _status.value
        _status.value = status
        if (prev != status) gpr.greentune.unblock.system.Fx.status(context, prev, status)
        if (status != EngineStatus.On) gpr.greentune.unblock.net.Live.socksPort = 0
        if (status == EngineStatus.On && SettingsStore.value.tgBridge) gpr.greentune.unblock.tg.TgBridge.start(context)
        if (status == EngineStatus.Off || status is EngineStatus.Error) gpr.greentune.unblock.tg.TgBridge.stop()
        if (status == EngineStatus.On) Health.start(context)
        if (status == EngineStatus.Off || status is EngineStatus.Error) Health.stop()
        val st = when (status) {
            EngineStatus.Off -> "off"
            EngineStatus.Starting -> "starting"
            EngineStatus.On -> "on"
            is EngineStatus.Error -> "error"
        }
        if (status != EngineStatus.Starting) gpr.greentune.unblock.net.Live.status(st, try { currentArgs().joinToString(" ").take(380) } catch (_: Exception) { "" })
        OrbWidget.updateAll(context)
        GtTileService.requestUpdate(context)
    }

    fun needsVpnPermission(context: Context): Boolean =
        SettingsStore.value.mode == "vpn" && VpnService.prepare(context) != null

    fun currentArgs(): List<String> {
        val base = baseArgs()
        val s = SettingsStore.value
        if (!s.udpBypass || (s.expert && s.customArgsEnabled) || base.isEmpty()) return base
        return listOf("-Ku", "-a6", "-An") + base
    }

    private fun baseArgs(): List<String> {
        val s = SettingsStore.value
        val presets = Presets.active(Presets.data.value, s)
        if (s.expert && s.customArgsEnabled && s.customArgs.isNotBlank()) {
            return Args.tokenize(Args.withSni(s.customArgs, presets.fakeSni))
        }
        val r = TuneStore.current.value
        if (r == null) {
            return Args.compose(presets, emptyMap(), emptySet(), presets.strategies.firstOrNull()?.id)
        }
        val choices = r.choices.filter { !it.direct && it.strategyId != null }
            .associate { it.serviceId to it.strategyId!! }
        val direct = r.choices.filter { it.direct }.map { it.serviceId }.toSet()
        return Args.compose(presets, choices, direct, r.defaultStrategy)
    }

    fun start(context: Context) {
        SettingsStore.update { it.copy(shouldRun = true) }
        val cls = if (SettingsStore.value.mode == "proxy") ProxyService::class.java else GtVpnService::class.java
        val intent = Intent(context, cls).setAction(GtVpnService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        SettingsStore.update { it.copy(shouldRun = false) }
        val vpn = GtVpnService.instance
        val proxy = ProxyService.instance
        vpn?.shutdown()
        proxy?.shutdown()
        if (vpn == null && proxy == null) setStatus(context, EngineStatus.Off)
    }

    fun toggle(context: Context) {
        if (isActive()) stop(context) else start(context)
    }

    fun reload(context: Context) {
        GtVpnService.instance?.reload()
        ProxyService.instance?.reload()
    }
}
