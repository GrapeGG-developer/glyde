package gpr.greentune.unblock.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import gpr.greentune.air.Air
import gpr.greentune.air.AirButton
import gpr.greentune.air.GlassPanel
import gpr.greentune.unblock.ui.openUrl

@Composable
fun DonateScreen() {
    val c = Air.colors
    val context = LocalContext.current
    ScreenColumn(stringResource(R.string.donate_title)) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.ic_menu_donate), null, Modifier.size(128.dp))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.donate_heading), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = c.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.donate_text), fontSize = 14.sp, color = c.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                AirButton(stringResource(R.string.donate_button), primary = true, modifier = Modifier.fillMaxWidth(), onClick = { openUrl(context, BuildConfig.DONATE_URL) })
                Spacer(Modifier.height(6.dp))
                Text("CloudTips · Т-Банк", fontSize = 12.sp, color = c.subText)
            }
        }
    }
}
