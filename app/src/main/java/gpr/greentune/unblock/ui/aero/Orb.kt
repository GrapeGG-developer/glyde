package gpr.greentune.unblock.ui.aero

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class OrbState { Off, Starting, On, Error }

private class OrbPalette(val top: Color, val mid: Color, val bottom: Color, val glow: Color)

private val PaletteOff = OrbPalette(Color(0xFF08296B), Color(0xFF1261C7), Color(0xFF40BFFF), Color(0xFF59CCFF))
private val PaletteOn = OrbPalette(Color(0xFF05471F), Color(0xFF149442), Color(0xFF73F273), Color(0xFF66FF80))
private val PaletteError = OrbPalette(Color(0xFF520808), Color(0xFFB81F1A), Color(0xFFFF7359), Color(0xFFFF664D))

@Composable
fun Orb(state: OrbState, onClick: () -> Unit, description: String, modifier: Modifier = Modifier, size: Dp = 230.dp) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "sc")
    val pal = when (state) {
        OrbState.On -> PaletteOn
        OrbState.Error -> PaletteError
        else -> PaletteOff
    }
    val top by animateColorAsState(pal.top, tween(600), label = "t")
    val mid by animateColorAsState(pal.mid, tween(600), label = "m")
    val bottom by animateColorAsState(pal.bottom, tween(600), label = "b")
    val glow by animateColorAsState(pal.glow, tween(600), label = "g")
    val energy by animateFloatAsState(
        when (state) {
            OrbState.On -> 1f
            OrbState.Starting -> 0.6f
            OrbState.Error -> 0.5f
            OrbState.Off -> 0.3f
        }, tween(600), label = "e"
    )
    val spinA by animateFloatAsState(if (state == OrbState.Starting) 1f else 0f, tween(300), label = "sa")
    val t = rememberInfiniteTransition(label = "orb")
    val breathe by t.animateFloat(0.7f, 1f, infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "br")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "sp")

    Box(modifier.size(size * 1.35f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }) {
            drawOrb(size.toPx() / 2f, top, mid, bottom, glow, energy * (if (state == OrbState.On) breathe else 1f), spinA, spin, pressed)
        }
        Box(
            Modifier
                .size(size * 0.98f)
                .clip(CircleShape)
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
        )
    }
}

private fun DrawScope.drawOrb(r: Float, top: Color, mid: Color, bottom: Color, glow: Color, energy: Float, spinA: Float, spin: Float, pressed: Boolean) {
    val c = center
    val tilt = Motion.tilt.value
    drawCircle(
        Brush.radialGradient(0.55f to glow.copy(alpha = 0.55f * energy), 1f to Color.Transparent, center = c, radius = r * 1.45f),
        r * 1.45f, c
    )
    drawCircle(
        Brush.radialGradient(0.8f to Color.Black.copy(alpha = 0.35f), 1f to Color.Transparent, center = c + Offset(0f, r * 0.06f), radius = r * 1.1f),
        r * 1.1f, c + Offset(0f, r * 0.06f)
    )
    drawCircle(
        Brush.verticalGradient(listOf(Color(0xFF66717D), Color(0xFF232A33), Color(0xFF0A0D11)), c.y - r, c.y + r),
        r, c
    )
    drawArc(
        Color.White.copy(alpha = 0.55f), 200f, 140f, false,
        topLeft = Offset(c.x - r * 0.985f, c.y - r * 0.985f), size = Size(r * 1.97f, r * 1.97f),
        style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round)
    )
    val ri = r * 0.91f
    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, ri)) }
    clipPath(clip) {
        drawCircle(
            Brush.verticalGradient(0f to top, 0.55f to mid, 1f to bottom, startY = c.y - ri, endY = c.y + ri),
            ri, c
        )
        drawCircle(
            Brush.radialGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f), center = c, radius = ri),
            ri, c
        )
        val bc = Offset(c.x, c.y + ri * 0.80f)
        scale(1f, 0.62f, pivot = bc) {
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.9f), Color.Transparent), bc, ri * 0.9f), ri * 0.9f, bc)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.38f), Color.Transparent), bc, ri * 0.55f), ri * 0.55f, bc)
        }
        if (pressed) drawCircle(Color.Black.copy(alpha = 0.12f), ri, c)
        val gx = -tilt.x * ri * 0.06f
        drawOval(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.70f), Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f)),
                c.y - ri * 0.93f, c.y + ri * 0.10f
            ),
            topLeft = Offset(c.x - ri * 0.78f + gx, c.y - ri * 0.93f),
            size = Size(ri * 1.56f, ri * 1.04f)
        )
        drawPower(c, ri, glow, energy)
    }
    if (spinA > 0.01f) {
        rotate(spin, c) {
            val segs = 14
            for (i in 0 until segs) {
                val a = (1f - i / segs.toFloat())
                drawArc(
                    Color.White.copy(alpha = 0.95f * a * spinA), -i * 8f, 8.5f, false,
                    topLeft = Offset(c.x - r * 0.955f, c.y - r * 0.955f), size = Size(r * 1.91f, r * 1.91f),
                    style = Stroke(r * 0.055f, cap = if (i == 0) StrokeCap.Round else StrokeCap.Butt)
                )
            }
        }
    }
}

private fun DrawScope.drawPower(c: Offset, ri: Float, glow: Color, energy: Float) {
    val rr = ri * 0.38f
    val w = ri * 0.15f
    fun strokes(color: Color, width: Float) {
        drawArc(
            color, -52f, 284f, false,
            topLeft = Offset(c.x - rr, c.y - rr), size = Size(rr * 2, rr * 2),
            style = Stroke(width, cap = StrokeCap.Round)
        )
        drawLine(color, Offset(c.x, c.y - rr * 1.15f), Offset(c.x, c.y - rr * 0.18f), width, StrokeCap.Round)
    }
    val g = lerpColor(glow, Color.White, 0.5f)
    val k = 0.5f + 0.5f * energy
    strokes(g.copy(alpha = 0.10f * k), w * 3.4f)
    strokes(g.copy(alpha = 0.16f * k), w * 2.4f)
    strokes(g.copy(alpha = 0.28f * k), w * 1.6f)
    strokes(Color.White, w)
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    1f
)
