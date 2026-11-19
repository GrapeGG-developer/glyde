package gpr.greentune.unblock.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import gpr.greentune.unblock.core.ShareCode
import gpr.greentune.unblock.ui.aero.AeroDialog
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gpr.greentune.unblock.R
import gpr.greentune.unblock.core.Args
import gpr.greentune.unblock.core.Ciadpi
import gpr.greentune.unblock.core.Engine
import gpr.greentune.unblock.core.Logs
import gpr.greentune.unblock.core.Presets
import gpr.greentune.unblock.core.Tuner
import gpr.greentune.unblock.data.ServiceChoice
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.data.TuneResult
import gpr.greentune.unblock.data.TuneStore
import gpr.greentune.unblock.ui.aero.Aero
import gpr.greentune.unblock.ui.aero.AeroButton
import gpr.greentune.unblock.ui.aero.AeroProgressBar
import gpr.greentune.unblock.ui.aero.GlassPanel
import gpr.greentune.unblock.ui.aero.SectionTitle
import gpr.greentune.unblock.ui.aero.StatusDot
import gpr.greentune.unblock.ui.aero.ToggleRow
import gpr.greentune.unblock.ui.aero.glass
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

@Composable
fun TuneScreen() {
    val c = Aero.colors
    val context = LocalContext.current
    val tune by Tuner.progress.collectAsStateWithLifecycle()
    val result by TuneStore.current.collectAsStateWithLifecycle()
    val presets by Presets.data.collectAsStateWithLifecycle()
    val settings by SettingsStore.state.collectAsStateWithLifecycle()

    ScreenColumn(stringResource(R.string.nav_tune)) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.tune_explain), fontSize = 13.5.sp, color = c.subText)
                if (tune.running) {
                    Text(tune.label, fontSize = 14.sp, color = c.text)
                    AeroProgressBar(tune.fraction)
                    AeroButton(stringResource(R.string.cancel), onClick = { Tuner.cancel() }, modifier = Modifier.fillMaxWidth())
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AeroButton(stringResource(R.string.tune_quick), onClick = { Tuner.start(context, false) }, primary = true, modifier = Modifier.weight(1f))
                        AeroButton(stringResource(R.string.tune_full), onClick = { Tuner.start(context, true) }, modifier = Modifier.weight(1f))
                    }
                    if (tune.message != null) Text(tune.message ?: "", fontSize = 13.sp, color = c.text)
                }
            }
        }

        val r = result
        GlassPanel(Modifier.fillMaxWidth()) {
            Column {
                Text(stringResource(R.string.tune_results), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                if (r == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.tune_no_results), fontSize = 13.sp, color = c.subText)
                } else {
                    Text(
                        r.label + " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(r.time)) +
                            if (r.full) " · " + stringResource(R.string.tune_full_short) else "",
                        fontSize = 12.sp, color = c.subText
                    )
                    Spacer(Modifier.height(8.dp))
                    for (s in Presets.active(presets, settings).services) {
                        val ch = r.choices.firstOrNull { it.serviceId == s.id }
                        ResultRow(s.name, ch, settings.expert, presets.strategies.map { it.id }) { sid ->
                            val updated = r.choices.filter { it.serviceId != s.id } +
                                ServiceChoice(s.id, sid, sid == null, true, ch?.ms ?: 0)
                            TuneStore.save(r.copy(choices = updated))
                            Engine.reload(context)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.tune_default_strategy, r.defaultStrategy ?: "—"),
                        fontSize = 12.5.sp, color = c.subText
                    )
                }
            }
        }

        SharePanel()

        if (settings.expert) ExpertPanel()
    }
}

