package gpr.greentune.unblock.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

class CosmicSpec(
    val kind: Int,
    val hue: Float,
    val hue2: Float,
    val tilt: Float,
    val arms: Int,
    val bands: Int,
    val moon: Float,
    val sparkles: List<Offset>,
) {
    companion object {
        fun of(name: String): CosmicSpec {
            var h = 0x811C9DC5.toInt()
            for (ch in name.lowercase()) {
                h = (h xor ch.code) * 16777619
            }
            var s = if (h == 0) 1 else h
            fun next(): Float {
                s = s xor (s shl 13)
                s = s xor (s ushr 17)
                s = s xor (s shl 5)
                return ((s ushr 8) and 0xFFFF) / 65535f
            }
            val kind = ((h ushr 3) and 0x7FFF) % 6
            val hue = next() * 360f
            val hue2 = (hue + 40f + next() * 140f) % 360f
            val tilt = -35f + next() * 70f
            val arms = 2 + (next() * 2.99f).toInt()
            val bands = 3 + (next() * 2.99f).toInt()
            val moon = next() * 360f
            val sparkles = List(3) { Offset(next() * 2f - 1f, next() * 2f - 1f) }
            return CosmicSpec(kind, hue, hue2, tilt, arms, bands, moon, sparkles)
        }
    }
}

@Composable
fun CosmicMark(name: String, modifier: Modifier = Modifier) {
    val spec = remember(name) { CosmicSpec.of(name) }
    Canvas(modifier) { drawCosmic(spec) }
}

fun DrawScope.drawCosmic(spec: CosmicSpec) {
    val r = size.minDimension / 2f
    when (spec.kind) {
        0 -> ringed(spec, r)
        1 -> giant(spec, r)
        2 -> galaxy(spec, r)
        3 -> binary(spec, r)
        4 -> nebula(spec, r)
        else -> moonPlanet(spec, r)
    }
}

private fun hsv(h: Float, s: Float, v: Float, a: Float = 1f) = Color.hsv(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f), a.coerceIn(0f, 1f))

private fun DrawScope.glow(color: Color, at: Offset, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), at, radius), radius, at)
}

private fun DrawScope.sphere(c: Offset, r: Float, hue: Float) {
    drawCircle(
        Brush.radialGradient(
            0f to hsv(hue, 0.25f, 1f),
            0.45f to hsv(hue, 0.65f, 0.9f),
            1f to hsv(hue, 0.85f, 0.35f),
            center = Offset(c.x - r * 0.35f, c.y - r * 0.4f),
            radius = r * 1.6f
        ),
        r, c
    )
}

private fun DrawScope.gloss(c: Offset, r: Float) {
    drawOval(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0f)), c.y - r * 0.92f, c.y - r * 0.1f),
        topLeft = Offset(c.x - r * 0.62f, c.y - r * 0.9f),
        size = Size(r * 1.24f, r * 0.8f)
    )
}

private fun DrawScope.sparkles(spec: CosmicSpec, r: Float, scale: Float = 0.9f) {
    for (p in spec.sparkles) {
        val at = Offset(center.x + p.x * r * scale, center.y + p.y * r * scale)
        drawCircle(Color.White.copy(alpha = 0.85f), r * 0.045f, at)
    }
}

