package gpr.greentune.unblock.system

import android.net.TrafficStats
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10

data class Speed(val down: Long = 0, val up: Long = 0) {
    val level: Float
        get() {
            val total = (down + up).coerceAtLeast(1)
            return ((log10(total.toDouble()) - 3.0) / 4.0).toFloat().coerceIn(0f, 1f)
        }
}

object Traffic {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val _speed = MutableStateFlow(Speed())
    val speed: StateFlow<Speed> get() = _speed

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var rx = TrafficStats.getTotalRxBytes()
            var tx = TrafficStats.getTotalTxBytes()
            var t = SystemClock.elapsedRealtime()
            var down = 0.0
            var up = 0.0
            while (isActive) {
                delay(500)
                val nrx = TrafficStats.getTotalRxBytes()
                val ntx = TrafficStats.getTotalTxBytes()
                val nt = SystemClock.elapsedRealtime()
                val dt = ((nt - t).coerceAtLeast(1)) / 1000.0
                if (nrx >= 0 && ntx >= 0 && rx >= 0 && tx >= 0) {
                    down = down * 0.4 + ((nrx - rx).coerceAtLeast(0) / dt) * 0.6
                    up = up * 0.4 + ((ntx - tx).coerceAtLeast(0) / dt) * 0.6
                    _speed.value = Speed(down.toLong(), up.toLong())
                }
                rx = nrx
                tx = ntx
                t = nt
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _speed.value = Speed()
    }

    fun format(bps: Long): String = when {
        bps >= 1_048_576 -> String.format(java.util.Locale.US, "%.1f МБ/с", bps / 1_048_576.0)
        bps >= 1024 -> String.format(java.util.Locale.US, "%.0f КБ/с", bps / 1024.0)
        else -> "$bps Б/с"
    }
}
