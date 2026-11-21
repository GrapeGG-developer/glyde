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
import gpr.greentune.air.Air
import gpr.greentune.air.AirButton
import gpr.greentune.air.AirProgressBar
import gpr.greentune.air.Orb
import gpr.greentune.air.OrbState
import gpr.greentune.air.StatusDot
import gpr.greentune.air.glass
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import gpr.greentune.air.AirSpec
import gpr.greentune.air.backdropBlur
import gpr.greentune.air.backdropSource
import gpr.greentune.air.pressBounce
import gpr.greentune.air.stage
import gpr.greentune.air.staggerIn
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
    val c = Air.colors
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

    val lite = Perf.lite(settings)
    val winOpen = page != null
    val homeScale by animateFloatAsState(if (winOpen) 0.93f else 1f, spring(dampingRatio = 0.8f, stiffness = 260f), label = "hs")
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val intro by animateFloatAsState(if (appeared) 1f else 0f, tween(if (lite) 200 else 650, easing = FastOutSlowInEasing), label = "in")
    val speed by gpr.greentune.unblock.system.Traffic.speed.collectAsStateWithLifecycle()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val orbSize = minOf(maxWidth * 0.62f, maxHeight * 0.36f, 300.dp)
        Column(
            Modifier
                .fillMaxSize()
                .backdropSource()
                .graphicsLayer {
                    scaleX = homeScale
                    scaleY = homeScale
                    rotationX = (1f - homeScale) / 0.07f * 6f
                    cameraDistance = 18f * density
                }
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopBar(onHow = { showHow = true }, modifier = Modifier.graphicsLayer {
                val k = stage(intro, 0f, 0.5f)
                alpha = k
                translationY = (1f - k) * -24.dp.toPx()
            })
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(orbSize * 1.2f).graphicsLayer {
                    val k = stage(intro, 0.05f, 0.75f)
                    alpha = k
                    val sc = 0.6f + 0.4f * androidx.compose.animation.core.EaseOutBack.transform(k)
                    scaleX = sc
                    scaleY = sc
                },
                contentAlignment = Alignment.Center
            ) {
                Orb(
                    state = orbState,
                    level = if (status == EngineStatus.On) speed.level else 0f,
                    description = title,
                    size = orbSize / 1.35f * 1.2f,
                    onClick = {
                        if (settings.sound) Fx.tap(context)
                        if (settings.haptics) Fx.haptic(view)
                        if (Engine.isActive()) onDisconnect() else onConnect()
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.graphicsLayer {
                    val k = stage(intro, 0.35f, 0.9f)
                    alpha = k
                    translationY = (1f - k) * 30.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInVertically(spring(dampingRatio = 0.7f, stiffness = 320f)) { it / 2 }) togetherWith
                            (fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 2 })
                    },
                    label = "title"
                ) { tt ->
                    Text(tt, fontSize = 32.sp, fontWeight = FontWeight.Light, color = c.text, maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                AnimatedContent(
                    targetState = sub,
                    transitionSpec = { fadeIn(tween(320, 80)) togetherWith fadeOut(tween(140)) },
                    label = "sub"
                ) { ss ->
                    Text(
                        ss, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        color = if (status is EngineStatus.Error) c.danger else c.subText,
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                }
                AnimatedVisibility(status == EngineStatus.On && !tune.running, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SpeedChip("↓", gpr.greentune.unblock.system.Traffic.format(speed.down), c.ok)
                        Spacer(Modifier.width(8.dp))
                        SpeedChip("↑", gpr.greentune.unblock.system.Traffic.format(speed.up), c.tint)
                    }
                }
            }
            AnimatedVisibility(tune.running, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Row(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 32.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) { AirProgressBar(tune.fraction) }
                    Spacer(Modifier.width(10.dp))
                    AirButton(stringResource(R.string.cancel), onClick = { Tuner.cancel() })
                }
            }
            AnimatedVisibility(!tune.running && tune.message != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Row(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tune.message ?: "", fontSize = 13.sp, color = c.text, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    AirButton(stringResource(R.string.ok), onClick = { Tuner.clearMessage() })
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.graphicsLayer {
                val k = stage(intro, 0.55f, 1f)
                alpha = k
                translationY = (1f - k) * 40.dp.toPx()
            }) {
                MenuButton(badge = settings.tgBridge && !settings.tgLinked) { open(Pages.MENU) }
            }
            Spacer(Modifier.height(18.dp))
        }

        AirWindowHost(
            page = page,
            maxH = maxHeight,
            lite = lite,
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
private fun versionCaption(): String {
    val v = gpr.greentune.unblock.BuildConfig.VERSION_NAME
    val n = gpr.greentune.unblock.BuildConfig.CODENAME
    return if (n.isBlank() || v.contains(n)) v else "$v $n"
}

@Composable
private fun SpeedChip(arrow: String, value: String, color: Color) {
    val c = Air.colors
    Row(
        Modifier.glass(c, 14.dp, glossFraction = 0.5f).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(arrow, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.width(6.dp))
        Text(value, fontSize = 13.sp, color = c.text, maxLines = 1)
    }
}

@Composable
private fun TopBar(onHow: () -> Unit, modifier: Modifier = Modifier) {
    val c = Air.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.logo_glass), null, Modifier.size(36.dp))
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), fontSize = 21.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                Spacer(Modifier.width(7.dp))
                DpiBadge()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                gpr.greentune.unblock.ui.brand.CosmicMark(gpr.greentune.unblock.BuildConfig.CODENAME_KIND, Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    versionCaption(), fontSize = 10.5.sp, lineHeight = 12.sp, color = c.subText, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, letterSpacing = 0.3.sp
                )
            }
        }
        Spacer(Modifier.weight(1f))
        val src = remember { MutableInteractionSource() }
        Box(
            Modifier
                .size(40.dp)
                .pressBounce(src, 0.88f)
                .glass(c, 20.dp)
                .clip(CircleShape)
                .clickable(interactionSource = src, indication = null, onClick = onHow),
            contentAlignment = Alignment.Center
        ) {
            Text("?", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        }
    }
}

