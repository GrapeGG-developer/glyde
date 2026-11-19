package gpr.greentune.unblock.ui.aero

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import gpr.greentune.unblock.R

enum class OrbState { Off, Starting, On, Error }

@Composable
fun Orb(state: OrbState, onClick: () -> Unit, description: String, modifier: Modifier = Modifier, size: Dp = 230.dp) {
    val c = Aero.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "sc")
    val t = rememberInfiniteTransition(label = "orb")
    val breathe by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "s")
    val ripple by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "r")
    val onA by animateFloatAsState(if (state == OrbState.On) 1f else 0f, tween(250), label = "on")
    val glowColor = when (state) {
        OrbState.Error -> c.danger
        else -> c.tint
    }
    Box(modifier.size(size * 1.32f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val baseR = size.toPx() / 2
            val glowA = when (state) {
                OrbState.On -> 0.55f * breathe
                OrbState.Starting -> 0.35f
                OrbState.Error -> 0.35f
                OrbState.Off -> 0.10f
            }
            drawCircle(
                Brush.radialGradient(listOf(glowColor.copy(alpha = glowA), Color.Transparent), center, baseR * 1.32f),
                baseR * 1.32f, center
            )
            if (state == OrbState.On) {
                val rr = baseR * (1f + 0.3f * ripple)
                drawCircle(glowColor.copy(alpha = 0.45f * (1f - ripple) * onA), rr, center, style = Stroke(2.dp.toPx()))
            }
            if (state == OrbState.Starting) {
                val ringR = baseR * 1.1f
                val sw = 4.dp.toPx()
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, glowColor, Color.White), center),
                    spin, 250f, false,
                    topLeft = Offset(center.x - ringR, center.y - ringR),
                    size = Size(ringR * 2, ringR * 2),
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
        }
        Box(
            Modifier
                .size(size)
                .scale(scale)
                .clip(CircleShape)
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = state == OrbState.On || state == OrbState.Starting, animationSpec = tween(250), label = "img") { on ->
                Image(
                    painterResource(if (on) R.drawable.orb_on else R.drawable.orb_off),
                    null,
                    Modifier.fillMaxSize().padding(2.dp)
                )
            }
        }
    }
}
