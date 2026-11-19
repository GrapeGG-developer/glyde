package gpr.greentune.unblock.system

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import gpr.greentune.unblock.R
import kotlin.math.sqrt

object Fx {
    fun chime(context: Context, on: Boolean) {
        try {
            val mp = MediaPlayer.create(
                context, if (on) R.raw.chime_on else R.raw.chime_off,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                0
            ) ?: return
            mp.setOnCompletionListener { it.release() }
            mp.start()
        } catch (_: Exception) {
        }
    }

    fun haptic(view: View, strong: Boolean = false) {
        view.performHapticFeedback(
            if (strong) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        )
    }
}

class ShakeDetector(context: Context, private val onShake: () -> Unit) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private var lastPeak = 0L
    private var peaks = 0
    private var lastFire = 0L

    fun start() {
        val acc = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sm?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0] / SensorManager.GRAVITY_EARTH
        val y = event.values[1] / SensorManager.GRAVITY_EARTH
        val z = event.values[2] / SensorManager.GRAVITY_EARTH
        val g = sqrt(x * x + y * y + z * z)
        val now = SystemClock.elapsedRealtime()
        if (g > 2.6f) {
            if (now - lastPeak > 1200) peaks = 0
            if (now - lastPeak > 120) {
                peaks++
                lastPeak = now
            }
            if (peaks >= 3 && now - lastFire > 3000) {
                peaks = 0
                lastFire = now
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