@Composable
private fun DpiBadge() {
    Box(
        Modifier
            .drawBehind {
                val cr = CornerRadius(6.dp.toPx())
                drawRoundRect(Color.Black.copy(alpha = 0.35f), topLeft = Offset(0f, 1.5.dp.toPx()), size = size, cornerRadius = cr)
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFE07A), Color(0xFFFFA000), Color(0xFFE65100))), cornerRadius = cr)
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.1f)), 0f, size.height * 0.5f),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.5f), cornerRadius = cr
                )
                drawRoundRect(Color(0xFF5A2300), cornerRadius = cr, style = Stroke(1.2.dp.toPx()))
            }
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            "DPI", fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = 1.2.sp,
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0xFF6B2A00), Offset(0f, 1.5f), 2f))
        )
    }
}

@Composable
private fun MenuButton(badge: Boolean, onClick: () -> Unit) {
    val c = Air.colors
    val src = remember { MutableInteractionSource() }
    Box(Modifier.pressBounce(src, 0.93f)) {
        Row(
            Modifier
                .widthIn(min = 200.dp)
                .height(56.dp)
                .glass(c, 28.dp)
                .clip(RoundedCornerShape(28.dp))
                .clickable(interactionSource = src, indication = null, onClick = onClick)
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

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
private fun AirWindowHost(
    page: String?,
    maxH: Dp,
    lite: Boolean,
    onClose: () -> Unit,
    onBack: () -> Unit,
    content: @Composable (String) -> Unit,
) {
    val c = Air.colors
    val blur = !lite && android.os.Build.VERSION.SDK_INT >= 31
    AnimatedVisibility(page != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = if (c.dark) 0.2f else 0.08f)) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
        )
    }
    var last by remember { mutableStateOf(Pages.MENU) }
    if (page != null) last = page
    AnimatedVisibility(
        page != null,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.68f, stiffness = 360f), initialScale = 0.84f) +
            slideInVertically(spring(dampingRatio = 0.75f, stiffness = 300f)) { it / 12 },
        exit = fadeOut(tween(160)) + scaleOut(tween(180), targetScale = 0.9f) + slideOutVertically(tween(180)) { it / 16 }
    ) {
        val open by transition.animateFloat(
            transitionSpec = { spring(dampingRatio = 0.72f, stiffness = 240f) }, label = "wopen"
        ) { st -> if (st == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }
        Box(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp)
                .graphicsLayer {
                    if (!lite) {
                        rotationX = (1f - open) * 22f
                        rotationY = (1f - open) * -8f
                        cameraDistance = 16f * density
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                },
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
                    .aeroShadow()
                    .backdropBlur(AirSpec.blur, AirSpec.windowCorner, blur)
                    .aeroFrame(c, if (blur) AirSpec.glassAlpha else AirSpec.glassAlphaFallback)
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
                        .padding(start = 7.dp, end = 7.dp, bottom = 7.dp)
                        .then(if (sub) Modifier.weight(1f) else Modifier)
                        .fillMaxWidth()
                        .drawBehind {
                            val a = if (blur) AirSpec.contentAlpha else AirSpec.contentAlphaFallback
                            drawRoundRect(if (c.dark) Color(0xFF071620).copy(alpha = a) else Color(0xFFF7FBFE).copy(alpha = a), cornerRadius = CornerRadius(10.dp.toPx()))
                            drawRoundRect(Color.White.copy(alpha = if (c.dark) 0.18f else 0.6f), cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(1.dp.toPx()))
                        }
                ) {
                    AnimatedContent(
                        targetState = pg,
                        transitionSpec = {
                            if (lite) {
                                fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                            } else {
                                (fadeIn(tween(260, 90)) togetherWith fadeOut(tween(200))).using(androidx.compose.animation.SizeTransform(clip = false))
                            }
                        },
                        label = "win"
                    ) { p ->
                        val back = pg == Pages.MENU
                        val k by transition.animateFloat(
                            transitionSpec = { spring(dampingRatio = 0.8f, stiffness = 200f) }, label = "flip"
                        ) { st ->
                            when (st) {
                                androidx.compose.animation.EnterExitState.PreEnter -> 1f
                                androidx.compose.animation.EnterExitState.Visible -> 0f
                                androidx.compose.animation.EnterExitState.PostExit -> -1f
                            }
                        }
                        Box(
                            Modifier.graphicsLayer {
                                if (!lite) {
                                    val dir = if (back) -1f else 1f
                                    val kk = k * dir
                                    cameraDistance = 12f * density
                                    rotationY = -kk * 48f
                                    translationX = kk * size.width * 0.42f
                                    translationY = kotlin.math.abs(k) * -size.height * 0.04f
                                    val sc = 1f - 0.22f * kotlin.math.abs(k)
                                    scaleX = sc
                                    scaleY = sc
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (kk > 0) 0f else 1f, 0.5f)
                                }
                            }
                        ) { content(p) }
                    }
                }
            }
        }
    }
}

