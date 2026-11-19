package gpr.greentune.unblock.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import gpr.greentune.unblock.data.Presets
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
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val status by Engine.status.collectAsStateWithLifecycle()
    val tune by Tuner.progress.collectAsStateWithLifecycle()
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var showHow by remember { mutableStateOf(false) }
    val prog = remember { Animatable(0f) }
    var collapsedPx by remember { mutableIntStateOf(0) }

    val orbState = when (status) {
        EngineStatus.On -> if (tune.running) OrbState.Starting else OrbState.On
        EngineStatus.Starting -> OrbState.Starting
        is EngineStatus.Error -> OrbState.Error
        EngineStatus.Off -> OrbState.Off
    }

    fun expand(to: String) {
        if (settings.haptics) Fx.haptic(view)
        page = to
        scope.launch { prog.animateTo(1f, spring(0.86f, 420f)) }
    }

    fun collapse() {
        scope.launch {
            prog.animateTo(0f, spring(0.9f, 420f))
            page = null
        }
    }

    BackHandler(enabled = page != null || prog.value > 0.01f) {
        if (page != null && page != Pages.MENU) page = Pages.MENU else collapse()
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullH = maxHeight
        val fullW = maxWidth
        val topBarH = statusTop + 56.dp
        val collapsedH = if (collapsedPx > 0) with(density) { collapsedPx.toDp() } else 250.dp
        val smallOrb = 110.dp
        val expandedH = (fullH - topBarH - smallOrb - 4.dp).coerceAtLeast(collapsedH)
        val p = prog.value
        val sheetH = collapsedH + (expandedH - collapsedH) * p
        val region = (fullH - topBarH - sheetH).coerceAtLeast(60.dp)
        val bigOrb = minOf(330.dp, fullW * 0.86f, (fullH - topBarH - collapsedH) * 0.96f).coerceAtLeast(130.dp)
        val orbSize = minOf(Dp(lerp(bigOrb.value, smallOrb.value, p)), region)
        val orbY = topBarH + (region - orbSize) / 2
        val rangePx = with(density) { (expandedH - collapsedH).toPx() }.coerceAtLeast(1f)

        TopBar(statusTop, onHow = { showHow = true })

        Box(
            Modifier
                .offset { IntOffset(((fullW - orbSize) / 2).roundToPx(), orbY.roundToPx()) }
                .size(orbSize),
            contentAlignment = Alignment.Center
        ) {
            Orb3D(
                state = orbState,
                effects = Perf.effects(settings),
                description = stringResource(R.string.app_name),
                size = orbSize / 1.35f,
                onClick = {
                    if (settings.sound) Fx.tap(context)
                    if (Engine.isActive()) onDisconnect() else onConnect()
                }
            )
        }

        val dragState = rememberDraggableState { d ->
            scope.launch { prog.snapTo((prog.value - d / rangePx).coerceIn(0f, 1f)) }
        }
        val dragMod = Modifier.draggable(
            dragState,
            Orientation.Vertical,
            onDragStarted = { if (page == null) page = Pages.MENU },
            onDragStopped = { v ->
                val up = v < -900f || (v <= 900f && prog.value > 0.5f)
                if (up) {
                    if (page == null) page = Pages.MENU
                    prog.animateTo(1f, spring(0.86f, 420f), -v / rangePx)
                } else {
                    prog.animateTo(0f, spring(0.9f, 420f), -v / rangePx)
                    page = null
                }
            }
        )

        Box(
            Modifier
                .offset { IntOffset(0, (fullH - sheetH).roundToPx()) }
                .fillMaxWidth()
                .height(sheetH + 48.dp)
                .glass(c, 30.dp, glossFraction = 0.10f)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(sheetH)
                    .padding(bottom = navBottom),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val compact = page != null && page != Pages.MENU && p > 0.5f
                Column(
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .then(dragMod)
                        .onSizeChanged { if (!compact && prog.value < 0.01f && !prog.isRunning) collapsedPx = it.height + with(density) { navBottom.roundToPx() } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Handle(onClick = { if (prog.value > 0.5f) collapse() else expand(Pages.MENU) })
                    StatusBlock(compact, onHow = { showHow = true }, onOpen = ::expand)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .alpha(((p - 0.15f) / 0.85f).coerceIn(0f, 1f))
                ) {
                    if (p > 0.02f && page != null) {
                        AnimatedContent(
                            targetState = page ?: Pages.MENU,
                            transitionSpec = {
                                if (targetState == Pages.MENU) {
                                    (slideInHorizontally(tween(260)) { -it / 4 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(160)))
                                } else {
                                    (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut(tween(160)))
                                }
                            },
                            label = "page"
                        ) { pg ->
                            if (pg == Pages.MENU) Menu(onOpen = { page = it }, onHow = { showHow = true })
                            else SubPage(pg, onBack = { page = Pages.MENU }, onOpen = { page = it })
                        }
                    }
                }
            }
        }
    }

    if (showHow) HowItWorksDialog(onDismiss = { showHow = false })
}

