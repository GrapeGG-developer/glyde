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
import androidx.compose.ui.graphics.drawscope.translate
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
    val squashX by animateFloatAsState(if (pressed) 1.05f else 1f, spring(dampingRatio = 0.32f, stiffness = 420f), label = "sx")
    val squashY by animateFloatAsState(if (pressed) 0.91f else 1f, spring(dampingRatio = 0.32f, stiffness = 420f), label = "sy")
    val pal = when (state) {
        OrbState.On -> PaletteOn
        OrbState.Error -> PaletteError
        else -> PaletteOff
    }
    val top by animateColorAsState(pal.top, tween(700), label = "t")
    val mid by animateColorAsState(pal.mid, tween(700), label = "m")
    val bottom by animateColorAsState(pal.bottom, tween(700), label = "b")
    val glow by animateColorAsState(pal.glow, tween(700), label = "g")
    val energy by animateFloatAsState(
        when (state) {
            OrbState.On -> 1f
            OrbState.Starting -> 0.65f
            OrbState.Error -> 0.5f
            OrbState.Off -> 0.32f
        }, tween(700), label = "e"
    )
    val spinA by animateFloatAsState(if (state == OrbState.Starting) 1f else 0f, tween(300), label = "sa")
    val onA by animateFloatAsState(if (state == OrbState.On) 1f else 0f, tween(900), label = "oa")
    val t = rememberInfiniteTransition(label = "orb")
    val breathe by t.animateFloat(0.72f, 1f, infiniteRepeatable(tween(2100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "br")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "sp")
    val sweep by t.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "sw")
    val ripple by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "rp")
    val wobble by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(4800, easing = LinearEasing), RepeatMode.Restart), label = "wb")

    Box(modifier.size(size * 1.35f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.toPx() / 2f
            drawHalo(r, glow, energy * (if (state == OrbState.On) breathe else 1f), onA, ripple)
            drawShadow(r, squashX)
        }
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = squashX * (1f + 0.012f * onA * kotlin.math.sin(wobble))
                scaleY = squashY * (1f + 0.012f * onA * kotlin.math.cos(wobble))
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.86f)
            }
        ) {
            drawOrb(size.toPx() / 2f, top, mid, bottom, glow, energy * (if (state == OrbState.On) breathe else 1f), spinA, spin, pressed, sweep)
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

private fun DrawScope.drawHalo(r: Float, glow: Color, energy: Float, onA: Float, ripple: Float) {
    val c = center
    drawCircle(
        Brush.radialGradient(0.5f to glow.copy(alpha = 0.6f * energy), 0.78f to glow.copy(alpha = 0.16f * energy), 1f to Color.Transparent, center = c, radius = r * 1.5f),
        r * 1.5f, c
    )
    if (onA > 0.01f) {
        for (k in 0 until 3) {
            val p = (ripple + k / 3f) % 1f
            val rr = r * (1.0f + 0.48f * p)
            drawCircle(glow.copy(alpha = 0.45f * (1f - p) * (1f - p) * onA), rr, c, style = Stroke(r * 0.025f * (1.2f - p)))
        }
    }
}

private fun DrawScope.drawShadow(r: Float, sx: Float) {
    val c = Offset(center.x, center.y + r * 0.98f)
    scale(1f * sx, 0.22f, pivot = c) {
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent), c, r * 0.95f), r * 0.95f, c)
    }
}

