package gpr.greentune.unblock.ui.aero

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

class BackdropSource(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)
}

class Backdrop {
    val sources = ArrayList<BackdropSource>()
    var version by mutableStateOf(0)
    val supported: Boolean get() = Build.VERSION.SDK_INT >= 31
}

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun ProvideBackdrop(content: @Composable () -> Unit) {
    val b = remember { Backdrop() }
    CompositionLocalProvider(LocalBackdrop provides b, content = content)
}

@Composable
fun Modifier.backdropSource(): Modifier {
    val b = LocalBackdrop.current ?: return this
    val layer = rememberGraphicsLayer()
    val src = remember(layer) { BackdropSource(layer) }
    DisposableEffect(src) {
        b.sources.add(src)
        b.version++
        onDispose {
            b.sources.remove(src)
            b.version++
        }
    }
    return this
        .onGloballyPositioned {
            src.origin = it.positionInRoot()
            src.size = it.size
        }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
}

@Composable
fun Modifier.backdropBlur(radius: Dp, corner: Dp, enabled: Boolean = true): Modifier {
    val b = LocalBackdrop.current ?: return this
    if (!enabled || !b.supported) return this
    val blurLayer = rememberGraphicsLayer()
    var pos by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .drawBehind {
            b.version
            val sources = b.sources.toList()
            if (sources.isEmpty()) return@drawBehind
            val root = sources.first()
            val r = radius.toPx()
            if (r > 0.5f && blurLayer.renderEffect == null) blurLayer.renderEffect = vibrantBlur(r)
            val w = sources.maxOf { (it.origin.x - root.origin.x).toInt() + it.size.width }
            val h = sources.maxOf { (it.origin.y - root.origin.y).toInt() + it.size.height }
            if (w <= 0 || h <= 0) return@drawBehind
            blurLayer.record(IntSize(w, h)) {
                for (s in sources) {
                    val o = s.origin - root.origin
                    translate(o.x, o.y) { drawLayer(s.layer) }
                }
            }
            val off = pos - root.origin
            val clip = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner.toPx())))
            }
            clipPath(clip) {
                translate(-off.x, -off.y) { drawLayer(blurLayer) }
            }
        }
}

private fun vibrantBlur(r: Float): androidx.compose.ui.graphics.RenderEffect =
    try {
        val sat = android.graphics.ColorMatrix().apply { setSaturation(1.5f) }
        android.graphics.RenderEffect.createChainEffect(
            android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(sat)),
            android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP)
        ).asComposeRenderEffect()
    } catch (_: Throwable) {
        BlurEffect(r, r, TileMode.Clamp)
    }
