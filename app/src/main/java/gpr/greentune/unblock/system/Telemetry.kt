package gpr.greentune.unblock.system

import android.content.Context
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.net.Backend
import gpr.greentune.unblock.net.Geo
import org.json.JSONObject
import java.util.Locale

object Telemetry {
    suspend fun onAppStart(context: Context) {
        val crash = CrashGuard.file(context)
        if (crash.exists()) {
            val text = try {
                crash.readText().take(20000)
            } catch (_: Exception) {
                ""
            }
            if (text.isBlank() || Backend.crash(text)) crash.delete()
        }
        val s = SettingsStore.value
        val now = System.currentTimeMillis()
        if (now - s.lastPing < 20 * 3600 * 1000L) return
        val geo = Geo.last.value ?: Geo.lookup()
        val body = JSONObject()
            .put("lang", Locale.getDefault().language)
            .put("mode", s.mode)
            .put("asn", geo?.asn ?: JSONObject.NULL)
            .put("country", geo?.country ?: JSONObject.NULL)
            .put("expert", s.expert)
            .put("quic", s.blockQuic)
            .put("apps", s.appsMode)
        if (Backend.ping(body)) SettingsStore.update { it.copy(lastPing = now) }
    }
}
