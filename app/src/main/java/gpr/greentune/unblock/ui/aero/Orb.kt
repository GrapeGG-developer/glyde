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
            drawShadow(r, squashX, glow, energy)
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
        Brush.radialGradient(0.62f to glow.copy(alpha = 0.42f * energy), 0.82f to glow.copy(alpha = 0.12f * energy), 1f to Color.Transparent, center = c, radius = r * 1.5f),
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

private fun DrawScope.drawShadow(r: Float, sx: Float, glow: Color = Color.Transparent, energy: Float = 0f) {
    val c = Offset(center.x, center.y + r * 1.02f)
    scale(1f * sx, 0.2f, pivot = c) {
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.15f), Color.Transparent), c, r * 0.9f), r * 0.9f, c)
    }
    val k = Offset(center.x, center.y + r * 1.0f)
    scale(sx * 0.9f, 0.16f, pivot = k) {
        drawCircle(Brush.radialGradient(listOf(lerpColor(glow, Color.White, 0.4f).copy(alpha = 0.55f * (0.4f + 0.6f * energy)), Color.Transparent), k, r * 0.5f), r * 0.5f, k)
    }
}

private fun DrawScope.drawOrb(r: Float, top: Color, mid: Color, bottom: Color, glow: Color, energy: Float, spinA: Float, spin: Float, pressed: Boolean, sweep: Float) {
    val c = center
    val tilt = Motion.tilt.value
    val lx = (-tilt.x * 0.10f).coerceIn(-0.18f, 0.18f)
    val ly = (tilt.y * 0.05f).coerceIn(-0.1f, 0.1f)
    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
    drawCircle(
        Brush.radialGradient(
            0f to lerpColor(bottom, Color.White, 0.35f),
            0.32f to bottom,
            0.66f to mid,
            0.9f to top,
            1f to lerpColor(top, Color.Black, 0.35f),
            center = Offset(c.x - lx * r, c.y + r * 0.42f),
            radius = r * 1.42f
        ),
        r, c
    )
    clipPath(clip) {
        drawCircle(
            Brush.radialGradient(
                0f to Color.Transparent,
                0.6f to Color.Transparent,
                0.84f to lerpColor(top, Color.Black, 0.4f).copy(alpha = 0.5f),
                1f to lerpColor(top, Color.Black, 0.65f).copy(alpha = 0.85f),
                center = Offset(c.x, c.y - r * 0.08f),
                radius = r * 1.04f
            ),
            r, c
        )
        val caustic = Offset(c.x + lx * r * 0.6f, c.y + r * 0.62f)
        scale(1.15f, 0.62f, pivot = caustic) {
            drawCircle(
                Brush.radialGradient(
                    listOf(lerpColor(glow, Color.White, 0.55f).copy(alpha = 0.85f * (0.55f + 0.45f * energy)), glow.copy(alpha = 0.35f), Color.Transparent),
                    caustic, r * 0.62f
                ),
                r * 0.62f, caustic
            )
        }
        if (spinA > 0.01f) {
            rotate(spin, c) {
                for (i in 0 until 18) {
                    val a = 1f - i / 18f
                    drawArc(
                        lerpColor(glow, Color.White, 0.5f).copy(alpha = 0.55f * a * spinA), -i * 7f, 7.5f, false,
                        topLeft = Offset(c.x - r * 0.78f, c.y - r * 0.78f), size = Size(r * 1.56f, r * 1.56f),
                        style = Stroke(r * 0.07f * a + r * 0.01f, cap = StrokeCap.Round)
                    )
                }
            }
        }
        drawPower(Offset(c.x - lx * r * 0.25f, c.y + r * 0.04f), r, glow, energy)
        drawCircle(
            Brush.radialGradient(
                0f to Color.Transparent,
                0.82f to Color.Transparent,
                0.95f to lerpColor(glow, Color.White, 0.5f).copy(alpha = 0.2f),
                1f to lerpColor(glow, Color.White, 0.7f).copy(alpha = 0.45f),
                center = Offset(c.x, c.y + r * 0.04f),
                radius = r
            ),
            r, c
        )
        drawArc(
            Brush.verticalGradient(listOf(Color.Transparent, lerpColor(glow, Color.White, 0.7f).copy(alpha = 0.9f)), c.y + r * 0.2f, c.y + r),
            30f, 120f, false,
            topLeft = Offset(c.x - r * 0.9f, c.y - r * 0.9f), size = Size(r * 1.8f, r * 1.8f),
            style = Stroke(r * 0.045f, cap = StrokeCap.Round)
        )
        val sx = c.x - r * 1.8f + sweep * r * 6f
        if (sx < c.x + r * 1.8f) {
            rotate(28f, c) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent), sx - r * 0.25f, sx + r * 0.25f),
                    topLeft = Offset(sx - r * 0.25f, c.y - r * 1.6f), size = Size(r * 0.5f, r * 3.2f)
                )
            }
        }
        if (pressed) drawCircle(Color.Black.copy(alpha = 0.12f), r, c)
        val hx = c.x + lx * r
        val hy = c.y + ly * r
        drawOval(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.95f), 0.35f to Color.White.copy(alpha = 0.55f), 1f to Color.White.copy(alpha = 0f),
                startY = hy - r * 0.93f, endY = hy - r * 0.05f
            ),
            topLeft = Offset(hx - r * 0.66f, hy - r * 0.93f),
            size = Size(r * 1.32f, r * 0.88f)
        )
        drawOval(
            Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), Offset(hx - r * 0.34f, hy - r * 0.62f), r * 0.2f),
            topLeft = Offset(hx - r * 0.54f, hy - r * 0.74f),
            size = Size(r * 0.4f, r * 0.24f)
        )
        drawCircle(Color.White.copy(alpha = 0.9f), r * 0.035f, Offset(hx + r * 0.42f, hy - r * 0.5f))
    }
}

private fun DrawScope.drawPower(c: Offset, r: Float, glow: Color, energy: Float) {
    val rr = r * 0.33f
    val w = r * 0.12f
    fun strokes(color: Color, width: Float, o: Offset = Offset.Zero) {
        drawArc(
            color, -50f, 280f, false,
            topLeft = Offset(c.x - rr + o.x, c.y - rr + o.y), size = Size(rr * 2, rr * 2),
            style = Stroke(width, cap = StrokeCap.Round)
        )
        drawLine(color, Offset(c.x + o.x, c.y - rr * 1.12f + o.y), Offset(c.x + o.x, c.y - rr * 0.15f + o.y), width, StrokeCap.Round)
    }
    val g = lerpColor(glow, Color.White, 0.55f)
    val k = 0.45f + 0.55f * energy
    drawCircle(Brush.radialGradient(0f to g.copy(alpha = 0.30f * k), 0.6f to g.copy(alpha = 0.12f * k), 1f to Color.Transparent, center = c, radius = rr * 1.7f), rr * 1.7f, c)
    strokes(Color.Black.copy(alpha = 0.22f), w, Offset(0f, w * 0.45f))
    strokes(lerpColor(g, Color.White, 0.75f), w)
    strokes(g.copy(alpha = 0.5f), w * 0.45f, Offset(0f, w * 0.12f))
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    1f
)
