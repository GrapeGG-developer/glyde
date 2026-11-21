package gpr.greentune.air

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

object Motion {
    val tilt = mutableStateOf(Offset.Zero)
}

@Composable
fun TiltSensor(enabled: Boolean) {
    val context = LocalContext.current
    LifecycleResumeEffect(enabled) {
        if (!enabled) {
            Motion.tilt.value = Offset.Zero
            return@LifecycleResumeEffect onPauseOrDispose { }
        }
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val wm = context.getSystemService(WindowManager::class.java)
        var fx = 0f
        var fy = 0f
        var bx = Float.NaN
        var by = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val rot = try {
                    if (Build.VERSION.SDK_INT >= 30) {
                        context.display?.rotation ?: Surface.ROTATION_0
                    } else {
                        @Suppress("DEPRECATION")
                        wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
                    }
                } catch (_: Throwable) {
                    Surface.ROTATION_0
                }
                val ax = e.values[0] / SensorManager.GRAVITY_EARTH
                val ay = e.values[1] / SensorManager.GRAVITY_EARTH
                val (gx, gy) = when (rot) {
                    Surface.ROTATION_90 -> -ay to ax
                    Surface.ROTATION_180 -> -ax to -ay
                    Surface.ROTATION_270 -> ay to -ax
                    else -> ax to ay
                }
                fx += (gx - fx) * 0.18f
                fy += (gy - fy) * 0.18f
                if (bx.isNaN()) {
                    bx = fx
                    by = fy
                }
                bx += (fx - bx) * 0.006f
                by += (fy - by) * 0.006f
                val tx = (-(fx - bx) * 2.4f).coerceIn(-1f, 1f)
                val ty = ((fy - by) * 2.4f).coerceIn(-1f, 1f)
                val cur = Motion.tilt.value
                if (kotlin.math.abs(cur.x - tx) > 0.004f || kotlin.math.abs(cur.y - ty) > 0.004f) {
                    Motion.tilt.value = Offset(tx, ty)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onPauseOrDispose {
            sm?.unregisterListener(listener)
        }
    }
}

@Composable
fun rememberShaderTime(fps: Int = 60): State<Float> {
    val state = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(fps) {
        val start = withFrameNanos { it }
        val startOffset = (System.currentTimeMillis() % 3_600_000L) * 1_000_000L
        var last = 0L
        val step = 1_000_000_000L / fps - 2_000_000L
        while (true) {
            withFrameNanos { now ->
                if (now - last >= step) {
                    last = now
                    state.floatValue = ((now - start + startOffset) / 1_000_000_000.0 % 3600.0).toFloat()
                }
            }
        }
    }
    return state
}
