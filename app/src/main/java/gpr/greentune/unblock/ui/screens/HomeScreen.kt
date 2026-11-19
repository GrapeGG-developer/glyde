package gpr.greentune.unblock.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gpr.greentune.unblock.R
import gpr.greentune.unblock.core.Engine
import gpr.greentune.unblock.core.EngineStatus
import gpr.greentune.unblock.core.Tuner
import gpr.greentune.unblock.core.Presets
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.data.TuneStore
import gpr.greentune.unblock.system.Fx
import gpr.greentune.unblock.system.Perf
import gpr.greentune.unblock.tg.TgBridge
import gpr.greentune.unblock.ui.aero.Aero
import gpr.greentune.unblock.ui.aero.AeroButton
import gpr.greentune.unblock.ui.aero.AeroProgressBar
import gpr.greentune.unblock.ui.aero.Orb3D
import gpr.greentune.unblock.ui.aero.OrbState
import gpr.greentune.unblock.ui.aero.StatusDot
import gpr.greentune.unblock.ui.aero.glass
import gpr.greentune.unblock.ui.openUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

object Pages {
    const val MENU = "menu"
    const val SERVICES = "services"
    const val TUNE = "tune"
    const val DIAG = "diag"
    const val APPS = "apps"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val DONATE = "donate"
    const val FEEDBACK = "feedback"
    const val TELEGRAM = "telegram"
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

@Composable
fun GlydeHome(onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val c = Aero.colors
    val context = LocalContext.current
    val view = LocalView.current
    val status by Engine.status.collectAsStateWithLifecycle()
    val since by Engine.since.collectAsStateWithLifecycle()
    val tune by Tuner.progress.collectAsStateWithLifecycle()
    val result by TuneStore.current.collectAsStateWithLifecycle()
    val presets by Presets.data.collectAsStateWithLifecycle()
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var showHow by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status) {
        while (status == EngineStatus.On) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val orbState = when (status) {
        EngineStatus.On -> if (tune.running) OrbState.Starting else OrbState.On
        EngineStatus.Starting -> OrbState.Starting
        is EngineStatus.Error -> OrbState.Error
        EngineStatus.Off -> OrbState.Off
    }
    val choices = result?.choices.orEmpty().associateBy { it.serviceId }
    val active = Presets.active(presets, settings).services
    val okCount = active.count { choices[it.id]?.let { ch -> ch.ok || ch.strategyId != null } == true }
    val title = when {
        status == EngineStatus.On && tune.running -> stringResource(R.string.status_tuning)
        status == EngineStatus.On -> stringResource(R.string.status_on)
        status == EngineStatus.Starting -> stringResource(R.string.status_connecting)
        status is EngineStatus.Error -> stringResource(R.string.status_error)
        else -> stringResource(R.string.status_off)
    }
    val timer = formatDuration(maxOf(0, now - since))
    val sub = when {
        status is EngineStatus.Error -> (status as EngineStatus.Error).message
        status == EngineStatus.On && tune.running -> stringResource(R.string.sheet_tuning)
        status == EngineStatus.On && settings.mode == "proxy" -> stringResource(R.string.status_proxy_on, settings.proxyPort) + " · " + timer
        status == EngineStatus.On && result != null && active.isNotEmpty() -> stringResource(R.string.sheet_on, okCount, active.size) + " · " + timer
        status == EngineStatus.On -> stringResource(R.string.sheet_on_plain) + " · " + timer
        status == EngineStatus.Starting -> stringResource(R.string.sheet_starting)
        else -> stringResource(R.string.tap_to_connect)
    }

    BackHandler(enabled = page != null) {
        page = if (page != Pages.MENU) Pages.MENU else null
    }

    val open: (String?) -> Unit = { p ->
        if (settings.haptics) Fx.haptic(view)
        page = p
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val orbSize = minOf(maxWidth * 0.62f, maxHeight * 0.36f, 300.dp)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopBar(onHow = { showHow = true })
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(orbSize * 1.2f), contentAlignment = Alignment.Center) {
                Orb3D(
                    state = orbState,
                    effects = Perf.effects(settings),
                    description = title,
                    size = orbSize / 1.35f * 1.2f,
                    onClick = {
                        if (settings.sound) Fx.tap(context)
                        if (Engine.isActive()) onDisconnect() else onConnect()
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 32.sp, fontWeight = FontWeight.Light, color = c.text, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(
                sub, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                color = if (status is EngineStatus.Error) c.danger else c.subText,
                modifier = Modifier.padding(horizontal = 28.dp)
            )
            AnimatedVisibility(tune.running, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Row(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 32.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) { AeroProgressBar(tune.fraction) }
                    Spacer(Modifier.width(10.dp))
                    AeroButton(stringResource(R.string.cancel), onClick = { Tuner.cancel() })
                }
            }
            if (!tune.running && tune.message != null) {
                Row(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tune.message ?: "", fontSize = 13.sp, color = c.text, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    AeroButton(stringResource(R.string.ok), onClick = { Tuner.clearMessage() })
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showHow = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(c.ok, size = 8.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.not_vpn), fontSize = 13.sp, color = c.subText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            MenuButton(badge = settings.tgBridge && !settings.tgLinked) { open(Pages.MENU) }
            Spacer(Modifier.height(18.dp))
        }

        AeroWindowHost(
            page = page,
            maxH = maxHeight,
            onClose = { page = null },
            onBack = { page = Pages.MENU },
        ) { pg ->
            if (pg == Pages.MENU) Menu(onOpen = { page = it })
            else PageContent(pg, onOpen = { page = it })
        }
    }

    if (showHow) HowItWorksDialog(onDismiss = { showHow = false })
}

@Composable
private fun TopBar(onHow: () -> Unit) {
    val c = Aero.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.logo_glass), null, Modifier.size(32.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.app_name), fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(40.dp)
                .glass(c, 20.dp)
                .clip(CircleShape)
                .clickable(onClick = onHow),
            contentAlignment = Alignment.Center
        ) {
            Text("?", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        }
    }
}

@Composable
private fun MenuButton(badge: Boolean, onClick: () -> Unit) {
    val c = Aero.colors
    Box {
        Row(
            Modifier
                .widthIn(min = 200.dp)
                .height(56.dp)
                .glass(c, 28.dp)
                .clip(RoundedCornerShape(28.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Canvas(Modifier.size(20.dp)) {
                val s = size.width
                val g = s * 0.14f
                val cell = (s - g) / 2
                for (ix in 0..1) for (iy in 0..1) {
                    drawRoundRect(
                        c.text.copy(alpha = 0.9f),
                        topLeft = Offset(ix * (cell + g), iy * (cell + g)),
                        size = androidx.compose.ui.geometry.Size(cell, cell),
                        cornerRadius = CornerRadius(cell * 0.3f)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.nav_menu), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 10.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .drawBehind { drawRect(c.warn) }
            )
        }
    }
}

@Composable
private fun AeroWindowHost(
    page: String?,
    maxH: Dp,
    onClose: () -> Unit,
    onBack: () -> Unit,
    content: @Composable (String) -> Unit,
) {
    val c = Aero.colors
    AnimatedVisibility(page != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = if (c.dark) 0.45f else 0.28f)) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
        )
    }
    var last by remember { mutableStateOf(Pages.MENU) }
    if (page != null) last = page
    AnimatedVisibility(
        page != null,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(200)) + scaleIn(spring(0.8f, 500f), initialScale = 0.9f),
        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.94f)
    ) {
        Box(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val pg = page ?: last
            val sub = pg != Pages.MENU
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxH * 0.88f)
                    .then(if (sub) Modifier.fillMaxHeight() else Modifier)
                    .aeroFrame(c)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            ) {
                WindowCaption(
                    title = pageTitle(pg),
                    showBack = sub,
                    onBack = onBack,
                    onClose = onClose
                )
                Box(
                    Modifier
                        .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                        .then(if (sub) Modifier.weight(1f) else Modifier)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .drawBehind {
                            drawRect(if (c.dark) Color(0xFF0B1E28).copy(alpha = 0.92f) else Color(0xFFF4FAFD).copy(alpha = 0.94f))
                        }
                ) {
                    AnimatedContent(
                        targetState = pg,
                        transitionSpec = {
                            if (targetState == Pages.MENU) {
                                (slideInHorizontally(tween(240)) { -it / 5 } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(tween(240)) { it / 5 } + fadeOut(tween(140)))
                            } else {
                                (slideInHorizontally(tween(240)) { it / 5 } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(tween(240)) { -it / 5 } + fadeOut(tween(140)))
                            }
                        },
                        label = "win"
                    ) { p -> content(p) }
                }
            }
        }
    }
}

