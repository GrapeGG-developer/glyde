package gpr.greentune.air

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

object AirFx {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= 33
    var broken by mutableStateOf(false)
}

@Composable
fun AirBackground(enabled: Boolean, energy: Float, modifier: Modifier = Modifier, pulse: Float = 0f, accent: Color = Air.colors.ok) {
    if (enabled && AirFx.supported && !AirFx.broken) {
        ShaderBackground(energy, pulse, accent, modifier)
    } else {
        AirWallpaper(modifier)
    }
}

@RequiresApi(33)
@Composable
private fun ShaderBackground(energy: Float, pulse: Float, accent: Color, modifier: Modifier) {
    val c = Air.colors
    val shader = remember {
        try {
            RuntimeShader(Shaders.BACKGROUND)
        } catch (_: Throwable) {
            AirFx.broken = true
            null
        }
    } ?: return AirWallpaper(modifier)
    val brush = remember(shader) { ShaderBrush(shader) }
    val time by rememberShaderTime(8)
    val e by animateFloatAsState(energy, tween(1600, easing = FastOutSlowInEasing), label = "e")
    val p by animateFloatAsState(pulse, tween(1500), label = "p")
    val ac by animateColorAsState(accent, tween(1600), label = "ac")
    Canvas(modifier.fillMaxSize()) {
        try {
            val tilt = Motion.tilt.value
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("tilt", tilt.x, tilt.y)
            shader.setFloatUniform("tint", c.tint.red, c.tint.green, c.tint.blue, 1f)
            shader.setFloatUniform("accent", ac.red, ac.green, ac.blue, 1f)
            shader.setFloatUniform("dark", if (c.dark) 1f else 0f)
            shader.setFloatUniform("energy", e)
            shader.setFloatUniform("pulse", p)
            drawRect(brush)
        } catch (_: Throwable) {
            AirFx.broken = true
        }
    }
}
