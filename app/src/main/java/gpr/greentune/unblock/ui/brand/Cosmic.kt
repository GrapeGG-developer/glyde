package gpr.greentune.unblock.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CosmicMark(kind: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        when (kind) {
            "planet" -> planet()
            "galaxy" -> galaxy()
            "comet" -> comet()
            "moon" -> moon()
            "nebula" -> nebula()
            else -> star()
        }
    }
}

private fun DrawScope.glow(color: Color, r: Float, at: Offset = center) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), at, r), r, at)
}

private fun DrawScope.star() {
    val r = size.minDimension / 2f
    glow(Color(0xFFFFE08A), r)
    val p = Path()
    for (i in 0 until 8) {
        val a = -Math.PI / 2 + i * Math.PI / 4
        val rr = if (i % 2 == 0) r * 0.95f else r * 0.28f
        val x = center.x + (cos(a) * rr).toFloat()
        val y = center.y + (sin(a) * rr).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, Brush.radialGradient(listOf(Color.White, Color(0xFFFFD54F), Color(0xFFFF9800)), center, r))
}

private fun DrawScope.planet() {
    val r = size.minDimension / 2f
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE0B2), Color(0xFFFF8A65), Color(0xFF8D3B1F)), Offset(center.x - r * 0.25f, center.y - r * 0.3f), r), r * 0.58f, center)
    rotate(-22f) {
        drawOval(Color(0xFFFFE6C7), topLeft = Offset(center.x - r * 0.98f, center.y - r * 0.2f), size = Size(r * 1.96f, r * 0.4f), style = Stroke(r * 0.12f))
    }
}

private fun DrawScope.galaxy() {
    val r = size.minDimension / 2f
    glow(Color(0xFFB39DFF), r)
    for (arm in 0 until 2) {
        val p = Path()
        for (i in 0..20) {
            val t = i / 20f
            val a = arm * Math.PI + t * 3.4
            val rr = r * (0.12f + 0.82f * t)
            val x = center.x + (cos(a) * rr).toFloat()
            val y = center.y + (sin(a) * rr * 0.62f).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, Color(0xFFD9CCFF), style = Stroke(r * 0.16f, cap = StrokeCap.Round))
    }
    drawCircle(Color.White, r * 0.2f, center)
}

private fun DrawScope.comet() {
    val r = size.minDimension / 2f
    val head = Offset(center.x + r * 0.45f, center.y - r * 0.45f)
    drawLine(
        Brush.linearGradient(listOf(Color(0xFF80DEEA), Color.Transparent), head, Offset(center.x - r, center.y + r)),
        head, Offset(center.x - r * 0.9f, center.y + r * 0.9f), r * 0.4f, StrokeCap.Round
    )
    glow(Color(0xFF80DEEA), r * 0.7f, head)
    drawCircle(Color.White, r * 0.26f, head)
}

private fun DrawScope.moon() {
    val r = size.minDimension / 2f
    glow(Color(0xFFCFD8DC), r)
    val outer = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r * 0.8f)) }
    val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(center.x + r * 0.38f, center.y - r * 0.22f), r * 0.7f)) }
    val crescent = Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, outer, cut)
    drawPath(crescent, Brush.linearGradient(listOf(Color.White, Color(0xFFB0BEC5)), Offset(0f, 0f), Offset(size.width, size.height)))
}

private fun DrawScope.nebula() {
    val r = size.minDimension / 2f
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFF80AB).copy(alpha = 0.9f), Color.Transparent), Offset(center.x - r * 0.25f, center.y), r * 0.8f), r * 0.8f, Offset(center.x - r * 0.25f, center.y))
    drawCircle(Brush.radialGradient(listOf(Color(0xFF82B1FF).copy(alpha = 0.9f), Color.Transparent), Offset(center.x + r * 0.3f, center.y - r * 0.2f), r * 0.75f), r * 0.75f, Offset(center.x + r * 0.3f, center.y - r * 0.2f))
    drawCircle(Color.White, r * 0.12f, Offset(center.x + r * 0.1f, center.y + r * 0.1f))
}
