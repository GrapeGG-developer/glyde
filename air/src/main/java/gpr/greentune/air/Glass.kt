package gpr.greentune.air

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
fun AirWallpaper(modifier: Modifier = Modifier) {
    val c = Air.colors
    val t = rememberInfiniteTransition(label = "wall")
    val phase by t.animateFloat(
        0f, 6.2832f,
        infiniteRepeatable(tween(90000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(c.wallTop, c.wallBottom)))
            val glowCenter = Offset(w * (0.53f + 0.008f * sin(phase)), h * 0.38f)
            drawCircle(
                Brush.radialGradient(listOf(c.tint.copy(alpha = if (c.dark) 0.13f else 0.15f), Color.Transparent), glowCenter, w * 0.8f),
                w * 0.8f,
                glowCenter
            )
            val horizon = h * 0.61f
            val shelf = Path().apply {
                moveTo(-w * 0.12f, horizon + h * 0.08f)
                cubicTo(w * 0.18f, horizon - h * 0.08f, w * 0.62f, horizon - h * 0.12f, w * 1.12f, horizon + h * 0.01f)
                lineTo(w * 1.12f, h * 1.12f)
                lineTo(-w * 0.12f, h * 1.12f)
                close()
            }
            drawPath(
                shelf,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (c.dark) 0.035f else 0.12f),
                        c.tint.copy(alpha = if (c.dark) 0.045f else 0.085f),
                        Color.Transparent
                    ),
                    horizon - h * 0.1f,
                    h
                )
            )
            val edge = Path().apply {
                moveTo(-w * 0.1f, horizon + h * 0.08f)
                cubicTo(w * 0.18f, horizon - h * 0.08f, w * 0.62f, horizon - h * 0.12f, w * 1.1f, horizon + h * 0.01f)
            }
            drawPath(edge, Color.White.copy(alpha = if (c.dark) 0.13f else 0.42f), style = Stroke(1.2.dp.toPx()))
            drawPath(edge, c.tint.copy(alpha = if (c.dark) 0.15f else 0.20f), style = Stroke(5.dp.toPx()))
            drawArc(
                Color.White.copy(alpha = if (c.dark) 0.08f else 0.19f),
                202f,
                136f,
                false,
                Offset(-w * 0.2f, h * 0.33f),
                Size(w * 1.4f, h * 0.48f),
                style = Stroke(1.dp.toPx())
            )
            val sheenX = w * (0.22f + 0.56f * ((sin(phase) + 1f) * 0.5f))
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = if (c.dark) 0.075f else 0.20f), Color.Transparent), sheenX - w * 0.16f, sheenX + w * 0.16f),
                Offset(sheenX - w * 0.12f, h * 0.16f),
                Offset(sheenX + w * 0.12f, h * 0.30f),
                strokeWidth = 1.1.dp.toPx()
            )
        }
    }
}

fun Modifier.glass(
    colors: AirColors,
    corner: Dp = 20.dp,
    tinted: Boolean = true,
    glossFraction: Float = 0.48f,
): Modifier = this.drawWithContent {
    val r = minOf(corner.toPx(), size.height / 2, size.width / 2)
    val cr = CornerRadius(r, r)
    val one = 1.dp.toPx()
    val tilt = Motion.tilt.value
    drawRoundRect(
        Color.Black.copy(alpha = if (colors.dark) 0.30f else 0.10f),
        topLeft = Offset(-tilt.x * 3.dp.toPx(), 3.dp.toPx() - tilt.y * 2.dp.toPx()),
        size = size,
        cornerRadius = cr
    )
    drawRoundRect(Brush.verticalGradient(listOf(colors.glassTop, colors.glassBottom)), cornerRadius = cr)
    if (tinted) drawRoundRect(colors.glassTint, cornerRadius = cr)
    val gh = size.height * glossFraction
    clipRect(bottom = gh) {
        drawRoundRect(
            Brush.verticalGradient(listOf(colors.gloss, colors.gloss.copy(alpha = colors.gloss.alpha * 0.12f)), 0f, gh),
            topLeft = Offset(one * 1.5f, one * 1.5f),
            size = Size(size.width - 3 * one, size.height - 3 * one),
            cornerRadius = CornerRadius(r - one, r - one)
        )
    }
    val sx = size.width * (0.5f - 0.9f * tilt.x)
    val band = size.width * 0.55f
    drawRoundRect(
        Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to Color.White.copy(alpha = if (colors.dark) 0.07f else 0.22f),
            1f to Color.Transparent,
            start = Offset(sx - band, 0f),
            end = Offset(sx + band * 0.4f, size.height)
        ),
        cornerRadius = cr
    )
    drawRoundRect(
        Brush.radialGradient(
            listOf(colors.tint.copy(alpha = if (colors.dark) 0.16f else 0.12f), Color.Transparent),
            Offset(size.width * (0.5f + 0.3f * tilt.x), size.height * 1.05f),
            maxOf(size.width, size.height) * 0.7f
        ),
        cornerRadius = cr
    )
    drawContent()
    drawRoundRect(colors.borderOuter, cornerRadius = cr, style = Stroke(one))
    drawRoundRect(
        Brush.verticalGradient(listOf(colors.borderInner, colors.borderInner.copy(alpha = colors.borderInner.alpha * 0.25f), colors.borderInner.copy(alpha = colors.borderInner.alpha * 0.6f))),
        topLeft = Offset(one, one),
        size = Size(size.width - 2 * one, size.height - 2 * one),
        cornerRadius = CornerRadius(r - one, r - one),
        style = Stroke(one)
    )
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    padding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Air.colors
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
