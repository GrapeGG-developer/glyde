package gpr.greentune.unblock.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gpr.greentune.unblock.BuildConfig
import gpr.greentune.unblock.R
import gpr.greentune.unblock.core.Engine
import gpr.greentune.unblock.core.EngineStatus
import gpr.greentune.unblock.core.Tuner
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.system.Fx
import gpr.greentune.unblock.ui.aero.Aero
import gpr.greentune.unblock.ui.aero.AquaBackground
import gpr.greentune.unblock.ui.aero.TiltSensor
import gpr.greentune.unblock.ui.aero.BarItem
import gpr.greentune.unblock.ui.aero.Superbar
import gpr.greentune.unblock.ui.aero.glass
import gpr.greentune.unblock.ui.screens.AboutScreen
import gpr.greentune.unblock.ui.screens.AppsScreen
import gpr.greentune.unblock.ui.screens.DiagScreen
import gpr.greentune.unblock.ui.screens.ServicesScreen
import gpr.greentune.unblock.ui.screens.DonateScreen
import gpr.greentune.unblock.ui.screens.FeedbackScreen
import gpr.greentune.unblock.ui.screens.HomeScreen
import gpr.greentune.unblock.ui.screens.OnboardingScreen
import gpr.greentune.unblock.ui.screens.SettingsScreen
import gpr.greentune.unblock.ui.screens.TuneScreen
import gpr.greentune.unblock.ui.screens.UpdateDialog
import kotlinx.coroutines.launch

object Screens {
    const val HOME = "home"
    const val TUNE = "tune"
    const val APPS = "apps"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val DONATE = "donate"
    const val FEEDBACK = "feedback"
    const val SERVICES = "services"
    const val DIAG = "diag"
}

fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

@Composable
fun AppRoot(
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    requestNotifications: () -> Unit,
    showUpdate: Boolean,
    onUpdateShown: () -> Unit,
) {
    val c = Aero.colors
    val context = LocalContext.current
    val view = LocalView.current
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val status by Engine.status.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screens.HOME) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var lastStatus by remember { mutableStateOf<EngineStatus?>(null) }

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
            if (settings.sound) Fx.chime(context, true)
            if (settings.haptics) Fx.haptic(view, true)
        } else if (status == EngineStatus.Off && prev == EngineStatus.On) {
            if (settings.sound) Fx.chime(context, false)
            if (settings.haptics) Fx.haptic(view)
        } else if (status is EngineStatus.Error && prev !is EngineStatus.Error) {
            if (settings.sound) Fx.error(context)
            if (settings.haptics) Fx.haptic(view, true)
        }
    }

    BackHandler(enabled = drawer.isOpen || screen != Screens.HOME) {
        if (drawer.isOpen) scope.launch { drawer.close() } else screen = Screens.HOME
    }

    val go: (String) -> Unit = { id ->
        if (settings.haptics) Fx.haptic(view)
        screen = id
        scope.launch { drawer.close() }
    }

    TiltSensor(settings.tilt && settings.effects3d)
    Box(Modifier.fillMaxSize()) {
        AquaBackground(settings.effects3d, if (status == EngineStatus.On) 1f else 0f)
        if (!settings.onboarded) {
            OnboardingScreen(onDone = {
                requestNotifications()
                SettingsStore.update { it.copy(onboarded = true) }
            })
            return@Box
        }
        ModalNavigationDrawer(
            drawerState = drawer,
            scrimColor = Color.Black.copy(alpha = 0.35f),
            drawerContent = { Drawer(screen, go) }
        ) {
            Column(Modifier.fillMaxSize()) {
                TopBar(onMenu = {
                    if (settings.haptics) Fx.haptic(view)
                    scope.launch { drawer.open() }
                })
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f)) togetherWith fadeOut(tween(150))
                        },
                        label = "screen"
                    ) { s ->
                        when (s) {
                            Screens.TUNE -> TuneScreen()
                            Screens.SERVICES -> ServicesScreen(onOpenDiag = { go(Screens.DIAG) })
                            Screens.DIAG -> DiagScreen()
                            Screens.APPS -> AppsScreen()
                            Screens.SETTINGS -> SettingsScreen()
                            Screens.ABOUT -> AboutScreen(onDonate = { go(Screens.DONATE) }, onFeedback = { go(Screens.FEEDBACK) })
                            Screens.DONATE -> DonateScreen()
                            Screens.FEEDBACK -> FeedbackScreen()
                            else -> HomeScreen(onConnect = onConnect, onDisconnect = onDisconnect, onOpenTune = { go(Screens.TUNE) }, onOpenServices = { go(Screens.SERVICES) })
                        }
                    }
                }
                Superbar(
                    items = listOf(
                        BarItem(Screens.HOME, R.drawable.ic_menu_home, stringResource(R.string.nav_home)),
                        BarItem(Screens.SERVICES, R.drawable.ic_menu_services, stringResource(R.string.nav_services)),
                        BarItem(Screens.TUNE, R.drawable.ic_menu_tune, stringResource(R.string.nav_tune)),
                        BarItem(Screens.DIAG, R.drawable.ic_menu_diag, stringResource(R.string.nav_diag)),
                        BarItem(Screens.SETTINGS, R.drawable.ic_menu_settings, stringResource(R.string.nav_settings)),
                    ),
                    selected = screen,
                    onSelect = go
                )
            }
        }
        if (showUpdate) UpdateDialog(onDismiss = onUpdateShown)
    }
}