@Composable
private fun TopBar(statusTop: Dp, onHow: () -> Unit) {
    val c = Aero.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = statusTop)
            .height(56.dp)
            .padding(horizontal = 14.dp),
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
private fun Handle(onClick: () -> Unit) {
    val c = Aero.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(44.dp, 5.dp)
                .clip(RoundedCornerShape(3.dp))
                .drawBehind { drawRect(c.text.copy(alpha = 0.28f)) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusBlock(compact: Boolean, onHow: () -> Unit, onOpen: (String) -> Unit) {
    val c = Aero.colors
    val context = LocalContext.current
    val view = LocalView.current
    val status by Engine.status.collectAsStateWithLifecycle()
    val since by Engine.since.collectAsStateWithLifecycle()
    val tune by Tuner.progress.collectAsStateWithLifecycle()
    val result by TuneStore.current.collectAsStateWithLifecycle()
    val presets by Presets.data.collectAsStateWithLifecycle()
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val tg by TgBridge.stats.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status) {
        while (status == EngineStatus.On) {
            now = System.currentTimeMillis()
            delay(1000)
        }
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
    val sub = when {
        status is EngineStatus.Error -> (status as EngineStatus.Error).message
        status == EngineStatus.On && tune.running -> stringResource(R.string.sheet_tuning)
        status == EngineStatus.On && settings.mode == "proxy" -> stringResource(R.string.status_proxy_on, settings.proxyPort) + " · " + formatDuration(maxOf(0, now - since))
        status == EngineStatus.On && result != null && active.isNotEmpty() -> stringResource(R.string.sheet_on, okCount, active.size) + " · " + formatDuration(maxOf(0, now - since))
        status == EngineStatus.On -> stringResource(R.string.sheet_on_plain) + " · " + formatDuration(maxOf(0, now - since))
        status == EngineStatus.Starting -> stringResource(R.string.sheet_starting)
        else -> stringResource(R.string.tap_to_connect)
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = if (compact) 18.sp else 26.sp, fontWeight = FontWeight.Light, color = c.text, maxLines = 1)
        Text(
            sub, fontSize = 13.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            color = if (status is EngineStatus.Error) c.danger else c.subText
        )
        AnimatedVisibility(!compact, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onHow)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusDot(c.ok, size = 7.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.not_vpn), fontSize = 12.5.sp, color = c.subText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                AnimatedVisibility(tune.running, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tune.label, fontSize = 12.5.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            AeroProgressBar(tune.fraction)
                        }
                        Spacer(Modifier.width(10.dp))
                        AeroButton(stringResource(R.string.cancel), onClick = { Tuner.cancel() })
                    }
                }
                if (!tune.running && tune.message != null) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tune.message ?: "", fontSize = 13.sp, color = c.text, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        AeroButton(stringResource(R.string.ok), onClick = { Tuner.clearMessage() })
                    }
                }
                if (status == EngineStatus.On && result != null && active.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val shown = active.take(6)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpen(Pages.SERVICES) }
                    ) {
                        for (s in shown) {
                            val ch = choices[s.id]
                            val col = when {
                                ch == null -> c.subText
                                ch.ok -> c.ok
                                ch.strategyId != null -> c.ok
                                else -> c.danger
                            }
                            Chip(s.name, col)
                        }
                        if (active.size > shown.size) Chip(stringResource(R.string.sheet_more, active.size - shown.size), null)
                    }
                }
                Spacer(Modifier.height(12.dp))
                val lite = Perf.lite(settings)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(
                        R.drawable.ic_v3_retune, stringResource(R.string.retune), Modifier.weight(1f),
                        enabled = !tune.running,
                    ) { Tuner.start(context, full = false) }
                    Tile(
                        R.drawable.ic_v3_telegram, "Telegram", Modifier.weight(1f),
                        active = tg.running, badge = settings.tgBridge && !settings.tgLinked,
                    ) { onOpen(Pages.TELEGRAM) }
                    Tile(R.drawable.ic_v3_lite, stringResource(R.string.lite_mode), Modifier.weight(1f), active = lite) {
                        if (settings.haptics) Fx.haptic(view)
                        SettingsStore.update { it.copy(lite = if (lite) "off" else "on") }
                    }
                    Tile(R.drawable.ic_menu_settings, stringResource(R.string.nav_more), Modifier.weight(1f)) { onOpen(Pages.MENU) }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
        if (compact) Spacer(Modifier.height(8.dp))
    }
}

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
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Image(painterResource(icon), null, Modifier.size(38.dp))
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
        Box(Modifier.height(32.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Text(
                label, fontSize = 11.5.sp, lineHeight = 14.sp, color = c.text, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Menu(onOpen: (String) -> Unit, onHow: () -> Unit) {
    val items = listOf(
        Triple(Pages.SERVICES, R.drawable.ic_menu_services, R.string.nav_services),
        Triple(Pages.TUNE, R.drawable.ic_menu_tune, R.string.nav_tune),
        Triple(Pages.DIAG, R.drawable.ic_v3_network, R.string.diag_title),
        Triple(Pages.TELEGRAM, R.drawable.ic_v3_telegram, R.string.tg_title),
        Triple(Pages.APPS, R.drawable.ic_menu_apps, R.string.nav_apps),
        Triple(Pages.SETTINGS, R.drawable.ic_v3_expert, R.string.nav_settings),
        Triple("how", R.drawable.ic_v3_how, R.string.how_it_works),
        Triple(Pages.FEEDBACK, R.drawable.ic_menu_feedback, R.string.nav_feedback),
        Triple(Pages.ABOUT, R.drawable.ic_v3_shield, R.string.nav_about),
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (row in items.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((id, icon, label) in row) {
                    Tile(icon, stringResource(label), Modifier.weight(1f)) { if (id == "how") onHow() else onOpen(id) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        DonateRow { onOpen(Pages.DONATE) }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun DonateRow(onClick: () -> Unit) {
    val c = Aero.colors
    Row(
        Modifier
            .fillMaxWidth()
            .glass(c, 18.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.ic_menu_donate), null, Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.donate_title), fontWeight = FontWeight.SemiBold, color = c.text, fontSize = 14.5.sp)
            Text(stringResource(R.string.donate_short), fontSize = 12.sp, color = c.subText)
        }
    }
}

@Composable
private fun SubPage(page: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val c = Aero.colors
    val title = stringResource(
        when (page) {
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
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).glass(c, 19.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(16.dp)) {
                    val w = size.width
                    val col = c.text
                    drawLine(col, Offset(w * 0.68f, w * 0.12f), Offset(w * 0.30f, w * 0.5f), 2.4.dp.toPx(), StrokeCap.Round)
                    drawLine(col, Offset(w * 0.30f, w * 0.5f), Offset(w * 0.68f, w * 0.88f), 2.4.dp.toPx(), StrokeCap.Round)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Light, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
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
