package gpr.greentune.unblock.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gpr.greentune.unblock.BuildConfig
import gpr.greentune.unblock.core.Engine
import gpr.greentune.unblock.core.EngineStatus
import gpr.greentune.unblock.core.Tuner
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.system.Fx
import gpr.greentune.unblock.system.Perf
import gpr.greentune.unblock.ui.aero.Aero
import gpr.greentune.unblock.ui.aero.AquaBackground
import gpr.greentune.unblock.ui.aero.Bubbles
import gpr.greentune.unblock.ui.aero.ProvideBackdrop
import gpr.greentune.unblock.ui.aero.backdropSource
import gpr.greentune.unblock.ui.aero.TiltSensor
import gpr.greentune.unblock.ui.aero.observeTouches
import gpr.greentune.unblock.ui.screens.GlydeHome
import gpr.greentune.unblock.ui.screens.OnboardingScreen
import gpr.greentune.unblock.ui.screens.UpdateDialog
import gpr.greentune.unblock.ui.screens.WhatsNewDialog

fun openUrl(context: android.content.Context, url: String): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

@Composable
fun AppRoot(
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    requestNotifications: () -> Unit,
    showUpdate: Boolean,
    onUpdateShown: () -> Unit,
    whatsNew: String? = null,
    onWhatsNewShown: () -> Unit = {},
) {
    val c = Aero.colors
    val context = LocalContext.current
    val view = LocalView.current
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val status by Engine.status.collectAsStateWithLifecycle()
    var lastStatus by remember { mutableStateOf<EngineStatus?>(null) }
    val lite = Perf.lite(settings)
    val effects = Perf.effects(settings)

    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        val ctl = WindowCompat.getInsetsController(window, view)
        ctl.isAppearanceLightStatusBars = !c.dark
        ctl.isAppearanceLightNavigationBars = !c.dark
    }

    val tuneState by Tuner.progress.collectAsStateWithLifecycle()
    var wasTuning by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Fx.preload(context) }
    LaunchedEffect(tuneState.running) {
        if (wasTuning && !tuneState.running && tuneState.message != null && settings.sound) Fx.done(context)
        wasTuning = tuneState.running
    }

    LaunchedEffect(status) {
        val prev = lastStatus
        lastStatus = status
        if (prev == null) return@LaunchedEffect
        if (status == EngineStatus.On && prev != EngineStatus.On) {
            if (settings.haptics) Fx.haptic(view, true)
        } else if (status == EngineStatus.Off && prev == EngineStatus.On) {
            if (settings.haptics) Fx.haptic(view)
        } else if (status is EngineStatus.Error && prev !is EngineStatus.Error) {
            if (settings.haptics) Fx.haptic(view, true)
        }
    }

    val energy = when {
        status == EngineStatus.On && tuneState.running -> 0.6f
        status == EngineStatus.On -> 1f
        status == EngineStatus.Starting -> 0.5f
        else -> 0f
    }

    TiltSensor(settings.tilt && !lite)
    ProvideBackdrop {
    Box(Modifier.fillMaxSize().observeTouches()) {
        Box(Modifier.fillMaxSize().backdropSource()) {
            AquaBackground(effects, if (status == EngineStatus.On) 1f else 0f)
            Bubbles(energy, lite, Modifier.fillMaxSize())
        }
        if (!settings.onboarded) {
            OnboardingScreen(onDone = {
                requestNotifications()
                SettingsStore.update { it.copy(onboarded = true, explained = true) }
            })
            return@Box
        }
        GlydeHome(onConnect = onConnect, onDisconnect = onDisconnect)
        if (whatsNew != null) WhatsNewDialog(BuildConfig.VERSION_NAME, whatsNew, onWhatsNewShown)
        else if (showUpdate) UpdateDialog(onDismiss = onUpdateShown)
    }
    }
}