private fun Modifier.aeroFrame(c: gpr.greentune.unblock.ui.aero.AeroColors): Modifier = this.drawBehind {
    val r = 16.dp.toPx()
    val cr = CornerRadius(r, r)
    drawRoundRect(Color.Black.copy(alpha = 0.35f), topLeft = Offset(0f, 6.dp.toPx()), size = size, cornerRadius = cr)
    drawRoundRect(
        Brush.verticalGradient(
            listOf(
                lerpC(c.tint, Color.White, if (c.dark) 0.05f else 0.55f).copy(alpha = 0.92f),
                lerpC(c.tint, Color.Black, if (c.dark) 0.55f else 0.05f).copy(alpha = 0.90f),
            )
        ),
        cornerRadius = cr
    )
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.0f)), 0f, 44.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(size.width, 44.dp.toPx()),
        cornerRadius = cr
    )
    drawRoundRect(Color.Black.copy(alpha = 0.55f), cornerRadius = cr, style = Stroke(1.dp.toPx()))
    drawRoundRect(
        Color.White.copy(alpha = 0.5f),
        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
        cornerRadius = CornerRadius(r - 1.dp.toPx()),
        style = Stroke(1.dp.toPx())
    )
}

private fun lerpC(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

@Composable
private fun WindowCaption(title: String, showBack: Boolean, onBack: () -> Unit, onClose: () -> Unit) {
    val c = Aero.colors
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            Box(
                Modifier.size(34.dp).glass(c, 17.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(14.dp)) {
                    val w = size.width
                    drawLine(Color.White, Offset(w * 0.68f, w * 0.1f), Offset(w * 0.28f, w * 0.5f), 2.4.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White, Offset(w * 0.28f, w * 0.5f), Offset(w * 0.68f, w * 0.9f), 2.4.dp.toPx(), StrokeCap.Round)
                }
            }
            Spacer(Modifier.width(10.dp))
        } else {
            Image(painterResource(R.drawable.logo_glass), null, Modifier.padding(start = 4.dp).size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            title, fontSize = 17.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 6f))
        )
        Box(
            Modifier
                .size(width = 46.dp, height = 26.dp)
                .clip(RoundedCornerShape(6.dp))
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color(0xFFF0A08C), Color(0xFFD9452B), Color(0xFFB3200A), Color(0xFFE0683A))))
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), 0f, size.height * 0.5f), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.5f))
                }
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(10.dp)) {
                val w = size.width
                drawLine(Color.White, Offset(0f, 0f), Offset(w, w), 2.2.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White, Offset(w, 0f), Offset(0f, w), 2.2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun pageTitle(page: String): String = stringResource(
    when (page) {
        Pages.MENU -> R.string.nav_menu
        Pages.SERVICES -> R.string.nav_services
        Pages.TUNE -> R.string.nav_tune
        Pages.DIAG -> R.string.diag_title
        Pages.APPS -> R.string.nav_apps
        Pages.SETTINGS -> R.string.nav_settings
        Pages.ABOUT -> R.string.nav_about
        Pages.DONATE -> R.string.donate_title
        Pages.FEEDBACK -> R.string.nav_feedback
        else -> R.string.tg_title
    }
)

@Composable
private fun Chip(text: String, dot: Color?) {
    val c = Aero.colors
    Row(
        Modifier.glass(c, 14.dp, glossFraction = 0.5f).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot != null) {
            StatusDot(dot, size = 8.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 12.5.sp, color = c.text, maxLines = 1)
    }
}

@Composable
fun Tile(
    icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    badge: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Aero.colors
    Column(
        modifier
            .glass(c, 18.dp)
            .drawBehind {
                if (active) drawRoundRect(c.tint.copy(alpha = 0.20f), cornerRadius = CornerRadius(18.dp.toPx()))
            }
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(top = 12.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            Image(painterResource(icon), null, Modifier.size(52.dp))
            if (badge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .drawBehind { drawRect(c.warn) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.height(34.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Text(
                label, fontSize = 12.5.sp, lineHeight = 15.sp, color = c.text, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Menu(onOpen: (String) -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val tune by Tuner.progress.collectAsStateWithLifecycle()
    val tg by TgBridge.stats.collectAsStateWithLifecycle()
    val lite = Perf.lite(settings)
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(R.drawable.ic_menu_services, stringResource(R.string.nav_services), Modifier.weight(1f)) { onOpen(Pages.SERVICES) }
            Tile(R.drawable.ic_v3_telegram, "Telegram", Modifier.weight(1f), active = tg.running, badge = settings.tgBridge && !settings.tgLinked) { onOpen(Pages.TELEGRAM) }
            Tile(R.drawable.ic_v3_retune, stringResource(R.string.retune), Modifier.weight(1f), enabled = !tune.running) { Tuner.start(context, full = false) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(R.drawable.ic_v3_lite, stringResource(R.string.lite_mode), Modifier.weight(1f), active = lite) {
                if (settings.haptics) Fx.haptic(view)
                SettingsStore.update { it.copy(lite = if (lite) "off" else "on") }
            }
            Tile(R.drawable.ic_v3_network, stringResource(R.string.diag_title), Modifier.weight(1f)) { onOpen(Pages.DIAG) }
            Tile(R.drawable.ic_menu_apps, stringResource(R.string.nav_apps), Modifier.weight(1f)) { onOpen(Pages.APPS) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(R.drawable.ic_v3_expert, stringResource(R.string.nav_settings), Modifier.weight(1f)) { onOpen(Pages.SETTINGS) }
            Tile(R.drawable.ic_menu_feedback, stringResource(R.string.nav_feedback), Modifier.weight(1f)) { onOpen(Pages.FEEDBACK) }
            Tile(R.drawable.ic_v3_shield, stringResource(R.string.nav_about), Modifier.weight(1f)) { onOpen(Pages.ABOUT) }
        }
        DonateRow { onOpen(Pages.DONATE) }
    }
}

@Composable
private fun DonateRow(onClick: () -> Unit) {
    val c = Aero.colors
    Row(
        Modifier
            .fillMaxWidth()
            .glass(c, 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.ic_menu_donate), null, Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.donate_title), fontWeight = FontWeight.SemiBold, color = c.text, fontSize = 14.5.sp)
            Text(stringResource(R.string.donate_short), fontSize = 12.sp, color = c.subText)
        }
    }
}

@Composable
private fun PageContent(page: String, onOpen: (String) -> Unit) {
    CompositionLocalProvider(LocalHideTitle provides true) {
        when (page) {
            Pages.SERVICES -> ServicesScreen(onOpenDiag = { onOpen(Pages.DIAG) })
            Pages.TUNE -> TuneScreen()
            Pages.DIAG -> DiagScreen()
            Pages.APPS -> AppsScreen()
            Pages.SETTINGS -> SettingsScreen()
            Pages.ABOUT -> AboutScreen(onDonate = { onOpen(Pages.DONATE) }, onFeedback = { onOpen(Pages.FEEDBACK) })
            Pages.DONATE -> DonateScreen()
            Pages.FEEDBACK -> FeedbackScreen()
            else -> TelegramScreen()
        }
    }
}

@Composable
fun TelegramScreen() {
    val c = Aero.colors
    val context = LocalContext.current
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val tg by TgBridge.stats.collectAsStateWithLifecycle()
    ScreenColumn(stringResource(R.string.tg_title)) {
        Column(
            Modifier.fillMaxWidth().glass(c, 22.dp).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(painterResource(R.drawable.ic_v3_telegram), null, Modifier.size(72.dp))
            Text(
                stringResource(if (tg.running) R.string.tg_ready else R.string.tg_link_sub),
                fontSize = 15.sp, color = c.text, textAlign = TextAlign.Center
            )
            if (tg.running) {
                Text(
                    "WS ${tg.ws} · TCP ${tg.tcp} · ${tg.active}/${tg.total} · ${formatBytes(tg.bytes)}",
                    fontSize = 12.5.sp, color = c.subText
                )
            }
            AeroButton(
                stringResource(R.string.tg_connect),
                primary = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (!tg.running) TgBridge.start(context)
                    if (openUrl(context, TgBridge.link(context))) {
                        SettingsStore.update { it.copy(tgLinked = true, tgBridge = true) }
                    } else {
                        Toast.makeText(context, R.string.tg_no_app, Toast.LENGTH_SHORT).show()
                    }
                }
            )
            Text(stringResource(R.string.tg_hint), fontSize = 12.5.sp, color = c.subText, textAlign = TextAlign.Center)
        }
        Column(Modifier.fillMaxWidth().glass(c, 22.dp).padding(horizontal = 14.dp, vertical = 4.dp)) {
            gpr.greentune.unblock.ui.aero.ToggleRow(
                stringResource(R.string.tg_set), stringResource(R.string.tg_set_sub), settings.tgBridge,
                onChange = { on ->
                    SettingsStore.update { it.copy(tgBridge = on) }
                    if (on && Engine.status.value == EngineStatus.On) TgBridge.start(context)
                    if (!on) TgBridge.stop()
                }
            )
        }
    }
}

private fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", b / 1073741824.0)
    b >= 1L shl 20 -> String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)
    b >= 1L shl 10 -> String.format(java.util.Locale.US, "%.0f KB", b / 1024.0)
    else -> "$b B"
}
