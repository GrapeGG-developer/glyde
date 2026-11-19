package gpr.greentune.unblock.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gpr.greentune.unblock.R
import gpr.greentune.unblock.core.Diag
import gpr.greentune.unblock.core.Engine
import gpr.greentune.unblock.core.Presets
import gpr.greentune.unblock.core.Service
import gpr.greentune.unblock.core.Tuner
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.data.TuneStore
import gpr.greentune.unblock.ui.aero.Aero
import gpr.greentune.unblock.ui.aero.AeroButton
import gpr.greentune.unblock.ui.aero.GlydeSans
import gpr.greentune.unblock.ui.aero.GlassPanel
import gpr.greentune.unblock.ui.aero.StatusDot
import gpr.greentune.unblock.ui.aero.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ServicesScreen(onOpenDiag: () -> Unit) {
    val c = Aero.colors
    val context = LocalContext.current
    val presets by Presets.data.collectAsStateWithLifecycle()
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val result by TuneStore.current.collectAsStateWithLifecycle()
    val tune by Tuner.progress.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val enabled = Presets.enabledIds(presets, settings)
    val ru = Locale.getDefault().language == "ru"
    val startKey = remember { settings.services.toString() + settings.servicesSet + settings.customDomains.toString() }

    DisposableEffect(Unit) {
        onDispose {
            val s = SettingsStore.value
            val key = s.services.toString() + s.servicesSet + s.customDomains.toString()
            if (key != startKey && Engine.isActive()) Engine.reload(context)
        }
    }

    fun setEnabled(ids: Set<String>) {
        SettingsStore.update { it.copy(servicesSet = true, services = ids) }
    }

    ScreenColumn(stringResource(R.string.services_title)) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.services_explain), fontSize = 13.5.sp, color = c.subText)
                Text(
                    stringResource(R.string.services_selected, enabled.size, presets.services.size),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AeroButton(stringResource(R.string.services_popular), modifier = Modifier.weight(1f), onClick = {
                        setEnabled(presets.services.filter { it.default }.map { it.id }.toSet())
                    })
                    AeroButton(stringResource(R.string.services_all), modifier = Modifier.weight(1f), onClick = {
                        setEnabled(presets.services.map { it.id }.toSet())
                    })
                    AeroButton(stringResource(R.string.services_none), modifier = Modifier.weight(1f), onClick = {
                        setEnabled(emptySet())
                    })
                }
                SearchField(query, { query = it }, stringResource(R.string.services_search))
            }
        }

        val q = query.trim().lowercase()
        val filtered = presets.services.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.domains.any { d -> d.contains(q) } }
        val cats = presets.categories.ifEmpty { listOf(gpr.greentune.unblock.core.Category("other", "Другое", "Other")) }
        val known = cats.map { it.id }.toSet()
        for (cat in cats) {
            val list = filtered.filter { it.category == cat.id || (cat == cats.last() && it.category !in known) }
            if (list.isEmpty()) continue
            GlassPanel(Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (ru) cat.ru else cat.en, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
                        val on = list.count { it.id in enabled }
                        Text("$on/${list.size}", fontSize = 12.sp, color = c.subText, modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(onClick = {
                                val ids = list.map { it.id }.toSet()
                                setEnabled(if (on == list.size) enabled - ids else enabled + ids)
                            })
                            .padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (s in list) {
                            val ch = result?.choices?.firstOrNull { it.serviceId == s.id }
                            val state = when {
                                ch == null -> null
                                ch.direct || ch.ok -> c.ok
                                ch.strategyId != null -> c.warn
                                else -> c.danger
                            }
                            ServiceToggle(
                                s, s.id in enabled, state,
                                onToggle = { setEnabled(if (s.id in enabled) enabled - s.id else enabled + s.id) },
                                onTune = {
                                    if (s.id !in enabled) setEnabled(enabled + s.id)
                                    Tuner.start(context, false, setOf(s.id))
                                },
                                onCheck = {
                                    if (s.id !in enabled) setEnabled(enabled + s.id)
                                    Diag.start(context, setOf(s.id))
                                    onOpenDiag()
                                },
                                tuneEnabled = !tune.running
                            )
                        }
                    }
                }
            }
        }

        CustomSitesPanel()

        AeroButton(
            stringResource(R.string.services_retune),
            primary = true,
            enabled = !tune.running && (enabled.isNotEmpty() || settings.customDomains.isNotEmpty()),
            modifier = Modifier.fillMaxWidth(),
            onClick = { Tuner.start(context, false) }
        )
        if (tune.running) {
            Text(tune.label, fontSize = 13.sp, color = c.subText, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServiceToggle(
    s: Service,
    on: Boolean,
    state: Color?,
    onToggle: () -> Unit,
    onTune: () -> Unit,
    onCheck: () -> Unit,
    tuneEnabled: Boolean,
) {
    val c = Aero.colors
    val brand = Color(s.color)
    var menu by remember { mutableStateOf(false) }
    val a by animateFloatAsState(if (on) 1f else 0f, tween(220), label = "a")
    val textColor by animateColorAsState(if (on) c.text else c.subText, tween(220), label = "t")
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .drawBehind {
                    val r = size.height / 2
                    val one = 1.dp.toPx()
                    val bg = if (c.dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.45f)
                    drawRoundRect(bg, cornerRadius = CornerRadius(r, r))
                    if (a > 0f) {
                        val base = lerp(c.tint, brand, 0.35f)
                        drawRoundRect(
                            Brush.verticalGradient(
                                listOf(lerp(base, Color.White, 0.55f).copy(alpha = 0.55f * a), base.copy(alpha = 0.40f * a), lerp(base, Color.White, 0.4f).copy(alpha = 0.55f * a))
                            ),
                            cornerRadius = CornerRadius(r, r)
                        )
                        drawRoundRect(
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f * a), Color.White.copy(alpha = 0.05f * a)), 0f, size.height * 0.5f),
                            topLeft = Offset(size.height * 0.25f, one * 1.5f),
                            size = androidx.compose.ui.geometry.Size(size.width - size.height * 0.5f, size.height * 0.46f),
                            cornerRadius = CornerRadius(r, r)
                        )
                    }
                    drawRoundRect(
                        if (on) lerp(c.tint, Color.Black, 0.3f).copy(alpha = 0.6f) else c.borderOuter,
                        cornerRadius = CornerRadius(r, r),
                        style = Stroke(one)
                    )
                }
                .combinedClickable(onClick = onToggle, onLongClick = { menu = true })
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(12.dp).drawBehind {
                    drawCircle(Brush.radialGradient(listOf(lerp(brand, Color.White, 0.6f), brand), Offset(size.width * 0.35f, size.height * 0.3f), size.width))
                    drawCircle(Color.Black.copy(alpha = 0.3f), style = Stroke(1f))
                }
            )
            Spacer(Modifier.width(7.dp))
            Text(s.name, fontSize = 13.5.sp, color = textColor, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            if (on && state != null) {
                Spacer(Modifier.width(6.dp))
                StatusDot(state, size = 7.dp)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.services_tune_one)) }, enabled = tuneEnabled, onClick = {
                menu = false
                onTune()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.services_check_one)) }, onClick = {
                menu = false
                onCheck()
            })
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    val c = Aero.colors
    Box(Modifier.fillMaxWidth().glass(c, 22.dp, glossFraction = 0.3f).padding(horizontal = 16.dp, vertical = 11.dp)) {
        if (value.isEmpty()) Text(hint, fontSize = 14.sp, color = c.subText)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = GlydeSans, fontSize = 14.sp, color = c.text),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun CustomSitesPanel() {
    val c = Aero.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }

    fun addAll(list: List<String>) {
        if (list.isEmpty()) {
            Toast.makeText(context, R.string.custom_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        val before = SettingsStore.value.customDomains.size
        SettingsStore.update { it.copy(customDomains = (it.customDomains + list).distinct().take(3000)) }
        val added = SettingsStore.value.customDomains.size - before
        Toast.makeText(context, context.getString(R.string.custom_added, added), Toast.LENGTH_SHORT).show()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { s ->
                            val bytes = s.readBytes()
                            String(bytes, 0, minOf(bytes.size, 4 * 1024 * 1024))
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
                addAll(Presets.parseHostlist(text ?: ""))
            }
        }
    }

    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.custom_sites), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.custom_count, settings.customDomains.size), fontSize = 12.sp, color = c.subText)
            }
            Text(stringResource(R.string.custom_explain), fontSize = 13.sp, color = c.subText)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).glass(c, 22.dp, glossFraction = 0.3f).padding(horizontal = 16.dp, vertical = 11.dp)) {
                    if (input.isEmpty()) Text(stringResource(R.string.custom_hint), fontSize = 14.sp, color = c.subText)
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            addAll(Presets.parseHostlist(input))
                            input = ""
                        }),
                        textStyle = TextStyle(fontFamily = GlydeSans, fontSize = 14.sp, color = c.text),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AeroButton(stringResource(R.string.custom_add), primary = true, onClick = {
                    addAll(Presets.parseHostlist(input))
                    input = ""
                })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AeroButton(stringResource(R.string.custom_paste), modifier = Modifier.weight(1f), onClick = {
                    addAll(Presets.parseHostlist(clipboard.getText()?.text ?: ""))
                })
                AeroButton(stringResource(R.string.custom_file), modifier = Modifier.weight(1f), onClick = {
                    try {
                        picker.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                    } catch (_: Exception) {
                    }
                })
            }
            if (settings.customDomains.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in settings.customDomains.take(60)) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .drawBehind { drawRect(c.tint.copy(alpha = 0.14f)) }
                                .combinedClickable(onClick = {}, onLongClick = {
                                    SettingsStore.update { it.copy(customDomains = it.customDomains - d) }
                                })
                                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(d, fontSize = 12.5.sp, color = c.text)
                            Box(
                                Modifier
                                    .padding(start = 4.dp)
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(50))
                                    .combinedClickable(onClick = {
                                        SettingsStore.update { it.copy(customDomains = it.customDomains - d) }
                                    }),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("×", fontSize = 15.sp, color = c.subText)
                            }
                        }
                    }
                    if (settings.customDomains.size > 60) {
                        Text("+${settings.customDomains.size - 60}", fontSize = 12.5.sp, color = c.subText, modifier = Modifier.padding(6.dp))
                    }
                }
                AeroButton(stringResource(R.string.custom_clear), danger = true, modifier = Modifier.fillMaxWidth(), onClick = {
                    SettingsStore.update { it.copy(customDomains = emptyList()) }
                })
            }
        }
    }
}
