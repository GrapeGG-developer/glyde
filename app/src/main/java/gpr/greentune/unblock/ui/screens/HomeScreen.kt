package gpr.greentune.unblock.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gpr.greentune.unblock.R
import gpr.greentune.unblock.core.Engine
import gpr.greentune.unblock.core.EngineStatus
import gpr.greentune.unblock.core.Presets
import gpr.greentune.unblock.core.Tuner
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.data.TuneStore
import gpr.greentune.unblock.net.Geo
import gpr.greentune.unblock.ui.aero.Aero
import gpr.greentune.unblock.ui.aero.AeroButton
import gpr.greentune.unblock.ui.aero.AeroProgressBar
import gpr.greentune.unblock.ui.aero.GlassPanel
import gpr.greentune.unblock.ui.aero.Orb
import gpr.greentune.unblock.ui.aero.OrbState
import gpr.greentune.unblock.ui.aero.StatusDot
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(onConnect: () -> Unit, onDisconnect: () -> Unit, onOpenTune: () -> Unit) {
    val c = Aero.colors
    val context = LocalContext.current
    val status by Engine.status.collectAsStateWithLifecycle()
    val since by Engine.since.collectAsStateWithLifecycle()
    val tune by Tuner.progress.collectAsStateWithLifecycle()
    val result by TuneStore.current.collectAsStateWithLifecycle()
    val presets by Presets.data.collectAsStateWithLifecycle()
    val geo by Geo.last.collectAsStateWithLifecycle()
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(status) {
        while (status == EngineStatus.On) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    LaunchedEffect(Unit) { if (Geo.last.value == null) Geo.lookup() }

    val orbState = when (status) {
        EngineStatus.On -> if (tune.running) OrbState.Starting else OrbState.On
        EngineStatus.Starting -> OrbState.Starting
        is EngineStatus.Error -> OrbState.Error
        EngineStatus.Off -> OrbState.Off
    }
    val title = when {
        status == EngineStatus.On && tune.running -> stringResource(R.string.status_tuning)
        status == EngineStatus.On -> stringResource(R.string.status_on)
        status == EngineStatus.Starting -> stringResource(R.string.status_connecting)
        status is EngineStatus.Error -> stringResource(R.string.status_error)
        else -> stringResource(R.string.status_off)
    }

    ScreenColumn(null) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(4.dp))
            Orb(
                state = orbState,
                description = title,
                onClick = {
                    if (Engine.isActive()) onDisconnect() else onConnect()
                }
            )
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Light, color = c.text)
            val sub = when {
                status == EngineStatus.On && settings.mode == "proxy" ->
                    stringResource(R.string.status_proxy_on, settings.proxyPort) + " · " + formatDuration(now - since)
                status == EngineStatus.On -> formatDuration(maxOf(0, now - since))
                status is EngineStatus.Error -> (status as EngineStatus.Error).message
                status == EngineStatus.Off -> stringResource(R.string.tap_to_connect)
                else -> stringResource(R.string.please_wait)
            }
            Text(sub, fontSize = 14.sp, color = if (status is EngineStatus.Error) c.danger else c.subText, textAlign = TextAlign.Center)
        }

        AnimatedVisibility(tune.running, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            GlassPanel(Modifier.fillMaxWidth()) {
                Column {
                    Text(tune.label, fontSize = 14.sp, color = c.text)
                    Spacer(Modifier.height(8.dp))
                    AeroProgressBar(tune.fraction)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(if (tune.full) R.string.tune_full_running else R.string.tune_quick_running),
                            fontSize = 12.sp, color = c.subText, modifier = Modifier.weight(1f)
                        )
                        AeroButton(stringResource(R.string.cancel), onClick = { Tuner.cancel() })
                    }
                }
            }
        }

        if (!tune.running && tune.message != null) {
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tune.message ?: "", fontSize = 14.sp, color = c.text, modifier = Modifier.weight(1f))
                    AeroButton(stringResource(R.string.ok), onClick = { Tuner.clearMessage() })
                }
            }
        }

        GlassPanel(Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.services), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
                    Text(geo?.label ?: result?.label ?: "", fontSize = 12.sp, color = c.subText)
                }
                Spacer(Modifier.height(10.dp))
                val choices = result?.choices.orEmpty().associateBy { it.serviceId }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in presets.services) {
                        val ch = choices[s.id]
                        val color = when {
                            ch == null -> c.subText
                            ch.ok -> c.ok
                            ch.strategyId != null -> c.warn
                            else -> c.danger
                        }
                        ServiceChip(s.name, Color(s.color), color)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AeroButton(
                        stringResource(R.string.tune_quick),
                        onClick = { Tuner.start(context, full = false) },
                        primary = true,
                        enabled = !tune.running,
                        modifier = Modifier.weight(1f)
                    )
                    AeroButton(stringResource(R.string.details), onClick = onOpenTune, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ServiceChip(name: String, brand: Color, state: Color) {
    val c = Aero.colors
    Box(Modifier.then(Modifier).padding(0.dp)) {
        GlassPanel(corner = 16.dp, padding = 0.dp) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(state)
                Spacer(Modifier.width(6.dp))
                Text(name, fontSize = 13.sp, color = c.text)
            }
        }
    }
}
