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
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import gpr.greentune.unblock.BuildConfig
import gpr.greentune.unblock.update.UpdateState
import java.io.File

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CONNECT = "connect"
        const val EXTRA_UPDATE = "update"
    }

    private val showUpdate = mutableStateOf(false)
    private val whatsNew = mutableStateOf<String?>(null)
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
                    onUpdateShown = {
                        showUpdate.value = false
                        Updater.dismissed = Updater.current()?.version ?: Updater.lastInfo?.version
                    },
                    whatsNew = whatsNew.value,
                    onWhatsNewShown = {
                        whatsNew.value = null
                        SettingsStore.update { it.copy(seenVersion = BuildConfig.VERSION_NAME) }
                    },
                )
            }
        }
        prepareWhatsNew()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Updater.foreground = true
                launch {
                    try {
                        awaitCancellation()
                    } finally {
                        Updater.foreground = false
                    }
                }
                launch {
                    while (isActive) {
                        Updater.poll(applicationContext)
                        delay(Updater.pollDelay())
                    }
                }
                launch {
                    Updater.state.collect { st ->
                        if (st is UpdateState.Available && st.info.version != SettingsStore.value.skippedVersion && st.info.version != Updater.dismissed) {
                            showUpdate.value = true
                        }
                        if (st is UpdateState.ReadyToInstall && st.info.version != Updater.dismissed) showUpdate.value = true
                    }
                }
                launch {
                    Updater.confirm.collect { i ->
                        if (i != null) {
                            Updater.requestConfirm(null)
                            try {
                                startActivity(i)
                            } catch (_: Exception) {
                                Updater.setFailed(getString(gpr.greentune.unblock.R.string.update_install_failed))
                            }
                        }
                    }
                }
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
        Updater.resumePending(this)
    }

    override fun onPause() {
        shake?.stop()
        super.onPause()
    }

    private fun prepareWhatsNew() {
        val ver = BuildConfig.VERSION_NAME
        val s = SettingsStore.value
        if (s.seenVersion == ver) return
        val updated = s.seenVersion.isNotBlank() || try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            pi.lastUpdateTime - pi.firstInstallTime > 5_000L
        } catch (_: Exception) {
            false
        }
        File(cacheDir, "updates").deleteRecursively()
        File(cacheDir, "update.apk").delete()
        val text = if (updated) Updater.changelogFor(this, ver) else null
        if (text != null) whatsNew.value = text
        else SettingsStore.update { it.copy(seenVersion = ver) }
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
