package gpr.greentune.unblock.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    val mode: String = "vpn",
    val proxyPort: Int = 1080,
    val theme: String = "system",
    val tint: Long = 0xFF19A7CEL,
    val tintIntensity: Float = 0.6f,
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val shake: Boolean = true,
    val dns: String = "cloudflare",
    val blockQuic: Boolean = true,
    val ipv6: Boolean = false,
    val autostart: Boolean = true,
    val tuneOnNetworkChange: Boolean = true,
    val appsMode: String = "all",
    val apps: Set<String> = emptySet(),
    val beta: Boolean = false,
    val expert: Boolean = false,
    val customArgsEnabled: Boolean = false,
    val customArgs: String = "",
    val onboarded: Boolean = false,
    val shouldRun: Boolean = false,
    val lastUpdateCheck: Long = 0L,
    val lastPing: Long = 0L,
    val skippedVersion: String = "",
    val servicesSet: Boolean = false,
    val services: Set<String> = emptySet(),
    val customDomains: List<String> = emptyList(),
    val tilt: Boolean = true,
    val effects3d: Boolean = true,
    val notifiedVersion: String = "",
    val seenVersion: String = "",
    val tgBridge: Boolean = true,
    val tgLinked: Boolean = false,
    val lite: String = "auto",
    val autoRepair: Boolean = true,
    val udpBypass: Boolean = true,
    val proxyAuth: Boolean = false,
    val proxyUser: String = "",
    val proxyPass: String = "",
    val hostsMode: String = "all",
    val hostsList: String = "",
    val explained: Boolean = false,
    val profiles: Boolean = true,
)

object SettingsStore {
    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> get() = _state
    val value: AppSettings get() = _state.value

    fun init(context: Context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _state.value = load()
    }

    @Synchronized
    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(_state.value)
        _state.value = next
        save(next)
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            mode = sp.getString("mode", d.mode) ?: d.mode,
            proxyPort = sp.getInt("proxyPort", d.proxyPort),
            theme = sp.getString("theme", d.theme) ?: d.theme,
            tint = sp.getLong("tint", d.tint).let { if (it == 0xFF27B35AL && !sp.getBoolean("v2", false)) d.tint else it },
            tintIntensity = sp.getFloat("tintIntensity", d.tintIntensity),
            sound = sp.getBoolean("sound", d.sound),
            haptics = sp.getBoolean("haptics", d.haptics),
            shake = sp.getBoolean("shake", d.shake),
            dns = (sp.getString("dns", d.dns) ?: d.dns).let { if (it.contains('.')) d.dns else it },
            blockQuic = sp.getBoolean("blockQuic", d.blockQuic),
            ipv6 = sp.getBoolean("ipv6", d.ipv6),
            autostart = sp.getBoolean("autostart", d.autostart),
            tuneOnNetworkChange = sp.getBoolean("tuneOnNetworkChange", d.tuneOnNetworkChange),
            appsMode = sp.getString("appsMode", d.appsMode) ?: d.appsMode,
            apps = sp.getStringSet("apps", d.apps)?.toSet() ?: d.apps,
            beta = sp.getBoolean("beta", d.beta),
            expert = sp.getBoolean("expert", d.expert),
            customArgsEnabled = sp.getBoolean("customArgsEnabled", d.customArgsEnabled),
            customArgs = sp.getString("customArgs", d.customArgs) ?: d.customArgs,
            onboarded = sp.getBoolean("onboarded", d.onboarded),
            shouldRun = sp.getBoolean("shouldRun", d.shouldRun),
            lastUpdateCheck = sp.getLong("lastUpdateCheck", d.lastUpdateCheck),
            lastPing = sp.getLong("lastPing", d.lastPing),
            skippedVersion = sp.getString("skippedVersion", d.skippedVersion) ?: d.skippedVersion,
            servicesSet = sp.getBoolean("servicesSet", d.servicesSet),
            services = sp.getStringSet("services", d.services)?.toSet() ?: d.services,
            customDomains = (sp.getString("customDomains", "") ?: "").split('\n').filter { it.isNotBlank() },
            tilt = sp.getBoolean("tilt", d.tilt),
            effects3d = sp.getBoolean("effects3d", d.effects3d),
            notifiedVersion = sp.getString("notifiedVersion", "") ?: "",
            seenVersion = sp.getString("seenVersion", "") ?: "",
            tgBridge = sp.getBoolean("tgBridge", d.tgBridge),
            tgLinked = sp.getBoolean("tgLinked", d.tgLinked),
            lite = sp.getString("lite", d.lite) ?: d.lite,
            autoRepair = sp.getBoolean("autoRepair", d.autoRepair),
            udpBypass = sp.getBoolean("udpBypass", d.udpBypass),
            proxyAuth = sp.getBoolean("proxyAuth", d.proxyAuth),
            proxyUser = sp.getString("proxyUser", "") ?: "",
            proxyPass = sp.getString("proxyPass", "") ?: "",
            hostsMode = sp.getString("hostsMode", d.hostsMode) ?: d.hostsMode,
            hostsList = sp.getString("hostsList", "") ?: "",
            explained = sp.getBoolean("explained", d.explained),
            profiles = sp.getBoolean("profiles", d.profiles),
        )
    }

    private fun save(s: AppSettings) {
        sp.edit()
            .putString("mode", s.mode)
            .putInt("proxyPort", s.proxyPort)
            .putString("theme", s.theme)
            .putLong("tint", s.tint)
            .putBoolean("v2", true)
            .putFloat("tintIntensity", s.tintIntensity)
            .putBoolean("sound", s.sound)
            .putBoolean("haptics", s.haptics)
            .putBoolean("shake", s.shake)
            .putString("dns", s.dns)
            .putBoolean("blockQuic", s.blockQuic)
            .putBoolean("ipv6", s.ipv6)
            .putBoolean("autostart", s.autostart)
            .putBoolean("tuneOnNetworkChange", s.tuneOnNetworkChange)
            .putString("appsMode", s.appsMode)
            .putStringSet("apps", s.apps)
            .putBoolean("beta", s.beta)
            .putBoolean("expert", s.expert)
            .putBoolean("customArgsEnabled", s.customArgsEnabled)
            .putString("customArgs", s.customArgs)
            .putBoolean("onboarded", s.onboarded)
            .putBoolean("shouldRun", s.shouldRun)
            .putLong("lastUpdateCheck", s.lastUpdateCheck)
            .putLong("lastPing", s.lastPing)
            .putString("skippedVersion", s.skippedVersion)
            .putBoolean("servicesSet", s.servicesSet)
            .putStringSet("services", s.services)
            .putString("customDomains", s.customDomains.joinToString("\n"))
            .putBoolean("tilt", s.tilt)
            .putBoolean("effects3d", s.effects3d)
            .putBoolean("tgBridge", s.tgBridge)
            .putBoolean("tgLinked", s.tgLinked)
            .putString("lite", s.lite)
            .putBoolean("autoRepair", s.autoRepair)
            .putBoolean("udpBypass", s.udpBypass)
            .putBoolean("proxyAuth", s.proxyAuth)
            .putString("proxyUser", s.proxyUser)
            .putString("proxyPass", s.proxyPass)
            .putString("hostsMode", s.hostsMode)
            .putString("hostsList", s.hostsList)
            .putBoolean("explained", s.explained)
            .putBoolean("profiles", s.profiles)
            .putString("notifiedVersion", s.notifiedVersion)
            .putString("seenVersion", s.seenVersion)
            .apply()
    }
}
