package gpr.greentune.unblock.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gpr.greentune.unblock.BuildConfig
import gpr.greentune.unblock.R
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.net.Backend
import gpr.greentune.unblock.ui.aero.Aero
import gpr.greentune.unblock.ui.aero.AeroButton
import gpr.greentune.unblock.ui.aero.Badge
import gpr.greentune.unblock.ui.aero.GlassPanel
import gpr.greentune.unblock.ui.aero.SectionTitle
import gpr.greentune.unblock.ui.aero.SettingRow
import gpr.greentune.unblock.ui.openUrl

@Composable
fun AboutScreen(onDonate: () -> Unit, onFeedback: () -> Unit) {
    val c = Aero.colors
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    ScreenColumn(stringResource(R.string.nav_about)) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.logo_glass), null, Modifier.size(120.dp))
                Text(stringResource(R.string.app_name), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    fontSize = 13.sp, color = c.subText,
                    modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) {
                        taps++
                        if (taps == 7) {
                            SettingsStore.update { it.copy(expert = true) }
                            Toast.makeText(context, R.string.expert_enabled, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Spacer(Modifier.height(6.dp))
                Badge("by GREENTUNE", color = Aero.colors.accent)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.about_text), fontSize = 14.sp, color = c.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.about_dev), fontSize = 13.sp, color = c.subText)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AeroButton(stringResource(R.string.donate_title), primary = true, icon = painterResource(R.drawable.ic_menu_donate), onClick = onDonate, modifier = Modifier.weight(1f))
            AeroButton(stringResource(R.string.nav_feedback), icon = painterResource(R.drawable.ic_menu_feedback), onClick = onFeedback, modifier = Modifier.weight(1f))
        }
        SectionTitle(stringResource(R.string.about_links))
        GlassPanel(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(stringResource(R.string.about_source), Backend.repoUrl.removePrefix("https://"), onClick = { openUrl(context, Backend.repoUrl) })
                SettingRow(stringResource(R.string.about_privacy), null, onClick = { openUrl(context, Backend.repoUrl + "/blob/main/PRIVACY.md") })
                SettingRow(stringResource(R.string.about_license), "GPL-3.0", onClick = { openUrl(context, Backend.repoUrl + "/blob/main/LICENSE") })
            }
        }
        SectionTitle(stringResource(R.string.about_telemetry_title))
        GlassPanel(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.about_telemetry), fontSize = 13.sp, color = c.subText)
        }
        SectionTitle(stringResource(R.string.about_credits))
        GlassPanel(Modifier.fillMaxWidth()) {
            Column {
                SettingRow("byedpi", "hufrea · MIT", onClick = { openUrl(context, "https://github.com/hufrea/byedpi") })
                SettingRow("hev-socks5-tunnel", "heiher · MIT", onClick = { openUrl(context, "https://github.com/heiher/hev-socks5-tunnel") })
                SettingRow("ByeByeDPI", "romanvht · GPL-3.0", onClick = { openUrl(context, "https://github.com/romanvht/ByeByeDPI") })
                SettingRow("zapret / zapret2", "bol-van · MIT", onClick = { openUrl(context, "https://github.com/bol-van/zapret") })
            }
        }
        Text(stringResource(R.string.about_disclaimer), fontSize = 12.sp, color = c.subText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
