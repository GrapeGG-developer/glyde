package gpr.greentune.unblock.ui.screens

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.width
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    var showCode by remember { mutableStateOf(false) }
    val build by gpr.greentune.unblock.system.Authenticity.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { gpr.greentune.unblock.system.Authenticity.check(context) }
    ScreenColumn(stringResource(R.string.nav_about)) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                EggLogo(120)
                Text(stringResource(R.string.app_name), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                Text(
                    BuildConfig.DISPLAY_NAME,
                    fontSize = 14.sp, color = c.subText,
                    modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) {
                        taps++
                        if (taps == 7) {
                            showCode = true
                            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                            cm?.setPrimaryClip(android.content.ClipData.newPlainText("Glyde ID", gpr.greentune.unblock.net.Device.code))
                            Toast.makeText(context, R.string.insider_copied, Toast.LENGTH_LONG).show()
                        }
                    }
                )
                if (showCode) {
                    Spacer(Modifier.height(4.dp))
                    Badge(gpr.greentune.unblock.net.Device.code, color = Aero.colors.tint)
                }
                Spacer(Modifier.height(6.dp))
                Badge("by GREENTUNE", color = Aero.colors.accent)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.about_text), fontSize = 14.sp, color = c.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.about_dev), fontSize = 13.sp, color = c.subText)
            }
        }
        GlassPanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_v3_shield), null, Modifier.size(48.dp).alpha(if (build == gpr.greentune.unblock.system.BuildCheck.Genuine) 1f else 0.45f))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            when (build) {
                                gpr.greentune.unblock.system.BuildCheck.Genuine -> R.string.build_ok
                                gpr.greentune.unblock.system.BuildCheck.Modified -> R.string.build_bad
                                gpr.greentune.unblock.system.BuildCheck.Unknown -> R.string.build_unknown
                                gpr.greentune.unblock.system.BuildCheck.Dev -> R.string.build_dev
                                else -> R.string.build_checking
                            }
                        ),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = when (build) {
                            gpr.greentune.unblock.system.BuildCheck.Genuine -> c.ok
                            gpr.greentune.unblock.system.BuildCheck.Modified -> c.danger
                            else -> c.text
                        }
                    )
                    if (build == gpr.greentune.unblock.system.BuildCheck.Genuine || build == gpr.greentune.unblock.system.BuildCheck.Modified) {
                        Text(
                            stringResource(if (build == gpr.greentune.unblock.system.BuildCheck.Genuine) R.string.build_ok_sub else R.string.build_bad_sub),
                            fontSize = 12.5.sp, color = c.subText
                        )
                    }
                }
                if (build == gpr.greentune.unblock.system.BuildCheck.Genuine) {
                    AeroButton(stringResource(R.string.build_log), onClick = { openUrl(context, gpr.greentune.unblock.system.Authenticity.releaseUrl()) })
                }
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
            Text(stringResource(R.string.privacy_text), fontSize = 13.sp, color = c.subText)
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
