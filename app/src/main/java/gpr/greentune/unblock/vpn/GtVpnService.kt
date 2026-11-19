package gpr.greentune.unblock.vpn

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import gpr.greentune.unblock.R
import gpr.greentune.unblock.core.Ciadpi
import gpr.greentune.unblock.core.Engine
import gpr.greentune.unblock.core.EngineStatus
import gpr.greentune.unblock.core.Logs
import gpr.greentune.unblock.core.TProxyService
import gpr.greentune.unblock.data.AppSettings
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.net.DnsProviders
import gpr.greentune.unblock.net.DohServer
import gpr.greentune.unblock.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.concurrent.thread

class GtVpnService : VpnService() {
    companion object {
        const val ACTION_START = "gpr.greentune.unblock.action.START"
        const val ACTION_STOP = "gpr.greentune.unblock.action.STOP"

        @Volatile
        var instance: GtVpnService? = null
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var tun: ParcelFileDescriptor? = null
    private var proc: Process? = null
    private var tunnelRunning = false
    private var doh: DohServer? = null
    @Volatile
    private var generation = 0
    private val restarts = ArrayDeque<Long>()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.startForeground(this, Notifications.engine(this, GtVpnService::class.java, getString(R.string.status_connecting)), true)
        if (intent?.action == ACTION_STOP) {
            SettingsStore.update { it.copy(shouldRun = false) }
            shutdown()
            return START_NOT_STICKY
        }
        if (Engine.status.value != EngineStatus.On) {
            scope.launch { mutex.withLock { startEngine() } }
        } else {
            Notifications.update(this, Notifications.engine(this, GtVpnService::class.java, getString(R.string.status_on)))
        }
        return START_STICKY
    }

    fun reload() {
        scope.launch { mutex.withLock { startEngine() } }
    }

    fun shutdown() {
        scope.launch {
            mutex.withLock { stopEngine() }
            Engine.setStatus(this@GtVpnService, EngineStatus.Off)
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onRevoke() {
        SettingsStore.update { it.copy(shouldRun = false) }
        shutdown()
    }

    override fun onDestroy() {
        instance = null
        try {
            stopEngine()
        } catch (_: Exception) {
        }
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
            val args = Engine.currentArgs()
            val port = Ciadpi.freePort()
            val log = Logs.engineLog(this)
            Logs.append(this, "start vpn port=$port args=${args.joinToString(" ")}")
            val dns = DohServer(DnsProviders.byId(s.dns))
            val dnsPort = withContext(Dispatchers.IO) { dns.start() }
            doh = dns
            val p = withContext(Dispatchers.IO) { Ciadpi.start(this@GtVpnService, port, args, s.blockQuic, log, dnsPort) }
            proc = p
            if (!Ciadpi.awaitReady(port, p, 5000)) throw IllegalStateException(getString(R.string.err_engine_start))
            gpr.greentune.unblock.net.Live.socksPort = port
            val fd = buildTun(s) ?: throw IllegalStateException(getString(R.string.err_vpn_establish))
            tun = fd
            val cfg = File(filesDir, "tunnel.yml")
            cfg.writeText(
                "tunnel:\n  mtu: 8500\nmisc:\n  task-stack-size: 81920\nsocks5:\n  address: 127.0.0.1\n  port: $port\n  udp: 'udp'\n"
            )
            if (!TProxyService.TProxyStartService(cfg.absolutePath, fd.fd)) throw IllegalStateException(getString(R.string.err_tunnel))
            tunnelRunning = true
            Engine.setStatus(this, EngineStatus.On)
            Notifications.update(this, Notifications.engine(this, GtVpnService::class.java, getString(R.string.status_on)))
            watch(p, gen)
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

    private fun watch(p: Process, gen: Int) {
        thread(name = "ciadpi-watch", isDaemon = true) {
            try {
                p.waitFor()
            } catch (_: InterruptedException) {
                return@thread
            }
            if (gen != generation || Engine.status.value != EngineStatus.On) return@thread
            Logs.append(this, "engine exited code=${p.exitValue()}")
            val now = System.currentTimeMillis()
            synchronized(restarts) {
                while (restarts.isNotEmpty() && now - restarts.first() > 60_000) restarts.removeFirst()
                restarts.addLast(now)
            }
            if (restarts.size <= 3) reload() else {
                Engine.setStatus(this, EngineStatus.Error(getString(R.string.err_engine_crashed)))
                shutdown()
            }
        }
    }

    private fun stopEngine() {
        generation++
        if (tunnelRunning) {
            try {
                TProxyService.TProxyStopService()
            } catch (_: Throwable) {
            }
            tunnelRunning = false
        }
        Ciadpi.stop(proc)
        proc = null
        doh?.stop()
        doh = null
        try {
            tun?.close()
        } catch (_: Exception) {
        }
        tun = null
    }

    private fun buildTun(s: AppSettings): ParcelFileDescriptor? {
        val b = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(8500)
            .addAddress("10.111.0.2", 32)
            .addRoute("0.0.0.0", 0)
        if (s.ipv6) {
            b.addAddress("fd6e:6774:7562::2", 128).addRoute("::", 0)
        }
        b.addDnsServer("10.111.0.53")
        b.setMetered(false)
        b.setConfigureIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        )
        when (s.appsMode) {
            "only" -> {
                var added = 0
                for (pkg in s.apps) {
                    if (pkg == packageName) continue
                    try {
                        b.addAllowedApplication(pkg)
                        added++
                    } catch (_: PackageManager.NameNotFoundException) {
                    }
                }
                if (added == 0) b.addDisallowedApplication(packageName)
            }
            "except" -> {
                for (pkg in s.apps) {
                    try {
                        b.addDisallowedApplication(pkg)
                    } catch (_: PackageManager.NameNotFoundException) {
                    }
                }
                try {
                    b.addDisallowedApplication(packageName)
                } catch (_: Exception) {
                }
            }
            else -> b.addDisallowedApplication(packageName)
        }
        return b.establish()
    }
}
