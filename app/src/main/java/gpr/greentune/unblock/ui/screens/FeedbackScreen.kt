package gpr.greentune.unblock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gpr.greentune.unblock.R
import gpr.greentune.unblock.net.Backend
import gpr.greentune.unblock.ui.aero.Aero
import gpr.greentune.unblock.ui.aero.AeroButton
import gpr.greentune.unblock.ui.aero.GlassPanel
import gpr.greentune.unblock.ui.aero.glass
import gpr.greentune.unblock.ui.openUrl
import kotlinx.coroutines.launch

@Composable
fun FeedbackScreen() {
    val c = Aero.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Boolean?>(null) }

    ScreenColumn(stringResource(R.string.nav_feedback)) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.feedback_text), fontSize = 13.5.sp, color = c.subText)
                Field(text, { text = it; result = null }, stringResource(R.string.feedback_hint), 140)
                Field(contact, { contact = it }, stringResource(R.string.feedback_contact), 0)
                AeroButton(
                    stringResource(if (sending) R.string.feedback_sending else R.string.feedback_send),
                    primary = true,
                    enabled = !sending && text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        sending = true
                        scope.launch {
                            val ok = Backend.feedback(text.trim(), contact.trim())
                            sending = false
                            result = ok
                            if (ok) text = ""
                        }
                    }
                )
                when (result) {
                    true -> Text(stringResource(R.string.feedback_ok), color = c.ok, fontSize = 14.sp)
                    false -> Column {
                        Text(stringResource(R.string.feedback_fail), color = c.danger, fontSize = 14.sp)
                        AeroButton(stringResource(R.string.feedback_github), modifier = Modifier.fillMaxWidth(), onClick = { openUrl(context, Backend.repoUrl + "/issues/new") })
                    }
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, minHeight: Int) {
    val c = Aero.colors
    Box(Modifier.fillMaxWidth().heightIn(min = maxOf(44, minHeight).dp).glass(c, 8.dp, glossFraction = 0.2f).padding(12.dp)) {
        if (value.isEmpty()) Text(hint, color = c.subText, fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(4000)) },
            textStyle = TextStyle(color = c.text, fontSize = 14.sp),
            cursorBrush = SolidColor(c.accent),
            singleLine = minHeight == 0,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
