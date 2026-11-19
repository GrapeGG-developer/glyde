package gpr.greentune.unblock.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gpr.greentune.unblock.R
import gpr.greentune.unblock.ui.aero.Aero
import gpr.greentune.unblock.ui.aero.AeroButton
import gpr.greentune.unblock.ui.aero.Badge
import gpr.greentune.unblock.ui.aero.GlassPanel

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val c = Aero.colors
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.logo_glass), null, Modifier.size(150.dp))
        Text(stringResource(R.string.app_name), fontSize = 32.sp, fontWeight = FontWeight.Light, color = c.text)
        Spacer(Modifier.height(6.dp))
        Badge(stringResource(R.string.unofficial_port))
        Spacer(Modifier.height(18.dp))
        GlassPanel(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Point(R.drawable.ic_menu_home, R.string.onb_1_title, R.string.onb_1_text)
                Point(R.drawable.ic_menu_tune, R.string.onb_2_title, R.string.onb_2_text)
                Point(R.drawable.ic_menu_settings, R.string.onb_3_title, R.string.onb_3_text)
                Text(stringResource(R.string.onb_telemetry), fontSize = 11.5.sp, color = c.subText, textAlign = TextAlign.Start)
            }
        }
        Spacer(Modifier.height(18.dp))
        AeroButton(stringResource(R.string.onb_start), primary = true, onClick = onDone, modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth())
    }
}

@Composable
private fun Point(icon: Int, title: Int, text: Int) {
    val c = Aero.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(icon), null, Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(title), fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            Text(stringResource(text), fontSize = 13.sp, color = c.subText)
        }
    }
}
