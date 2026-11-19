package gpr.greentune.unblock.ui.aero

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AquaFx {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= 33
    var broken by mutableStateOf(false)
}

@Composable
fun AquaBackground(enabled: Boolean, energy: Float, modifier: Modifier = Modifier) {
    if (enabled && AquaFx.supported && !AquaFx.broken) {
        ShaderBackground(energy, modifier)
    } else {
        AeroWallpaper(modifier)
    }
}

@RequiresApi(33)
@Composable
private fun ShaderBackground(energy: Float, modifier: Modifier) {
    val c = Aero.colors
    val shader = remember {
        try {
            RuntimeShader(Shaders.BACKGROUND)
        } catch (_: Throwable) {
            AquaFx.broken = true
            null
        }
    } ?: return AeroWallpaper(modifier)
    val brush = remember(shader) { ShaderBrush(shader) }
    val time by rememberShaderTime(30)
    val e by animateFloatAsState(energy, tween(900), label = "e")
    Canvas(modifier.fillMaxSize()) {
        try {
            val tilt = Motion.tilt.value
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("tilt", tilt.x, tilt.y)
            shader.setFloatUniform("tint", c.tint.red, c.tint.green, c.tint.blue, 1f)
            shader.setFloatUniform("dark", if (c.dark) 1f else 0f)
            shader.setFloatUniform("energy", e)
            drawRect(brush)
        } catch (_: Throwable) {
            AquaFx.broken = true
        }
    }
}

@Composable
fun Orb3D(
    state: OrbState,
    onClick: () -> Unit,
    description: String,
    effects: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 230.dp,
) {
    Orb(state, onClick, description, modifier, size)
}

@RequiresApi(33)
@Composable
private fun ShaderOrb(state: OrbState, onClick: () -> Unit, description: String, modifier: Modifier, size: Dp) {
    val c = Aero.colors
    val shader = remember {
        try {
            RuntimeShader(Shaders.ORB)
        } catch (_: Throwable) {
            AquaFx.broken = true
            null
        }
    }
    if (shader == null) {
        Orb(state, onClick, description, modifier, size)
        return
    }
    val brush = remember(shader) { ShaderBrush(shader) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "p")
    val energy by animateFloatAsState(
        when (state) {
            OrbState.On -> 1f
            OrbState.Starting -> 0.55f
            OrbState.Error -> 0.25f
            OrbState.Off -> 0f
        },
        spring(dampingRatio = 0.6f, stiffness = 60f), label = "en"
    )
    val spin by animateFloatAsState(if (state == OrbState.Starting) 1f else 0f, tween(400), label = "sp")
    val tint by animateColorAsState(if (state == OrbState.Error) c.danger else c.tint, tween(400), label = "tn")
    val time by rememberShaderTime(60)
    val box = size * 1.35f
    Box(modifier.size(box), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            try {
                val tilt = Motion.tilt.value
                shader.setFloatUniform("size", this.size.width, this.size.height)
                shader.setFloatUniform("time", time)
                shader.setFloatUniform("tilt", tilt.x, tilt.y)
                shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, 1f)
                shader.setFloatUniform("energy", energy)
                shader.setFloatUniform("spin", spin)
                shader.setFloatUniform("press", press)
                shader.setFloatUniform("dark", if (c.dark) 1f else 0f)
                drawRect(brush)
            } catch (_: Throwable) {
                AquaFx.broken = true
            }
        }
        Box(
            Modifier
                .size(size * 0.98f)
                .clip(CircleShape)
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
        )
    }
}

fun aquaHighlight(tilt: Offset, w: Float, h: Float): Offset =
    Offset(w * (0.5f - 0.35f * tilt.x), h * (0.1f - 0.25f * tilt.y))

val AquaWhite = Color(0xFFF7FCFF)
