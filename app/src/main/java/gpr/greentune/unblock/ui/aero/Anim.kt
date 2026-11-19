package gpr.greentune.unblock.ui.aero

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

object GGV7 {
    const val NAME = "Glyde Glass Vista-7"
    val windowCorner = 16.dp
    val tileCorner = 18.dp
    val blur = 34.dp
    const val glassAlpha = 0.30f
    const val glassAlphaFallback = 0.9f
    const val contentAlpha = 0.50f
    const val contentAlphaFallback = 0.95f
    const val staggerMs = 38L
}

@Composable
fun Modifier.staggerIn(index: Int, enabled: Boolean = true): Modifier {
    val a = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enabled) {
            delay(GGV7.staggerMs * index)
            a.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 240f))
        }
    }
    return graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 26.dp.toPx()
        val s = 0.88f + 0.12f * v
        scaleX = s
        scaleY = s
    }
}

@Composable
fun Modifier.pressBounce(source: MutableInteractionSource, depth: Float = 0.94f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) depth else 1f, spring(dampingRatio = 0.42f, stiffness = 520f), label = "pb")
    return graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

fun stage(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)
