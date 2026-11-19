package gpr.greentune.unblock.ui.aero

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gpr.greentune.unblock.R
import kotlin.math.cos
import kotlin.math.sin

object Boot {
    val done = mutableStateOf(false)
}

private val lights = listOf(Color(0xFFFF4A2E), Color(0xFF7CD420), Color(0xFF1E9BFF), Color(0xFFFFC21A))

private fun ease(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

private fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

@Composable
fun BootIntro(lite: Boolean) {
    if (Boot.done.value) return
    val p = remember { Animatable(0f) }
    val total = if (lite) 900 else 2300
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(total, easing = LinearEasing))
        Boot.done.value = true
    }
    val t = p.value
    val fadeOut = seg(t, 0.84f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - fadeOut }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Boot.done.value = true },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Color(0xFF071A2B), Color(0xFF020509)), center, size.maxDimension * 0.7f))
            val c = center
            val orbit = size.minDimension * 0.42f
            val fly = seg(t, 0.0f, 0.5f)
            val merge = seg(t, 0.42f, 0.6f)
            for ((i, col) in lights.withIndex()) {
                for (k in 0 until 9) {
                    val tt = (fly - k * 0.018f).coerceIn(0f, 1f)
                    if (tt <= 0f) continue
                    val e = ease(tt)
                    val ang = i * 1.5708f + 0.6f + e * 4.2f
                    val rad = orbit * (1f - e)
                    val pos = Offset(c.x + cos(ang) * rad, c.y + sin(ang) * rad * 0.82f)
                    val a = (1f - merge) * (1f - k / 9f) * (if (k == 0) 1f else 0.35f)
                    val rr = (if (k == 0) 26f else 16f - k) * density
                    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = a), col.copy(alpha = a * 0.3f), Color.Transparent), pos, rr), rr, pos)
                    if (k == 0) drawCircle(Color.White.copy(alpha = a * 0.9f), 3.5f * density, pos)
                }
            }
            val flash = seg(t, 0.48f, 0.62f) * (1f - seg(t, 0.62f, 0.85f))
            if (flash > 0f) {
                val fr = size.minDimension * (0.2f + 0.35f * seg(t, 0.48f, 0.8f))
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f * flash), Color(0xFF7FD8FF).copy(alpha = 0.35f * flash), Color.Transparent), c, fr), fr, c)
            }
            val ring = seg(t, 0.55f, 0.95f)
            if (ring in 0.001f..0.999f) {
                val rr = size.minDimension * (0.12f + 0.5f * ease(ring))
                drawCircle(Color(0xFF9FE6FF).copy(alpha = 0.35f * (1f - ring)), rr, c, style = androidx.compose.ui.graphics.drawscope.Stroke(2f * density))
            }
        }
        val logo = seg(t, 0.5f, 0.72f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.logo_glass), null,
                Modifier.size(120.dp).graphicsLayer {
                    alpha = logo
                    val s = 0.6f + 0.4f * ease(logo)
                    scaleX = s
                    scaleY = s
                }
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Glyde", fontSize = 30.sp, fontWeight = FontWeight.Light, color = Color.White,
                modifier = Modifier.graphicsLayer {
                    val k = seg(t, 0.6f, 0.8f)
                    alpha = k
                    translationY = (1f - k) * 12.dp.toPx()
                }
            )
        }
    }
}
