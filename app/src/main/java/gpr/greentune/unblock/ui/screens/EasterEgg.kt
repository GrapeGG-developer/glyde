package gpr.greentune.unblock.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gpr.greentune.unblock.R
import gpr.greentune.unblock.system.Fx
import gpr.greentune.air.GlydeSans
import kotlinx.coroutines.launch

private val Iridescent = listOf(
    Color(0xFF2EE59D), Color(0xFF19A7CE), Color(0xFF7B6CFF), Color(0xFFE05CFF),
    Color(0xFFFF6FA8), Color(0xFFFFC857), Color(0xFF2EE59D),
)

@Composable
fun EggLogo(size: Int = 120) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var taps by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(false) }
    val spin = remember { Animatable(0f) }
    val bounce = remember { Animatable(1f) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painterResource(R.drawable.logo_glass), null,
            Modifier
                .size(size.dp)
                .graphicsLayer {
                    rotationY = spin.value
                    scaleX = bounce.value
                    scaleY = bounce.value
                }
                .clickable(remember { MutableInteractionSource() }, null) {
                    taps++
                    Fx.haptic(view, taps >= 5)
                    scope.launch {
                        bounce.snapTo(0.86f)
                        bounce.animateTo(1f, spring(0.35f, 500f))
                    }
                    if (taps >= 5 && !open) {
                        open = true
                        scope.launch {
                            spin.animateTo(720f, tween(1400))
                            spin.snapTo(0f)
                        }
                    }
                }
        )
        AnimatedVisibility(open, enter = fadeIn(tween(500)) + scaleIn(spring(0.45f, 300f), initialScale = 0.4f)) {
            ShimmerText("GreenTune by GrapeGG", Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun ShimmerText(text: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val t = rememberInfiniteTransition(label = "egg")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "s")
    val glow by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "g")
    val span = with(density) { 260.dp.toPx() }
    val brush = Brush.linearGradient(
        Iridescent,
        start = Offset(span * shift * 2 - span * 2, 0f),
        end = Offset(span * shift * 2, span * 0.25f),
        tileMode = TileMode.Repeated,
    )
    Text(
        text,
        modifier = modifier,
        style = TextStyle(
            brush = brush,
            fontFamily = GlydeSans,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            shadow = Shadow(Color(0xFF7B6CFF).copy(alpha = 0.55f * glow), Offset(0f, 2f), 18f * glow),
        )
    )
}