@Composable
private fun SharePanel() {
    val c = Aero.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val result by TuneStore.current.collectAsStateWithLifecycle()
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    var showQr by remember { mutableStateOf(false) }
    val code = remember(result, settings.customDomains, settings.customArgs, settings.customArgsEnabled) { ShareCode.build() }
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.share_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            Text(stringResource(R.string.share_explain), fontSize = 13.sp, color = c.subText)
            if (code != null) {
                Text(
                    code.take(64) + if (code.length > 64) "…" else "",
                    fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = c.text,
                    modifier = Modifier.fillMaxWidth().glass(c, 14.dp, glossFraction = 0.2f).padding(10.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AeroButton(stringResource(R.string.share_send), primary = true, enabled = code != null, modifier = Modifier.weight(1f), onClick = {
                    val cd = code ?: return@AeroButton
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, cd)
                    try {
                        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                    }
                })
                AeroButton("QR", enabled = code != null, onClick = { showQr = true })
                AeroButton(stringResource(R.string.diag_copy), enabled = code != null, onClick = {
                    clipboard.setText(AnnotatedString(code ?: ""))
                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                })
            }
            AeroButton(stringResource(R.string.share_import), modifier = Modifier.fillMaxWidth(), onClick = {
                val text = clipboard.getText()?.text
                if (ShareCode.looksLike(text) && ShareCode.apply(text!!)) {
                    Engine.reload(context)
                    Toast.makeText(context, R.string.share_imported, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.share_bad, Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
    if (showQr && code != null) {
        AeroDialog(
            title = stringResource(R.string.share_code),
            onDismiss = { showQr = false },
            buttons = { AeroButton(stringResource(R.string.close), onClick = { showQr = false }) }
        ) {
            val matrix = remember(code) { ShareCode.qr(code) }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(10.dp)
                ) {
                    val n = matrix.width
                    val cell = size.width / n
                    for (y in 0 until matrix.height) {
                        for (x in 0 until n) {
                            if (matrix.get(x, y)) {
                                drawRect(Color(0xFF06222F), Offset(x * cell, y * cell), androidx.compose.ui.geometry.Size(cell + 0.6f, cell + 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(name: String, ch: ServiceChoice?, expert: Boolean, strategies: List<String>, onPick: (String?) -> Unit) {
    val c = Aero.colors
    var open by remember { mutableStateOf(false) }
    val state = when {
        ch == null -> c.subText
        ch.direct -> c.ok
        ch.ok -> c.ok
        ch.strategyId != null -> c.warn
        else -> c.danger
    }
    val text = when {
        ch == null -> stringResource(R.string.tune_not_checked)
        ch.direct -> stringResource(R.string.tune_direct)
        ch.strategyId != null -> ch.strategyId + if (ch.ms > 0) " · ${ch.ms} ms" else ""
        else -> stringResource(R.string.tune_not_found)
    }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (expert) Modifier.clickable { open = true } else Modifier)
                .padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(state)
            Spacer(Modifier.width(10.dp))
            Text(name, fontSize = 14.5.sp, color = c.text, modifier = Modifier.weight(1f))
            Text(text, fontSize = 13.sp, color = c.subText)
        }
        if (expert) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.tune_direct)) }, onClick = {
                    open = false
                    onPick(null)
                })
                for (id in strategies) {
                    DropdownMenuItem(text = { Text(id) }, onClick = {
                        open = false
                        onPick(id)
                    })
                }
            }
        }
    }
}

@Composable
private fun ExpertPanel() {
    val c = Aero.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val presets by Presets.data.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var args by remember { mutableStateOf(settings.customArgs) }
    var defaultOpen by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf<String?>(null) }

    SectionTitle(stringResource(R.string.expert_mode))
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box {
                AeroButton(stringResource(R.string.expert_pick_default), onClick = { defaultOpen = true }, modifier = Modifier.fillMaxWidth())
                DropdownMenu(expanded = defaultOpen, onDismissRequest = { defaultOpen = false }) {
                    for (st in presets.strategies) {
                        DropdownMenuItem(
                            text = { Text(st.id + "  " + st.args.take(40), fontSize = 13.sp) },
                            onClick = {
                                defaultOpen = false
                                TuneStore.setDefault(st.id)
                                Engine.reload(context)
                            }
                        )
                    }
                }
            }
            ToggleRow(stringResource(R.string.expert_custom), stringResource(R.string.expert_custom_sub), settings.customArgsEnabled, { v ->
                SettingsStore.update { it.copy(customArgsEnabled = v, customArgs = args) }
                Engine.reload(context)
            })
            BasicTextField(
                value = args,
                onValueChange = { args = it },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = c.text),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp).glass(c, 14.dp, glossFraction = 0.2f).padding(10.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AeroButton(stringResource(R.string.save), primary = true, modifier = Modifier.weight(1f), onClick = {
                    scope.launch {
                        val ok = Ciadpi.validate(context, Args.tokenize(Args.withSni(args, presets.fakeSni)))
                        if (ok) {
                            SettingsStore.update { it.copy(customArgs = args) }
                            Engine.reload(context)
                            Toast.makeText(context, R.string.expert_saved, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, R.string.expert_invalid, Toast.LENGTH_LONG).show()
                        }
                    }
                })
                AeroButton(stringResource(R.string.expert_from_current), modifier = Modifier.weight(1f), onClick = {
                    args = Engine.currentArgs().joinToString(" ") { if (it.contains(' ')) "\"$it\"" else it }
                })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AeroButton(stringResource(R.string.expert_export), modifier = Modifier.weight(1f), onClick = {
                    val o = JSONObject().put("gtunblock", 1).put("args", args)
                    TuneStore.current.value?.let { o.put("tune", it.toJson()) }
                    clipboard.setText(AnnotatedString(o.toString()))
                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                })
                AeroButton(stringResource(R.string.expert_import), modifier = Modifier.weight(1f), onClick = {
                    val text = clipboard.getText()?.text ?: ""
                    try {
                        val o = JSONObject(text)
                        if (o.has("args")) args = o.getString("args")
                        o.optJSONObject("tune")?.let { TuneStore.save(TuneResult.fromJson(it)) }
                        SettingsStore.update { it.copy(customArgs = args) }
                        Engine.reload(context)
                        Toast.makeText(context, R.string.expert_imported, Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        if (text.isNotBlank() && text.trim().startsWith("-")) {
                            args = text.trim()
                        } else {
                            Toast.makeText(context, R.string.expert_import_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AeroButton(stringResource(R.string.expert_log), modifier = Modifier.weight(1f), onClick = {
                    log = if (log == null) Logs.tail(context) else null
                })
                AeroButton(stringResource(R.string.expert_reset), danger = true, modifier = Modifier.weight(1f), onClick = {
                    TuneStore.clear()
                    Engine.reload(context)
                })
            }
            val l = log
            if (l != null) {
                Box(Modifier.fillMaxWidth().heightIn(max = 320.dp).glass(c, 14.dp, glossFraction = 0.1f).verticalScroll(rememberScrollState()).padding(8.dp)) {
                    Text(l.ifBlank { "—" }, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = c.text)
                }
            }
        }
    }
}
