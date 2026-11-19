package gpr.greentune.unblock.vpn

import android.app.Service
import android.content.Intent
import android.os.IBinder
import gpr.greentune.unblock.R
import gpr.greentune.unblock.core.Ciadpi
import gpr.greentune.unblock.core.Engine
import gpr.greentune.unblock.core.EngineStatus
import gpr.greentune.unblock.core.Logs
import gpr.greentune.unblock.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

class ProxyService : Service() {
    companion object {
        @Volatile
        var instance: ProxyService? = null
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var proc: Process? = null
    @Volatile
    private var generation = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.startForeground(this, Notifications.engine(this, ProxyService::class.java, getString(R.string.status_connecting)), false)
        if (intent?.action == GtVpnService.ACTION_STOP) {
            SettingsStore.update { it.copy(shouldRun = false) }
            shutdown()
            return START_NOT_STICKY
        }
        if (Engine.status.value != EngineStatus.On) scope.launch { mutex.withLock { startEngine() } }
        return START_STICKY
    }

    fun reload() {
        scope.launch { mutex.withLock { startEngine() } }
    }

    fun shutdown() {
        scope.launch {
            mutex.withLock { stopEngine() }
            Engine.setStatus(this@ProxyService, EngineStatus.Off)
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        instance = null
        stopEngine()
        scope.cancel()
        val st = Engine.status.value
        if (st == EngineStatus.On || st == EngineStatus.Starting) Engine.setStatus(this, EngineStatus.Off)
        super.onDestroy()
    }

    private suspend fun startEngine() {
        stopEngine()
        Engine.setStatus(this, EngineStatus.Starting)
        val gen = ++generation
        try {
            val s = SettingsStore.value
            val port = s.proxyPort
            var free = false
            for (i in 0 until 10) {
                if (Ciadpi.isPortFree(port)) {
                    free = true
                    break
                }
                kotlinx.coroutines.delay(150)
            }
            if (!free) throw IllegalStateException(getString(R.string.err_port_busy, port))
            val args = Engine.currentArgs()
            Logs.append(this, "start proxy port=$port args=${args.joinToString(" ")}")
            val p = withContext(Dispatchers.IO) { Ciadpi.start(this@ProxyService, port, args, s.blockQuic, Logs.engineLog(this@ProxyService)) }
            proc = p
            if (!Ciadpi.awaitReady(port, p, 5000)) throw IllegalStateException(getString(R.string.err_engine_start))
            Engine.setStatus(this, EngineStatus.On)
            Notifications.update(this, Notifications.engine(this, ProxyService::class.java, getString(R.string.status_proxy_on, port)))
            thread(name = "ciadpi-proxy-watch", isDaemon = true) {
                try {
                    p.waitFor()
                } catch (_: InterruptedException) {
                    return@thread
                }
                if (gen == generation && Engine.status.value == EngineStatus.On) reload()
            }
        } catch (e: Exception) {
            Logs.append(this, "error: ${e.message}")
            stopEngine()
            Engine.setStatus(this, EngineStatus.Error(e.message ?: getString(R.string.err_unknown)))
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopEngine() {
        generation++
        Ciadpi.stop(proc)
        proc = null
    }
}
