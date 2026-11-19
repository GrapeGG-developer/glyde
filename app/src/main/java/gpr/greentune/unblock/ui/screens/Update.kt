package gpr.greentune.unblock.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gpr.greentune.unblock.R
import gpr.greentune.unblock.ui.aero.Aero
import gpr.greentune.unblock.ui.aero.AeroButton
import gpr.greentune.unblock.ui.aero.AeroDialog
import gpr.greentune.unblock.ui.aero.AeroProgressBar
import gpr.greentune.unblock.update.UpdateInfo
import gpr.greentune.unblock.update.UpdateState
import gpr.greentune.unblock.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object UpdateFlow {
    fun start(context: Context, scope: CoroutineScope, info: UpdateInfo) {
        if (!Updater.canInstall(context)) {
            try {
                context.startActivity(Updater.installPermissionIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
            return
        }
        scope.launch {
            Updater.download(context, info)
            if (Updater.state.value is UpdateState.ReadyToInstall) Updater.install(context)
        }
    }
}

@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val c = Aero.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by Updater.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (st !is UpdateState.Available && st !is UpdateState.Downloading && st !is UpdateState.ReadyToInstall) {
            Updater.check(context, manual = true)
        }
    }
    val info = when (val s = st) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.ReadyToInstall -> s.info
        else -> null
    }
    AeroDialog(
        title = if (info != null) stringResource(R.string.update_available_title, info.version) else stringResource(R.string.update_title),
        onDismiss = onDismiss,
        buttons = {
            when (val s = st) {
                is UpdateState.Available -> {
                    AeroButton(stringResource(R.string.update_skip), onClick = {
                        Updater.skip(s.info)
                        onDismiss()
                    })
                    AeroButton(stringResource(R.string.update_install), primary = true, onClick = { UpdateFlow.start(context, scope, s.info) })
                }
                is UpdateState.ReadyToInstall -> {
                    AeroButton(stringResource(R.string.later), onClick = onDismiss)
                    AeroButton(stringResource(R.string.update_install), primary = true, onClick = { Updater.install(context) })
                }
                else -> AeroButton(stringResource(R.string.close), onClick = onDismiss)
            }
        }
    ) {
        Column {
            when (val s = st) {
                UpdateState.Checking -> AeroProgressBar(null)
                UpdateState.UpToDate -> Text(stringResource(R.string.update_latest), color = c.text)
                is UpdateState.Failed -> Text(s.message, color = c.danger)
                is UpdateState.Downloading -> {
                    Text(stringResource(R.string.update_downloading, (s.progress * 100).toInt()), color = c.text)
                    AeroProgressBar(s.progress)
                }
                else -> Unit
            }
            if (info != null) {
                Text(stringResource(R.string.update_changelog), fontSize = 13.sp, color = c.subText)
                Text(
                    info.changelog.ifBlank { "—" },
                    fontSize = 14.sp,
                    color = c.text,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())
                )
            }
        }
    }
}