private fun DrawScope.ringed(spec: CosmicSpec, r: Float) {
    val c = center
    val pr = r * 0.52f
    val ring = hsv(spec.hue2, 0.35f, 1f)
    rotate(spec.tilt, c) {
        drawArc(ring.copy(alpha = 0.55f), 180f, 180f, false, Offset(c.x - r * 0.95f, c.y - r * 0.24f), Size(r * 1.9f, r * 0.48f), style = Stroke(r * 0.09f))
    }
    sphere(c, pr, spec.hue)
    gloss(c, pr)
    rotate(spec.tilt, c) {
        drawArc(ring, 0f, 180f, false, Offset(c.x - r * 0.95f, c.y - r * 0.24f), Size(r * 1.9f, r * 0.48f), style = Stroke(r * 0.1f, cap = StrokeCap.Round))
        drawArc(Color.White.copy(alpha = 0.6f), 20f, 140f, false, Offset(c.x - r * 0.95f, c.y - r * 0.24f), Size(r * 1.9f, r * 0.48f), style = Stroke(r * 0.03f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.giant(spec: CosmicSpec, r: Float) {
    val c = center
    val pr = r * 0.8f
    sphere(c, pr, spec.hue)
    val clip = Path().apply { addOval(Rect(c, pr)) }
    clipPath(clip) {
        rotate(spec.tilt * 0.4f, c) {
            for (i in 0 until spec.bands) {
                val y = c.y - pr + (i + 0.6f) * (2f * pr / (spec.bands + 0.4f))
                drawOval(
                    hsv(if (i % 2 == 0) spec.hue2 else spec.hue, 0.55f, 0.95f, 0.38f),
                    topLeft = Offset(c.x - pr * 1.3f, y - pr * 0.08f),
                    size = Size(pr * 2.6f, pr * 0.16f + (i % 2) * pr * 0.07f)
                )
            }
        }
        drawCircle(
            Brush.radialGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f), center = Offset(c.x - pr * 0.2f, c.y - pr * 0.25f), radius = pr * 1.25f),
            pr, c
        )
    }
    gloss(c, pr)
}

private fun DrawScope.galaxy(spec: CosmicSpec, r: Float) {
    val c = center
    glow(hsv(spec.hue, 0.5f, 1f, 0.55f), c, r)
    rotate(spec.tilt, c) {
        for (a in 0 until spec.arms) {
            val path = Path()
            for (i in 0..24) {
                val t = i / 24f
                val ang = a * (6.2832f / spec.arms) + t * 3.6f
                val rr = r * (0.1f + 0.82f * t)
                val p = Offset(c.x + cos(ang) * rr, c.y + sin(ang) * rr * 0.6f)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, hsv(spec.hue2, 0.45f, 1f, 0.28f), style = Stroke(r * 0.22f, cap = StrokeCap.Round))
            drawPath(path, hsv(spec.hue2, 0.35f, 1f, 0.6f), style = Stroke(r * 0.09f, cap = StrokeCap.Round))
            drawPath(path, Color.White.copy(alpha = 0.4f), style = Stroke(r * 0.03f, cap = StrokeCap.Round))
        }
    }
    glow(Color.White, c, r * 0.32f)
    drawCircle(Color.White, r * 0.12f, c)
}

private fun DrawScope.binary(spec: CosmicSpec, r: Float) {
    val c = center
    rotate(spec.tilt, c) {
        drawOval(Color.White.copy(alpha = 0.35f), Offset(c.x - r * 0.8f, c.y - r * 0.3f), Size(r * 1.6f, r * 0.6f), style = Stroke(r * 0.035f))
        val a = Offset(c.x - r * 0.42f, c.y)
        val b = Offset(c.x + r * 0.5f, c.y)
        glow(hsv(spec.hue, 0.6f, 1f, 0.8f), a, r * 0.55f)
        glow(hsv(spec.hue2, 0.6f, 1f, 0.8f), b, r * 0.4f)
        drawCircle(Brush.radialGradient(listOf(Color.White, hsv(spec.hue, 0.5f, 1f)), a, r * 0.24f), r * 0.24f, a)
        drawCircle(Brush.radialGradient(listOf(Color.White, hsv(spec.hue2, 0.5f, 1f)), b, r * 0.16f), r * 0.16f, b)
    }
}

private fun DrawScope.nebula(spec: CosmicSpec, r: Float) {
    val c = center
    val rad = spec.moon * 0.01745f
    val p1 = Offset(c.x + cos(rad) * r * 0.3f, c.y + sin(rad) * r * 0.3f)
    val p2 = Offset(c.x - cos(rad) * r * 0.32f, c.y - sin(rad) * r * 0.26f)
    glow(hsv(spec.hue, 0.7f, 1f, 0.85f), p1, r * 0.75f)
    glow(hsv(spec.hue2, 0.7f, 1f, 0.8f), p2, r * 0.7f)
    glow(hsv((spec.hue + spec.hue2) / 2f, 0.4f, 1f, 0.6f), c, r * 0.5f)
    sparkles(spec, r, 0.75f)
    glow(Color.White, c, r * 0.28f)
    drawCircle(Color.White, r * 0.09f, c)
}

private fun DrawScope.moonPlanet(spec: CosmicSpec, r: Float) {
    val c = Offset(center.x - r * 0.1f, center.y + r * 0.05f)
    val pr = r * 0.66f
    glow(hsv(spec.hue, 0.5f, 1f, 0.35f), c, pr * 1.4f)
    sphere(c, pr, spec.hue)
    val clip = Path().apply { addOval(Rect(c, pr)) }
    clipPath(clip) {
        drawCircle(Color.Black.copy(alpha = 0.42f), pr * 1.05f, Offset(c.x + pr * 0.75f, c.y + pr * 0.55f))
        drawCircle(hsv(spec.hue, 0.6f, 0.5f, 0.35f), pr * 0.16f, Offset(c.x - pr * 0.3f, c.y + pr * 0.15f))
        drawCircle(hsv(spec.hue, 0.6f, 0.5f, 0.3f), pr * 0.1f, Offset(c.x + pr * 0.15f, c.y - pr * 0.35f))
    }
    gloss(c, pr)
    val rad = spec.moon * 0.01745f
    val m = Offset(center.x + cos(rad) * r * 0.78f, center.y + sin(rad) * r * 0.78f)
    sphere(m, r * 0.17f, spec.hue2)
}