private fun Modifier.aeroShadow(): Modifier = this.drawBehind {
    val r = AirSpec.windowCorner.toPx()
    val path = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
    }
    clipPath(path, androidx.compose.ui.graphics.ClipOp.Difference) {
        for (k in 1..8) {
            val g = k * 2.5.dp.toPx()
            drawRoundRect(
                Color.Black.copy(alpha = 0.055f * (9 - k) / 8f),
                topLeft = Offset(-g, -g + 4.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width + g * 2, size.height + g * 2),
                cornerRadius = CornerRadius(r + g),
                style = Stroke(2.6.dp.toPx())
            )
        }
    }
}

private fun Modifier.aeroFrame(c: gpr.greentune.air.AirColors, alpha: Float): Modifier = this.drawBehind {
    val r = AirSpec.windowCorner.toPx()
    val cr = CornerRadius(r, r)
    drawRoundRect(
        Brush.verticalGradient(
            listOf(
                lerpC(c.tint, Color.White, if (c.dark) 0.15f else 0.45f).copy(alpha = alpha),
                lerpC(c.tint, Color.Black, if (c.dark) 0.35f else 0.05f).copy(alpha = alpha * 0.8f),
                lerpC(c.tint, Color.Black, if (c.dark) 0.5f else 0.15f).copy(alpha = alpha),
            )
        ),
        cornerRadius = cr
    )
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f), Color.Transparent), 0f, 40.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(size.width, 40.dp.toPx()),
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
    val c = Air.colors
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
    val c = Air.colors
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
    val c = Air.colors
    val src = remember { MutableInteractionSource() }
    val activeA by animateFloatAsState(if (active) 1f else 0f, tween(350), label = "ta")
    Column(
        modifier
            .pressBounce(src, 0.92f)
            .glass(c, 18.dp)
            .drawBehind {
                if (activeA > 0.01f) {
                    drawRoundRect(c.tint.copy(alpha = 0.22f * activeA), cornerRadius = CornerRadius(18.dp.toPx()))
                    drawRoundRect(c.tint.copy(alpha = 0.7f * activeA), cornerRadius = CornerRadius(18.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                }
            }
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), enabled = enabled, onClick = onClick)
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
            Tile(R.drawable.ic_menu_services, stringResource(R.string.nav_services), Modifier.weight(1f).staggerIn(0, !lite)) { onOpen(Pages.SERVICES) }
            Tile(R.drawable.ic_v3_telegram, "Telegram", Modifier.weight(1f).staggerIn(1, !lite), active = tg.running, badge = settings.tgBridge && !settings.tgLinked) { onOpen(Pages.TELEGRAM) }
            Tile(R.drawable.ic_v3_retune, stringResource(R.string.retune), Modifier.weight(1f).staggerIn(2, !lite), enabled = !tune.running) { Tuner.start(context, full = false) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(R.drawable.ic_v3_lite, stringResource(R.string.lite_mode), Modifier.weight(1f).staggerIn(3, !lite), active = lite) {
                if (settings.haptics) Fx.haptic(view)
                SettingsStore.update { it.copy(lite = if (lite) "off" else "on") }
            }
            Tile(R.drawable.ic_v3_network, stringResource(R.string.diag_title), Modifier.weight(1f).staggerIn(4, !lite)) { onOpen(Pages.DIAG) }
            Tile(R.drawable.ic_menu_apps, stringResource(R.string.nav_apps), Modifier.weight(1f).staggerIn(5, !lite)) { onOpen(Pages.APPS) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(R.drawable.ic_v3_expert, stringResource(R.string.nav_settings), Modifier.weight(1f).staggerIn(6, !lite)) { onOpen(Pages.SETTINGS) }
            Tile(R.drawable.ic_menu_feedback, stringResource(R.string.nav_feedback), Modifier.weight(1f).staggerIn(7, !lite)) { onOpen(Pages.FEEDBACK) }
            Tile(R.drawable.ic_v3_shield, stringResource(R.string.nav_about), Modifier.weight(1f).staggerIn(8, !lite)) { onOpen(Pages.ABOUT) }
        }
        Box(Modifier.staggerIn(9, !lite)) { DonateRow { onOpen(Pages.DONATE) } }
    }
}

@Composable
private fun DonateRow(onClick: () -> Unit) {
    val c = Air.colors
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
    val c = Air.colors
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
            AirButton(
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
            gpr.greentune.air.ToggleRow(
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
