package gpr.greentune.unblock.ui.aero

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun AeroDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = Aero.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        if (c.dark) Color(0xFF0E1913) else Color(0xFFF3F9F5),
                        cornerRadius = CornerRadius(14.dp.toPx())
                    )
                }
                .glass(c, 24.dp)
                .padding(18.dp)
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            Spacer(Modifier.height(10.dp))
            content()
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, androidx.compose.ui.Alignment.End)) {
                buttons()
            }
        }
    }
}
