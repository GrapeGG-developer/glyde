package gpr.greentune.unblock.ui.aero

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
)

@Composable
fun Bubbles(energy: Float, lite: Boolean, modifier: Modifier = Modifier) {
    val c = Aero.colors
    val density = LocalDensity.current.density
    val bubbles = remember { ArrayList<Bubble>() }
    var frame by remember { mutableLongStateOf(0L) }
    var bounds by remember { mutableStateOf(Size.Zero) }
    val count = if (lite) 5 else 10
    val speedTarget = 0.12f + 0.88f * energy.coerceIn(0f, 1f)
    var speed by remember { mutableStateOf(speedTarget) }
    LaunchedEffect(count) {
        var last = 0L
        var t = 0f
        var lastBurst = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                t += dt
                val w = bounds.width
                val h = bounds.height
                if (w <= 0f || h <= 0f) return@withFrameNanos
                val rnd = Random
                while (bubbles.size < count) {
                    val depth = 0.35f + 0.65f * rnd.nextFloat()
                    val r = (4f + 13f * depth * depth + rnd.nextFloat() * 4f) * density
                    bubbles.add(Bubble(rnd.nextFloat() * w, if (bubbles.size < count / 2 || frame == 0L) rnd.nextFloat() * h else h + r, 0f, 0f, r, depth, rnd.nextFloat() * 6.28f, 0.6f + rnd.nextFloat() * 0.9f))
                }
                while (bubbles.size > count) bubbles.removeAt(bubbles.size - 1)
                speed += (speedTarget - speed) * min(1f, dt * 1.5f)
                val tilt = if (lite) Offset.Zero else Motion.tilt.value
                val touch = Touch.point.value
                val burst = Touch.burst.value
                val newBurst = burst != lastBurst
                lastBurst = burst
                val reach = 110f * density
                for (b in bubbles) {
                    val rise = (18f + 30f * b.depth) * density * speed
                    val targetVy = -rise
                    b.vy += (targetVy - b.vy) * min(1f, dt * 1.8f)
                    val wob = sin(t * b.freq + b.phase) * 14f * density * (0.3f + speed)
                    b.vx += (wob - b.vx) * min(1f, dt * 1.2f)
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
                    b.x += b.vx * dt
                    b.y += b.vy * dt
                    if (b.x < b.r) {
                        b.x = b.r
                        b.vx = kotlin.math.abs(b.vx) * 0.5f
                    } else if (b.x > w - b.r) {
                        b.x = w - b.r
                        b.vx = -kotlin.math.abs(b.vx) * 0.5f
                    }
                    if (b.y > h + b.r * 2f) b.y = h + b.r * 2f
                    if (b.y < -b.r * 2f) {
                        b.y = h + b.r + rnd.nextFloat() * 40f * density
                        b.x = b.r + rnd.nextFloat() * max(1f, w - 2f * b.r)
                        b.vx = 0f
                        b.vy = 0f
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
        val body = c.tint
        for (b in bubbles) {
            val center = Offset(b.x, b.y)
            val a = 0.35f + 0.65f * b.depth
            drawCircle(
                Brush.radialGradient(
                    0f to body.copy(alpha = 0.02f * a),
                    0.72f to body.copy(alpha = 0.10f * a),
                    1f to body.copy(alpha = 0.32f * a),
                    center = center,
                    radius = b.r,
                ),
                b.r, center,
            )
            drawCircle(rim.copy(alpha = 0.55f * a), b.r - 0.6f * density, center, style = Stroke(1.1f * density))
            val hl = Offset(b.x - b.r * 0.38f, b.y - b.r * 0.42f)
            drawOval(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f * a), Color.White.copy(alpha = 0f)), hl, b.r * 0.42f),
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
        }
    }
}
