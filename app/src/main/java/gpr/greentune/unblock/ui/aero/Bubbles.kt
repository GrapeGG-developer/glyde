package gpr.greentune.unblock.ui.aero

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object Touch {
    val point = mutableStateOf<Offset?>(null)
    val burst = mutableStateOf(0L)
    val burstAt = mutableStateOf(Offset.Zero)
}

fun Modifier.observeTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val ch = e.changes.firstOrNull()
            if (ch == null || !ch.pressed) {
                Touch.point.value = null
            } else {
                if (!ch.previousPressed) {
                    Touch.burstAt.value = ch.position
                    Touch.burst.value = System.nanoTime()
                }
                Touch.point.value = ch.position
            }
        }
    }
}

private class Bubble(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var r: Float,
    val depth: Float,
    val phase: Float,
    val freq: Float,
    var flash: Float = 0f,
    var lastBand: Int = -1,
    var passed: Float = 0f,
)

@Composable
fun Bubbles(energy: Float, lite: Boolean, modifier: Modifier = Modifier) {
    val c = Aero.colors
    val density = LocalDensity.current.density
    val bubbles = remember { ArrayList<Bubble>() }
    var frame by remember { mutableLongStateOf(0L) }
    var bounds by remember { mutableStateOf(Size.Zero) }
    val e = energy.coerceIn(0f, 1f)
    val count = if (lite) 7 else if (e > 0.5f) 18 else 13
    val energyNow by rememberUpdatedState(e)
    var flow by remember { mutableStateOf(e) }
    var clock by remember { mutableStateOf(0f) }
    var kick by remember { mutableStateOf(0f) }
    var lastEnergy by remember { mutableStateOf(e) }
    LaunchedEffect(count) {
        var last = 0L
        var t = 0f
        var lastBurst = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                t += dt
                clock = t
                val w = bounds.width
                val h = bounds.height
                if (w <= 0f || h <= 0f) return@withFrameNanos
                val rnd = Random
                val target = energyNow
                if (target > lastEnergy + 0.3f) kick = 1f
                lastEnergy = target
                kick = max(0f, kick - dt * 0.9f)
                while (bubbles.size < count) {
                    val depth = 0.35f + 0.65f * rnd.nextFloat()
                    val r = (5f + 14f * depth * depth + rnd.nextFloat() * 4f) * density
                    bubbles.add(Bubble(rnd.nextFloat() * w, rnd.nextFloat() * h, 0f, 0f, r, depth, rnd.nextFloat() * 6.28f, 0.4f + rnd.nextFloat() * 0.8f))
                }
                while (bubbles.size > count) bubbles.removeAt(bubbles.size - 1)
                flow += (target - flow) * min(1f, dt * 1.2f)
                val tilt = if (lite) Offset.Zero else Motion.tilt.value
                val touch = Touch.point.value
                val burst = Touch.burst.value
                val newBurst = burst != lastBurst
                lastBurst = burst
                val reach = 110f * density
                val bands = floatArrayOf(h * 0.34f, h * 0.66f)
                for (b in bubbles) {
                    val wanderX = (sin(t * b.freq * 0.7f + b.phase) + 0.6f * sin(t * b.freq * 1.9f + b.phase * 2.1f)) * 16f * density
                    val wanderY = (kotlin.math.cos(t * b.freq * 0.5f + b.phase * 1.3f)) * 10f * density
                    val rise = (55f + 95f * b.depth) * density * flow + 140f * density * kick * b.depth
                    val targetVx = wanderX * (1f - 0.6f * flow)
                    val targetVy = wanderY * (1f - flow) - rise
                    b.vx += (targetVx - b.vx) * min(1f, dt * 1.4f)
                    b.vy += (targetVy - b.vy) * min(1f, dt * (1.2f + 1.5f * flow))
                    b.vx += tilt.x * 220f * density * b.depth * dt
                    b.vy -= tilt.y * 120f * density * b.depth * dt
                    if (touch != null) {
                        val dx = b.x - touch.x
                        val dy = b.y - touch.y
                        val d = sqrt(dx * dx + dy * dy)
                        if (d < reach && d > 0.5f) {
                            val f = (1f - d / reach) * 1600f * density * dt
                            b.vx += dx / d * f
                            b.vy += dy / d * f
                        }
                    }
                    if (newBurst) {
                        val p = Touch.burstAt.value
                        val dx = b.x - p.x
                        val dy = b.y - p.y
                        val d = sqrt(dx * dx + dy * dy)
                        if (d < reach * 2f && d > 0.5f) {
                            val f = (1f - d / (reach * 2f)) * 260f * density
                            b.vx += dx / d * f
                            b.vy += dy / d * f
                        }
                    }
                    val prevY = b.y
                    b.x += b.vx * dt
                    b.y += b.vy * dt
                    if (flow > 0.2f) {
                        for ((bi, by) in bands.withIndex()) {
                            if (prevY > by && b.y <= by && b.lastBand != bi) {
                                b.flash = 1f
                                b.lastBand = bi
                                if (bi == 0) b.passed = 1f
                            }
                        }
                    }
                    b.flash = max(0f, b.flash - dt * 1.6f)
                    if (flow < 0.2f) b.passed = max(0f, b.passed - dt * 0.8f)
                    if (b.x < b.r) {
                        b.x = b.r
                        b.vx = kotlin.math.abs(b.vx) * 0.5f
                    } else if (b.x > w - b.r) {
                        b.x = w - b.r
                        b.vx = -kotlin.math.abs(b.vx) * 0.5f
                    }
                    if (flow < 0.15f) {
                        if (b.y < b.r) {
                            b.y = b.r
                            b.vy = kotlin.math.abs(b.vy) * 0.5f
                        } else if (b.y > h - b.r) {
                            b.y = h - b.r
                            b.vy = -kotlin.math.abs(b.vy) * 0.5f
                        }
                    }
                    if (b.y > h + b.r * 2f) b.y = h + b.r * 2f
                    if (b.y < -b.r * 2f) {
                        b.y = h + b.r + rnd.nextFloat() * 40f * density
                        b.x = b.r + rnd.nextFloat() * max(1f, w - 2f * b.r)
                        b.vx = 0f
                        b.vy = -40f * density * flow
                        b.lastBand = -1
                        b.passed = 0f
                    }
                }
                val gap = 3f * density
                repeat(2) {
                    for (i in bubbles.indices) {
                        val a = bubbles[i]
                        for (j in i + 1 until bubbles.size) {
                            val b = bubbles[j]
                            val dx = b.x - a.x
                            val dy = b.y - a.y
                            val min = a.r + b.r + gap
                            val d2 = dx * dx + dy * dy
                            if (d2 < min * min) {
                                val d = max(0.01f, sqrt(d2))
                                val nx = dx / d
                                val ny = dy / d
                                val push = (min - d) / 2f
                                val wa = b.r / (a.r + b.r)
                                val wb = a.r / (a.r + b.r)
                                a.x -= nx * push * 2f * wa
                                a.y -= ny * push * 2f * wa
                                b.x += nx * push * 2f * wb
                                b.y += ny * push * 2f * wb
                                val rv = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
                                if (rv < 0f) {
                                    val imp = rv * 0.8f
                                    a.vx += nx * imp * wa
                                    a.vy += ny * imp * wa
                                    b.vx -= nx * imp * wb
                                    b.vy -= ny * imp * wb
                                }
                            }
                        }
                    }
                }
                frame = now
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        bounds = size
        frame
        val rim = if (c.dark) Color(0xFFB8ECFF) else Color.White
        val body = lerpTint(c.tint, c.ok, flow * 0.35f)
        for (b in bubbles) {
            val center = Offset(b.x, b.y)
            val a = 0.35f + 0.65f * b.depth
            val col = lerpTint(body, c.ok, b.passed * flow)
            if (b.passed > 0.01f && flow > 0.05f) {
                drawCircle(
                    Brush.radialGradient(listOf(c.ok.copy(alpha = 0.22f * a * b.passed * flow), Color.Transparent), center, b.r * 2.2f),
                    b.r * 2.2f, center
                )
            }
            drawCircle(
                Brush.radialGradient(
                    0f to col.copy(alpha = 0.04f * a),
                    0.72f to col.copy(alpha = 0.14f * a),
                    1f to col.copy(alpha = 0.40f * a),
                    center = center,
                    radius = b.r,
                ),
                b.r, center,
            )
            drawCircle(rim.copy(alpha = 0.6f * a), b.r - 0.6f * density, center, style = Stroke(1.1f * density))
            val hl = Offset(b.x - b.r * 0.38f, b.y - b.r * 0.42f)
            drawOval(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f * a), Color.White.copy(alpha = 0f)), hl, b.r * 0.42f),
                topLeft = Offset(hl.x - b.r * 0.38f, hl.y - b.r * 0.24f),
                size = Size(b.r * 0.76f, b.r * 0.48f),
            )
            drawArc(
                rim.copy(alpha = 0.35f * a),
                20f, 70f, false,
                topLeft = Offset(b.x - b.r * 0.72f, b.y - b.r * 0.72f),
                size = Size(b.r * 1.44f, b.r * 1.44f),
                style = Stroke(1.6f * density),
            )
            if (b.flash > 0.01f) {
                val fr = b.r * (1.2f + 1.6f * (1f - b.flash))
                drawCircle(c.ok.copy(alpha = 0.55f * b.flash), fr, center, style = Stroke(1.4f * density))
            }
        }
    }
}

private fun lerpTint(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k, a.blue + (b.blue - a.blue) * k, 1f)
}