@Composable
private fun TopBar(onMenu: () -> Unit) {
    val c = Aero.colors
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (c.dark) 0.08f else 0.40f), Color.Transparent)))
            }
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onMenu),
            contentAlignment = Alignment.Center
        ) {
            Column {
                repeat(3) {
                    Box(
                        Modifier
                            .padding(vertical = 2.dp)
                            .size(20.dp, 2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .drawBehind { drawRect(c.text.copy(alpha = 0.85f)) }
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Image(painterResource(R.drawable.logo_glass), null, Modifier.size(30.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.app_name), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Drawer(current: String, go: (String) -> Unit) {
    val c = Aero.colors
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxHeight()
            .width(300.dp)
            .drawBehind {
                drawRect(if (c.dark) Color(0xFF07151D).copy(alpha = 0.94f) else Color(0xFFEEF8FC).copy(alpha = 0.95f))
                drawRect(
                    Brush.radialGradient(listOf(c.tint.copy(alpha = 0.30f), Color.Transparent), Offset(0f, 0f), size.width * 1.2f)
                )
                drawLine(Color.Black.copy(alpha = 0.4f), Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), 1f)
                drawLine(Color.White.copy(alpha = 0.5f), Offset(size.width - 2.5f, 0f), Offset(size.width - 2.5f, size.height), 1f)
            }
            .statusBarsPadding()
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Image(painterResource(R.drawable.logo_glass), null, Modifier.size(52.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(stringResource(R.string.app_name), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                Text("GREENTUNE · v${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = c.subText)
            }
        }
        Spacer(Modifier.height(14.dp))
        val items = listOf(
            Triple(Screens.HOME, R.drawable.ic_menu_home, R.string.nav_home),
            Triple(Screens.SERVICES, R.drawable.ic_menu_services, R.string.nav_services),
            Triple(Screens.TUNE, R.drawable.ic_menu_tune, R.string.nav_tune),
            Triple(Screens.DIAG, R.drawable.ic_menu_diag, R.string.diag_title),
            Triple(Screens.APPS, R.drawable.ic_menu_apps, R.string.nav_apps),
            Triple(Screens.SETTINGS, R.drawable.ic_menu_settings, R.string.nav_settings),
            Triple(Screens.FEEDBACK, R.drawable.ic_menu_feedback, R.string.nav_feedback),
            Triple(Screens.ABOUT, R.drawable.ic_menu_about, R.string.nav_about),
        )
        for ((id, icon, label) in items) DrawerItem(icon, stringResource(label), id == current) { go(id) }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().glass(c, 18.dp).clickable { go(Screens.DONATE) }.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_menu_donate), null, Modifier.size(40.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.donate_title), fontWeight = FontWeight.SemiBold, color = c.text)
                    Text(stringResource(R.string.donate_short), fontSize = 12.sp, color = c.subText)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DrawerItem(icon: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Aero.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(if (selected) Modifier.glass(c, 14.dp) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(icon), null, Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 15.5.sp, color = c.text, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