private fun DrawScope.drawOrb(r: Float, top: Color, mid: Color, bottom: Color, glow: Color, energy: Float, spinA: Float, spin: Float, pressed: Boolean, sweep: Float) {
    val c = center
    val tilt = Motion.tilt.value
    drawCircle(
        Brush.sweepGradient(
            listOf(Color(0xFFF4F8FB), Color(0xFF8D99A6), Color(0xFFE9EEF3), Color(0xFF5C6773), Color(0xFFDCE3EA), Color(0xFF7D8894), Color(0xFFF4F8FB)),
            center = c
        ),
        r, c
    )
    drawCircle(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.35f)), c.y - r, c.y + r),
        r, c
    )
    drawCircle(Color.Black.copy(alpha = 0.55f), r * 0.915f, c, style = Stroke(r * 0.02f))
    val ri = r * 0.895f
    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, ri)) }
    clipPath(clip) {
        drawCircle(
            Brush.verticalGradient(0f to top, 0.5f to mid, 1f to bottom, startY = c.y - ri, endY = c.y + ri),
            ri, c
        )
        drawCircle(
            Brush.radialGradient(0.0f to lerpColor(bottom, Color.White, 0.25f).copy(alpha = 0.55f), 0.55f to Color.Transparent, center = Offset(c.x, c.y + ri * 0.55f), radius = ri * 1.1f),
            ri, c
        )
        drawCircle(
            Brush.radialGradient(0.62f to Color.Transparent, 0.92f to top.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.55f), center = c, radius = ri),
            ri, c
        )
        val bc = Offset(c.x, c.y + ri * 0.84f)
        scale(1f, 0.55f, pivot = bc) {
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.95f), glow.copy(alpha = 0.3f), Color.Transparent), bc, ri * 0.95f), ri * 0.95f, bc)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), bc, ri * 0.5f), ri * 0.5f, bc)
        }
        drawArc(
            Brush.verticalGradient(listOf(Color.Transparent, lerpColor(glow, Color.White, 0.6f).copy(alpha = 0.75f)), c.y, c.y + ri),
            20f, 140f, false,
            topLeft = Offset(c.x - ri * 0.94f, c.y - ri * 0.94f), size = Size(ri * 1.88f, ri * 1.88f),
            style = Stroke(ri * 0.05f, cap = StrokeCap.Round)
        )
        if (pressed) drawCircle(Color.Black.copy(alpha = 0.14f), ri, c)
        drawPower(c, ri, glow, energy)
        val sx = c.x - ri * 1.6f + sweep * ri * 5.2f
        if (sx < c.x + ri * 1.6f) {
            rotate(24f, c) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.32f), Color.Transparent), sx - ri * 0.22f, sx + ri * 0.22f),
                    topLeft = Offset(sx - ri * 0.22f, c.y - ri * 1.5f), size = Size(ri * 0.44f, ri * 3f)
                )
            }
        }
        val gx = -tilt.x * ri * 0.07f
        val gy = tilt.y * ri * 0.03f
        drawOval(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.92f), 0.45f to Color.White.copy(alpha = 0.42f), 1f to Color.White.copy(alpha = 0.06f),
                startY = c.y - ri * 0.95f + gy, endY = c.y - ri * 0.02f + gy
            ),
            topLeft = Offset(c.x - ri * 0.74f + gx, c.y - ri * 0.95f + gy),
            size = Size(ri * 1.48f, ri * 0.93f)
        )
        drawOval(
            Color.White.copy(alpha = 0.95f),
            topLeft = Offset(c.x - ri * 0.52f + gx, c.y - ri * 0.80f + gy),
            size = Size(ri * 0.26f, ri * 0.13f)
        )
    }
    drawArc(
        Color.White.copy(alpha = 0.75f), 205f, 130f, false,
        topLeft = Offset(c.x - r * 0.985f, c.y - r * 0.985f), size = Size(r * 1.97f, r * 1.97f),
        style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round)
    )
    if (spinA > 0.01f) {
        rotate(spin, c) {
            val segs = 16
            for (i in 0 until segs) {
                val a = (1f - i / segs.toFloat())
                drawArc(
                    lerpColor(glow, Color.White, 0.6f).copy(alpha = 0.95f * a * spinA), -i * 8f, 8.5f, false,
                    topLeft = Offset(c.x - r * 0.955f, c.y - r * 0.955f), size = Size(r * 1.91f, r * 1.91f),
                    style = Stroke(r * 0.06f, cap = if (i == 0) StrokeCap.Round else StrokeCap.Butt)
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
    translate(0f, w * 0.35f) { strokes(Color.Black.copy(alpha = 0.28f), w) }
    strokes(Color.White, w)
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    1f
)
