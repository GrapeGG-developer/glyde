package gpr.greentune.unblock.ui.aero

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AeroWallpaper(modifier: Modifier = Modifier) {
    val c = Aero.colors
    val t = rememberInfiniteTransition(label = "wall")
    val phase by t.animateFloat(
        0f, 6.2832f,
        infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(c.wallTop, c.wallBottom)))
        }
        val blurMod = if (Build.VERSION.SDK_INT >= 31) Modifier.blur(48.dp) else Modifier
        Canvas(Modifier.fillMaxSize().then(blurMod)) {
            val w = size.width
            val h = size.height
            val a = if (c.dark) 0.30f else 0.38f
            val blobs = listOf(
                Triple(Offset(w * (0.18f + 0.06f * cos(phase)), h * (0.20f + 0.04f * sin(phase))), w * 0.55f, c.tint.copy(alpha = a)),
                Triple(Offset(w * (0.85f + 0.05f * sin(phase * 2)), h * (0.42f + 0.05f * cos(phase))), w * 0.50f, Color(0xFF39C6D8).copy(alpha = a * 0.7f)),
                Triple(Offset(w * (0.35f + 0.07f * sin(phase)), h * (0.86f + 0.03f * cos(phase * 2))), w * 0.65f, c.tint.copy(alpha = a * 0.8f)),
            )
            for ((center, r, col) in blobs) {
                drawCircle(Brush.radialGradient(listOf(col, Color.Transparent), center, r), r, center)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val streak = Color.White.copy(alpha = if (c.dark) 0.035f else 0.16f)
            for (k in 0 until 3) {
                val p = Path().apply {
                    moveTo(-w * 0.2f, h * (0.30f + k * 0.12f))
                    cubicTo(w * 0.3f, h * (0.18f + k * 0.12f), w * 0.6f, h * (0.52f + k * 0.1f), w * 1.2f, h * (0.34f + k * 0.14f))
                    lineTo(w * 1.2f, h * (0.40f + k * 0.14f))
                    cubicTo(w * 0.6f, h * (0.58f + k * 0.1f), w * 0.3f, h * (0.24f + k * 0.12f), -w * 0.2f, h * (0.36f + k * 0.12f))
                    close()
                }
                drawPath(p, streak)
            }
        }
    }
}

fun Modifier.glass(
    colors: AeroColors,
    corner: Dp = 14.dp,
    tinted: Boolean = true,
    glossFraction: Float = 0.48f,
): Modifier = this.drawWithContent {
    val r = corner.toPx()
    val cr = CornerRadius(r, r)
    val one = 1.dp.toPx()
    drawRoundRect(Brush.verticalGradient(listOf(colors.glassTop, colors.glassBottom)), cornerRadius = cr)
    if (tinted) drawRoundRect(colors.glassTint, cornerRadius = cr)
    clipRect(bottom = size.height * glossFraction) {
        drawRoundRect(
            Brush.verticalGradient(listOf(colors.gloss, colors.gloss.copy(alpha = colors.gloss.alpha * 0.25f)), 0f, size.height * glossFraction),
            topLeft = Offset(one, one),
            size = Size(size.width - 2 * one, size.height - 2 * one),
            cornerRadius = CornerRadius(r - one, r - one)
        )
    }
    drawContent()
    drawRoundRect(colors.borderOuter, cornerRadius = cr, style = Stroke(one))
    drawRoundRect(
        colors.borderInner,
        topLeft = Offset(one, one),
        size = Size(size.width - 2 * one, size.height - 2 * one),
        cornerRadius = CornerRadius(r - one, r - one),
        style = Stroke(one)
    )
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    padding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Aero.colors
    Box(modifier.glass(c, corner).padding(padding), content = content)
}

fun Modifier.glow(color: Color, alpha: Float, radius: Dp = 18.dp, corner: Dp = 10.dp): Modifier =
    if (alpha <= 0.001f) this else this.drawBehind {
        val rr = radius.toPx()
        val steps = 6
        for (s in steps downTo 1) {
            val grow = rr * s / steps
            val a = alpha * (1f - (s - 1f) / steps) * 0.22f
            drawRoundRect(
                color.copy(alpha = a),
                topLeft = Offset(-grow, -grow),
                size = Size(size.width + grow * 2, size.height + grow * 2),
                cornerRadius = CornerRadius(corner.toPx() + grow, corner.toPx() + grow)
            )
        }
    }
