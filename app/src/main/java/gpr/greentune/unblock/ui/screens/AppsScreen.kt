package gpr.greentune.unblock.ui.screens

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gpr.greentune.unblock.R
import gpr.greentune.unblock.core.Engine
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.ui.aero.Aero
import gpr.greentune.unblock.ui.aero.AeroButton
import gpr.greentune.unblock.ui.aero.AeroProgressBar
import gpr.greentune.unblock.ui.aero.AeroToggle
import gpr.greentune.unblock.ui.aero.GlassPanel
import gpr.greentune.unblock.ui.aero.Segmented
import gpr.greentune.unblock.ui.aero.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val pkg: String, val label: String, val system: Boolean, val icon: ImageBitmap?)

private suspend fun loadApps(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    apps.asSequence()
        .filter { it.packageName != context.packageName }
        .filter { pm.checkPermission(android.Manifest.permission.INTERNET, it.packageName) == PackageManager.PERMISSION_GRANTED }
        .map { ai ->
            val icon = try {
                val d = pm.getApplicationIcon(ai)
                val bmp = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
                val cv = Canvas(bmp)
                d.setBounds(0, 0, 72, 72)
                d.draw(cv)
                bmp.asImageBitmap()
            } catch (_: Exception) {
                null
            }
            AppEntry(ai.packageName, pm.getApplicationLabel(ai).toString(), (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0, icon)
        }
        .sortedWith(compareBy<AppEntry> { it.system }.thenBy { it.label.lowercase() })
        .toList()
}

@Composable
fun AppsScreen() {
    val c = Aero.colors
    val context = LocalContext.current
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { apps = loadApps(context) }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.nav_apps), fontSize = 26.sp, color = c.text, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Segmented(
            listOf(
                "all" to stringResource(R.string.apps_mode_all),
                "only" to stringResource(R.string.apps_mode_only),
                "except" to stringResource(R.string.apps_mode_except),
            ),
            settings.appsMode,
            { m ->
                SettingsStore.update { it.copy(appsMode = m) }
                dirty = true
            }
        )
        Text(
            stringResource(
                when (settings.appsMode) {
                    "only" -> R.string.apps_mode_only_sub
                    "except" -> R.string.apps_mode_except_sub
                    else -> R.string.apps_mode_all_sub
                }
            ),
            fontSize = 12.5.sp, color = c.subText
        )
        if (dirty && Engine.isActive()) {
            AeroButton(stringResource(R.string.apps_apply), primary = true, modifier = Modifier.fillMaxWidth(), onClick = {
                Engine.reload(context)
                dirty = false
            })
        }
        if (settings.appsMode != "all") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).glass(c, 14.dp, glossFraction = 0.3f).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (query.isEmpty()) Text(stringResource(R.string.search), color = c.subText, fontSize = 14.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.apps_system), fontSize = 12.sp, color = c.subText)
                Spacer(Modifier.width(6.dp))
                AeroToggle(showSystem, { showSystem = it })
            }
            val list = apps
            if (list == null) {
                AeroProgressBar(null)
            } else {
                val q = query.trim().lowercase()
                val filtered = list.filter { (showSystem || !it.system || it.pkg in settings.apps) && (q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q)) }
                    .sortedByDescending { it.pkg in settings.apps }
                GlassPanel(Modifier.fillMaxWidth().weight(1f), padding = 4.dp) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(filtered, key = { it.pkg }) { app ->
                            val checked = app.pkg in settings.apps
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        SettingsStore.update { st -> st.copy(apps = if (checked) st.apps - app.pkg else st.apps + app.pkg) }
                                        dirty = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.icon != null) Image(app.icon, null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, fontSize = 14.5.sp, color = c.text, maxLines = 1)
                                    Text(app.pkg, fontSize = 11.sp, color = c.subText, maxLines = 1)
                                }
                                AeroToggle(checked, { v ->
                                    SettingsStore.update { st -> st.copy(apps = if (v) st.apps + app.pkg else st.apps - app.pkg) }
                                    dirty = true
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}
