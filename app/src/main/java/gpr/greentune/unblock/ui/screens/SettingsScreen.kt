package gpr.greentune.unblock.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gpr.greentune.unblock.BuildConfig
import gpr.greentune.unblock.R
import gpr.greentune.unblock.core.Engine
import gpr.greentune.unblock.data.AppSettings
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.net.DnsProviders
import gpr.greentune.unblock.ui.aero.Aero
import gpr.greentune.unblock.ui.aero.AeroButton
import gpr.greentune.unblock.ui.aero.AeroSlider
import gpr.greentune.unblock.ui.aero.GlassPanel
import gpr.greentune.unblock.ui.aero.SectionTitle
import gpr.greentune.unblock.ui.aero.Segmented
import gpr.greentune.unblock.ui.aero.SettingRow
import gpr.greentune.unblock.ui.aero.ToggleRow
import gpr.greentune.unblock.ui.aero.AquaFx
import gpr.greentune.unblock.ui.aero.glass
import gpr.greentune.unblock.update.UpdateState
import gpr.greentune.unblock.update.Updater
import kotlinx.coroutines.launch

val tintPalette = listOf(
    0xFF19A7CEL, 0xFF27B35AL, 0xFF3D6FE0L, 0xFF7B4FD6L, 0xFFC2459BL,
    0xFFE04848L, 0xFFE98A2BL, 0xFFD8B21FL, 0xFF14A89AL, 0xFF6B7B86L,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val c = Aero.colors
    val context = LocalContext.current
    val s by SettingsStore.state.collectAsStateWithLifecycle()
    val upd by Updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var portText by remember { mutableStateOf(s.proxyPort.toString()) }

    fun change(restart: Boolean, f: (AppSettings) -> AppSettings) {
        SettingsStore.update(f)
        if (restart && Engine.isActive()) Engine.reload(context)
    }

    ScreenColumn(stringResource(R.string.nav_settings)) {
        SectionTitle(stringResource(R.string.set_connection))
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Segmented(
                    listOf("vpn" to stringResource(R.string.mode_vpn), "proxy" to stringResource(R.string.mode_proxy)),
                    s.mode,
                    { m ->
                        if (m != s.mode) {
                            val was = Engine.isActive()
                            if (was) Engine.stop(context)
                            SettingsStore.update { it.copy(mode = m) }
                        }
                    }
                )
                Text(
                    stringResource(if (s.mode == "vpn") R.string.mode_vpn_sub else R.string.mode_proxy_sub),
                    fontSize = 12.5.sp, color = c.subText
                )
                if (s.mode == "proxy") {
                    SettingRow(stringResource(R.string.proxy_port), "127.0.0.1") {
                        BasicTextField(
                            value = portText,
                            onValueChange = { v ->
                                portText = v.filter(Char::isDigit).take(5)
                                portText.toIntOrNull()?.takeIf { it in 1025..65535 }?.let { p -> change(true) { it.copy(proxyPort = p) } }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.size(90.dp, 40.dp).glass(c, 12.dp, glossFraction = 0.3f).padding(10.dp)
                        )
                    }
                }
                ToggleRow(stringResource(R.string.set_quic), stringResource(R.string.set_quic_sub), s.blockQuic, { v -> change(true) { it.copy(blockQuic = v) } })
                ToggleRow(stringResource(R.string.set_ipv6), stringResource(R.string.set_ipv6_sub), s.ipv6, { v -> change(true) { it.copy(ipv6 = v) } }, enabled = s.mode == "vpn")
                ToggleRow(stringResource(R.string.set_autostart), stringResource(R.string.set_autostart_sub), s.autostart, { v -> change(false) { it.copy(autostart = v) } })
                ToggleRow(stringResource(R.string.set_retune), stringResource(R.string.set_retune_sub), s.tuneOnNetworkChange, { v -> change(false) { it.copy(tuneOnNetworkChange = v) } })
                ToggleRow(stringResource(R.string.auto_repair), stringResource(R.string.auto_repair_sub), s.autoRepair, { v -> change(false) { it.copy(autoRepair = v) } })
                ToggleRow(stringResource(R.string.udp_bypass), stringResource(R.string.udp_bypass_sub), s.udpBypass, { v -> change(true) { it.copy(udpBypass = v) } })
                ToggleRow(stringResource(R.string.tg_set), stringResource(R.string.tg_set_sub), s.tgBridge, { v -> change(false) { it.copy(tgBridge = v) } })
                SettingRow(stringResource(R.string.set_always_on), stringResource(R.string.set_always_on_sub), onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                    }
                })
            }
        }

        if (s.mode == "vpn") {
            SectionTitle(stringResource(R.string.set_dns))
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Segmented(DnsProviders.all.map { it.id to it.name }, s.dns, { id -> change(true) { it.copy(dns = id) } })
                    Text(stringResource(R.string.set_dns_sub), fontSize = 12.5.sp, color = c.subText)
                }
            }
        }

        SectionTitle(stringResource(R.string.set_look))
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Segmented(
                    listOf(
                        "system" to stringResource(R.string.theme_system),
                        "light" to stringResource(R.string.theme_light),
                        "dark" to stringResource(R.string.theme_dark),
                    ),
                    s.theme,
                    { t -> change(false) { it.copy(theme = t) } }
                )
                Text(stringResource(R.string.set_tint), fontSize = 15.sp, color = c.text)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (col in tintPalette) Swatch(Color(col), s.tint == col) { change(false) { it.copy(tint = col) } }
                }
                Text(stringResource(R.string.set_tint_intensity), fontSize = 15.sp, color = c.text)
                AeroSlider(s.tintIntensity, { v -> SettingsStore.update { it.copy(tintIntensity = v) } })
                ToggleRow(
                    stringResource(R.string.set_effects),
                    stringResource(if (AquaFx.supported) R.string.set_effects_sub else R.string.set_effects_old),
                    s.effects3d && AquaFx.supported,
                    { v -> change(false) { it.copy(effects3d = v) } },
                    enabled = AquaFx.supported
                )
                ToggleRow(
                    stringResource(R.string.lite_mode),
                    if (s.lite == "auto" && gpr.greentune.unblock.system.Perf.weak) stringResource(R.string.lite_auto) else stringResource(R.string.lite_sub),
                    gpr.greentune.unblock.system.Perf.lite(s),
                    { v -> change(false) { it.copy(lite = if (v) "on" else "off") } }
                )
                ToggleRow(stringResource(R.string.set_tilt), stringResource(R.string.set_tilt_sub), s.tilt, { v -> change(false) { it.copy(tilt = v) } })
                ToggleRow(stringResource(R.string.set_sound), null, s.sound, { v -> change(false) { it.copy(sound = v) } })
                ToggleRow(stringResource(R.string.set_haptics), null, s.haptics, { v -> change(false) { it.copy(haptics = v) } })
                ToggleRow(stringResource(R.string.set_shake), stringResource(R.string.set_shake_sub), s.shake, { v -> change(false) { it.copy(shake = v) } })
            }
        }

        SectionTitle(stringResource(R.string.set_updates))
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow(stringResource(R.string.set_beta), stringResource(R.string.set_beta_sub), s.beta, { v -> change(false) { it.copy(beta = v) } })
                val status = when (val u = upd) {
                    UpdateState.Checking -> stringResource(R.string.update_checking)
                    UpdateState.UpToDate -> stringResource(R.string.update_latest)
                    is UpdateState.Available -> stringResource(R.string.update_available_title, u.info.version)
                    is UpdateState.Downloading -> stringResource(R.string.update_downloading, (u.progress * 100).toInt())
                    is UpdateState.ReadyToInstall -> stringResource(R.string.update_ready)
                    is UpdateState.Failed -> u.message
                    UpdateState.Idle -> ""
                }
                if (status.isNotBlank()) Text(status, fontSize = 13.sp, color = c.subText)
                AeroButton(stringResource(R.string.update_check), modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch { Updater.check(context, manual = true) }
                })
                if (upd is UpdateState.Available) {
                    AeroButton(stringResource(R.string.update_install), primary = true, modifier = Modifier.fillMaxWidth(), onClick = {
                        UpdateFlow.start(context, scope, (upd as UpdateState.Available).info)
                    })
                }
                if (upd is UpdateState.ReadyToInstall) {
                    AeroButton(stringResource(R.string.update_install), primary = true, modifier = Modifier.fillMaxWidth(), onClick = {
                        Updater.install(context)
                    })
                }
                val news = remember { Updater.changelogFor(context, BuildConfig.VERSION_NAME) }
                if (news != null) {
                    var showNews by remember { mutableStateOf(false) }
                    AeroButton(stringResource(R.string.whats_new), modifier = Modifier.fillMaxWidth(), onClick = { showNews = true })
                    if (showNews) WhatsNewDialog(BuildConfig.VERSION_NAME, news) { showNews = false }
                }
            }
        }

        SectionTitle(stringResource(R.string.set_advanced))
        GlassPanel(Modifier.fillMaxWidth()) {
            ToggleRow(stringResource(R.string.set_expert), stringResource(R.string.set_expert_sub), s.expert, { v ->
                change(true) { it.copy(expert = v, customArgsEnabled = if (v) it.customArgsEnabled else false) }
            })
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val a by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "sw")
    Canvas(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        val r = size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(lerp(color, Color.White, 0.55f), color, lerp(color, Color.Black, 0.25f)), Offset(r * 0.7f, r * 0.6f), r * 1.4f))
        drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent), 0f, r), r * 0.8f, Offset(r, r * 0.75f))
        drawCircle(Color.Black.copy(alpha = 0.35f), r - 1f, style = Stroke(2f))
        if (a > 0f) drawCircle(Color.White.copy(alpha = a), r - 4f, style = Stroke(3.5f * a))
    }
}
