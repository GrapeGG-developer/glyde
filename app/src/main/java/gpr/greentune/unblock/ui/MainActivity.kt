package gpr.greentune.unblock.ui

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import gpr.greentune.unblock.core.Engine
import gpr.greentune.unblock.core.EngineStatus
import gpr.greentune.unblock.core.Tuner
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.data.TuneStore
import gpr.greentune.unblock.system.ShakeDetector
import gpr.greentune.unblock.ui.aero.AeroTheme
import gpr.greentune.unblock.update.Updater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CONNECT = "connect"
        const val EXTRA_UPDATE = "update"
    }

    private val showUpdate = mutableStateOf(false)
    private var shake: ShakeDetector? = null

    private val vpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) doStart() else Engine.setStatus(this, EngineStatus.Error(getString(gpr.greentune.unblock.R.string.err_vpn_denied)))
    }

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val s by SettingsStore.state.collectAsStateWithLifecycle()
            AeroTheme(s.theme, Color(s.tint), s.tintIntensity) {
                AppRoot(
                    onConnect = ::connect,
                    onDisconnect = { Engine.stop(this) },
                    requestNotifications = ::askNotifications,
                    showUpdate = showUpdate.value,
                    onUpdateShown = { showUpdate.value = false },
                )
            }
        }
        lifecycleScope.launch {
            Updater.autoCheck(this@MainActivity)
            if (Updater.state.value is gpr.greentune.unblock.update.UpdateState.Available) {
                val info = (Updater.state.value as gpr.greentune.unblock.update.UpdateState.Available).info
                if (info.version != SettingsStore.value.skippedVersion) showUpdate.value = true
            }
        }
        shake = ShakeDetector(this) {
            if (SettingsStore.value.shake && Engine.isActive()) Engine.reload(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        shake?.start()
    }

    override fun onPause() {
        shake?.stop()
        super.onPause()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_UPDATE, false)) showUpdate.value = true
        if (intent.getBooleanExtra(EXTRA_CONNECT, false)) {
            intent.removeExtra(EXTRA_CONNECT)
            connect()
        }
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun connect() {
        if (SettingsStore.value.mode == "vpn") {
            val prep = VpnService.prepare(this)
            if (prep != null) {
                try {
                    vpnLauncher.launch(prep)
                } catch (_: Exception) {
                    Engine.setStatus(this, EngineStatus.Error(getString(gpr.greentune.unblock.R.string.err_vpn_denied)))
                }
                return
            }
        }
        doStart()
    }

    private fun doStart() {
        Engine.start(this)
        val s = SettingsStore.value
        if (TuneStore.current.value == null && !(s.expert && s.customArgsEnabled) && !Tuner.isRunning()) {
            Tuner.start(this, full = false)
        }
    }
}
