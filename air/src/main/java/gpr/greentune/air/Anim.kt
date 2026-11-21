package gpr.greentune.air

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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

object AirSpec {
    const val NAME = "Air"
    val windowCorner = 16.dp
    val tileCorner = 18.dp
    val blur = 34.dp
    const val glassAlpha = 0.30f
    const val glassAlphaFallback = 0.9f
    const val contentAlpha = 0.50f
    const val contentAlphaFallback = 0.95f
    const val staggerMs = 30L
}

@Composable
fun Modifier.staggerIn(index: Int, enabled: Boolean = true): Modifier {
    val a = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enabled) {
            delay(AirSpec.staggerMs * index)
            a.animateTo(1f, tween(360, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
    }
    return graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 10.dp.toPx()
        val s = 0.98f + 0.02f * v
        scaleX = s
        scaleY = s
    }
}

@Composable
fun Modifier.pressBounce(source: MutableInteractionSource, depth: Float = 0.97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val target = 1f - (1f - depth) * 0.5f
    val s by animateFloatAsState(if (pressed) target else 1f, spring(dampingRatio = 1f, stiffness = 900f), label = "pb")
    return graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

fun stage(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)
