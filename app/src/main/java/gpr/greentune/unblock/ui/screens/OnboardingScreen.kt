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
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.logo_glass), null, Modifier.size(112.dp))
        Spacer(Modifier.height(6.dp))
        Badge("by GREENTUNE", color = Aero.colors.accent)
        Spacer(Modifier.height(14.dp))
        GlassPanel(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp), padding = 4.dp) {
            Column(Modifier.padding(vertical = 16.dp)) {
                OnboardingContent(onDone)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.onb_telemetry), fontSize = 11.sp, color = c.subText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            }
        }
    }
}
