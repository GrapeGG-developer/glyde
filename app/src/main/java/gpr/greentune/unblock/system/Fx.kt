package gpr.greentune.unblock.system

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import gpr.greentune.unblock.R
import kotlin.math.sqrt

object Fx {
    private var pool: SoundPool? = null
    private val ids = HashMap<Int, Int>()
    private val ready = HashSet<Int>()
    private val pending = HashMap<Int, Float>()
    @Volatile
    var visible = false
    private var lastStatusSound = 0L

    fun status(context: Context, prev: gpr.greentune.unblock.core.EngineStatus, now: gpr.greentune.unblock.core.EngineStatus) {
        val s = gpr.greentune.unblock.data.SettingsStore.value
        if (!s.sound || !visible) return
        val t = SystemClock.elapsedRealtime()
        val on = gpr.greentune.unblock.core.EngineStatus.On
        val res = when {
            now == on && prev != on -> R.raw.chime_on
            now == gpr.greentune.unblock.core.EngineStatus.Off && prev == on -> R.raw.chime_off
            now is gpr.greentune.unblock.core.EngineStatus.Error && prev !is gpr.greentune.unblock.core.EngineStatus.Error -> R.raw.error
            else -> return
        }
        if (t - lastStatusSound < 400) return
        lastStatusSound = t
        play(context, res, if (res == R.raw.error) 0.8f else 1f)
    }

    @Synchronized
    fun preload(context: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            .build()
        p.setOnLoadCompleteListener { sp, sampleId, status ->
            synchronized(this) {
                if (status == 0) {
                    ready.add(sampleId)
                    pending.remove(sampleId)?.let { v -> sp.play(sampleId, v, v, 1, 0, 1f) }
                }
            }
        }
        val app = context.applicationContext
        for (res in listOf(R.raw.chime_on, R.raw.chime_off, R.raw.tune_done, R.raw.error, R.raw.tap)) {
            try {
                ids[res] = p.load(app, res, 1)
            } catch (_: Exception) {
            }
        }
        pool = p
    }

    @Synchronized
    private fun play(context: Context, res: Int, volume: Float = 1f) {
        try {
            preload(context)
            val p = pool ?: return
            val id = ids[res] ?: return
            if (id in ready) p.play(id, volume, volume, 1, 0, 1f) else pending[id] = volume
        } catch (_: Exception) {
        }
    }

    fun chime(context: Context, on: Boolean) = play(context, if (on) R.raw.chime_on else R.raw.chime_off)

    fun done(context: Context) = play(context, R.raw.tune_done, 0.85f)

    fun error(context: Context) = play(context, R.raw.error, 0.8f)

    fun tap(context: Context) = play(context, R.raw.tap, 0.55f)

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
