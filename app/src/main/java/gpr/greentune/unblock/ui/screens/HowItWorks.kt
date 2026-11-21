package gpr.greentune.unblock.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gpr.greentune.unblock.R
import gpr.greentune.air.Air
import gpr.greentune.air.AirButton
import gpr.greentune.air.AirDialog

@Composable
fun FlowDiagram(modifier: Modifier = Modifier, height: Int = 120) {
    val c = Air.colors
    val t = rememberInfiniteTransition(label = "flow")
    val phase by t.animateFloat(0f, 2f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "ph")
    val you = stringResource(R.string.how_you)
    val dpi = stringResource(R.string.how_dpi)
    val site = stringResource(R.string.how_site)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(height.dp)) {
            val w = size.width
            val h = size.height
            val cy = h * 0.5f
            val nodeR = minOf(h * 0.26f, w * 0.09f)
            val xs = listOf(nodeR * 1.3f, w / 2f, w - nodeR * 1.3f)
            val blocked = phase < 1f
            val p = phase % 1f
            drawLine(c.subText.copy(alpha = 0.35f), Offset(xs[0], cy), Offset(xs[2], cy), 2.dp.toPx())
            node(Offset(xs[0], cy), nodeR, c.tint, c.dark)
            node(Offset(xs[2], cy), nodeR, if (blocked) c.subText else c.ok, c.dark)
            val wallW = nodeR * 0.55f
            val wallH = nodeR * 2.3f
            drawRoundRect(
                Brush.verticalGradient(listOf(c.danger.copy(alpha = 0.85f), c.danger.copy(alpha = 0.55f))),
                topLeft = Offset(xs[1] - wallW / 2, cy - wallH / 2),
                size = Size(wallW, wallH),
                cornerRadius = CornerRadius(wallW / 3, wallW / 3),
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.45f),
                topLeft = Offset(xs[1] - wallW / 2 + 2.dp.toPx(), cy - wallH / 2 + 2.dp.toPx()),
                size = Size(wallW * 0.35f, wallH - 4.dp.toPx()),
                cornerRadius = CornerRadius(wallW / 4, wallW / 4),
            )
            val pr = nodeR * 0.22f
            for (k in 0 until 3) {
                val q = ((p * 1.4f) - k * 0.14f)
                if (q < 0f || q > 1f) continue
                if (blocked) {
                    val stop = xs[1] - wallW / 2 - pr
                    val x = xs[0] + nodeR + (stop - xs[0] - nodeR) * minOf(1f, q * 1.25f)
                    val bounce = if (q > 0.8f) (q - 0.8f) / 0.2f else 0f
                    drawCircle(c.tint.copy(alpha = 1f - bounce), pr, Offset(x - bounce * nodeR * 0.6f, cy))
                } else {
                    val x = xs[0] + nodeR + (xs[2] - nodeR - xs[0] - nodeR) * q
                    val near = 1f - minOf(1f, kotlin.math.abs(x - xs[1]) / (nodeR * 1.4f))
                    val split = near * pr * 1.1f
                    drawCircle(c.ok, pr * (1f - near * 0.35f), Offset(x, cy - split))
                    if (near > 0.02f) drawCircle(c.ok.copy(alpha = 0.9f), pr * 0.6f, Offset(x - pr * near, cy + split))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(you, fontSize = 13.sp, color = c.subText, modifier = Modifier.padding(start = 6.dp))
            Text(dpi, fontSize = 13.sp, color = c.subText)
            Text(site, fontSize = 13.sp, color = c.subText, modifier = Modifier.padding(end = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(if (phase < 1f) R.string.how_block else R.string.how_pass),
                fontSize = 14.sp, color = c.text, textAlign = TextAlign.Center,
            )
        }
    }
}

private fun DrawScope.node(center: Offset, r: Float, color: Color, dark: Boolean) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), center, r * 1.6f), r * 1.6f, center)
    drawCircle(Brush.verticalGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.65f)), center.y - r, center.y + r), r, center)
    drawOval(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.05f)), center.y - r * 0.92f, center.y),
        topLeft = Offset(center.x - r * 0.68f, center.y - r * 0.92f),
        size = Size(r * 1.36f, r * 0.9f),
    )
    drawCircle(Color.White.copy(alpha = if (dark) 0.35f else 0.7f), r, center, style = Stroke(1.2f))
}

@Composable
fun HowItWorksDialog(onDismiss: () -> Unit) {
    val c = Air.colors
    AirDialog(
        title = stringResource(R.string.how_title),
        onDismiss = onDismiss,
        buttons = { AirButton(stringResource(R.string.how_got_it), onClick = onDismiss, primary = true) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowDiagram()
            Text(stringResource(R.string.how_note), fontSize = 13.sp, color = c.subText)
        }
    }
}

@Composable
fun OnboardingContent(onDone: () -> Unit) {
    val c = Air.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.onb_hello), fontSize = 28.sp, fontWeight = FontWeight.Light, color = c.text, textAlign = TextAlign.Center)
        Text(stringResource(R.string.onb_one), fontSize = 15.sp, color = c.text, textAlign = TextAlign.Center)
        Text(stringResource(R.string.onb_not_vpn), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        FlowDiagram()
        Text(stringResource(R.string.how_note), fontSize = 12.5.sp, color = c.subText, textAlign = TextAlign.Center)
        AirButton(stringResource(R.string.onb_start), onClick = onDone, primary = true, modifier = Modifier.fillMaxWidth())
    }
}
