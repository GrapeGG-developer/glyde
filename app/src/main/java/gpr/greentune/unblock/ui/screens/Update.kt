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
import gpr.greentune.air.Air
import gpr.greentune.air.AirButton
import gpr.greentune.air.AirDialog
import gpr.greentune.air.AirProgressBar
import gpr.greentune.unblock.update.UpdateInfo
import gpr.greentune.unblock.update.UpdateState
import gpr.greentune.unblock.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object UpdateFlow {
    fun start(context: Context, scope: CoroutineScope, info: UpdateInfo) {
        Updater.startDownload(context, info)
    }
}

fun cleanChangelog(text: String): String = text.lines()
    .filterNot { it.trim().startsWith("## ") }
    .joinToString("\n") { line ->
        line.replace("**", "").replace("`", "").let { l ->
            when {
                l.trimStart().startsWith("### ") -> l.trimStart().removePrefix("### ")
                l.trimStart().startsWith("- ") -> "• " + l.trimStart().removePrefix("- ")
                l.trimStart().startsWith("* ") -> "• " + l.trimStart().removePrefix("* ")
                else -> l
            }
        }
    }
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

@Composable
fun WhatsNewDialog(version: String, text: String, onDismiss: () -> Unit) {
    val c = Air.colors
    AirDialog(
        title = stringResource(R.string.whats_new_title, version),
        onDismiss = onDismiss,
        buttons = {
            AirButton(stringResource(R.string.whats_new_ok), primary = true, onClick = onDismiss)
        }
    ) {
        Text(
            cleanChangelog(text).ifBlank { "—" },
            fontSize = 14.sp,
            color = c.text,
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())
        )
    }
}

@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val c = Air.colors
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
        is UpdateState.Failed -> Updater.lastInfo
        else -> null
    }
    AirDialog(
        title = if (info != null) stringResource(R.string.update_available_title, info.version) else stringResource(R.string.update_title),
        onDismiss = onDismiss,
        buttons = {
            when (val s = st) {
                is UpdateState.Available -> {
                    AirButton(stringResource(R.string.update_skip), onClick = {
                        Updater.skip(s.info)
                        onDismiss()
                    })
                    AirButton(stringResource(R.string.update_install), primary = true, onClick = { Updater.startDownload(context, s.info) })
                }
                is UpdateState.ReadyToInstall -> {
                    AirButton(stringResource(R.string.later), onClick = onDismiss)
                    AirButton(stringResource(R.string.update_install), primary = true, onClick = { Updater.install(context) })
                }
                is UpdateState.Downloading -> AirButton(stringResource(R.string.later), onClick = onDismiss)
                is UpdateState.Failed -> {
                    AirButton(stringResource(R.string.close), onClick = onDismiss)
                    val retry = Updater.lastInfo
                    if (retry != null) {
                        AirButton(stringResource(R.string.update_install), primary = true, onClick = { Updater.startDownload(context, retry) })
                    } else {
                        AirButton(stringResource(R.string.update_check), primary = true, onClick = { scope.launch { Updater.check(context, manual = true) } })
                    }
                }
                else -> AirButton(stringResource(R.string.close), onClick = onDismiss)
            }
        }
    ) {
        Column {
            when (val s = st) {
                UpdateState.Checking -> AirProgressBar(null)
                UpdateState.UpToDate -> Text(stringResource(R.string.update_latest), color = c.text)
                is UpdateState.Failed -> Text(s.message, color = c.danger)
                is UpdateState.Downloading -> {
                    Text(stringResource(R.string.update_downloading, (s.progress * 100).toInt()), color = c.text)
                    AirProgressBar(s.progress)
                }
                is UpdateState.ReadyToInstall -> {
                    Text(stringResource(R.string.update_ready), color = c.text)
                    if (!Updater.canInstall(context)) Text(stringResource(R.string.update_install_perm), fontSize = 13.sp, color = c.subText)
                }
                is UpdateState.Available -> {
                    if (!Updater.canInstall(context)) Text(stringResource(R.string.update_install_perm), fontSize = 13.sp, color = c.subText)
                }
                else -> Unit
            }
            if (info != null) {
                Text(stringResource(R.string.update_changelog), fontSize = 13.sp, color = c.subText)
                Text(
                    cleanChangelog(info.changelog).ifBlank { "—" },
                    fontSize = 14.sp,
                    color = c.text,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())
                )
            }
        }
    }
}
