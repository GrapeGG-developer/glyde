package gpr.greentune.unblock.ui.aero

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BarItem(val id: String, val icon: Int, val label: String)

@Composable
fun Superbar(items: List<BarItem>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Aero.colors
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val top = if (c.dark) Color(0xFF16231B).copy(alpha = 0.86f) else Color(0xFFDCEFE3).copy(alpha = 0.80f)
                val bottom = if (c.dark) Color(0xFF07100B).copy(alpha = 0.92f) else Color(0xFFB9DCC6).copy(alpha = 0.88f)
                drawRect(Brush.verticalGradient(listOf(top, bottom)))
                drawRect(c.glassTint)
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (c.dark) 0.10f else 0.45f), Color.Transparent), 0f, size.height * 0.5f), size = Size(size.width, size.height * 0.5f))
                drawLine(Color.Black.copy(alpha = 0.45f), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                drawLine(Color.White.copy(alpha = if (c.dark) 0.18f else 0.8f), Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 1.dp.toPx())
            }
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (item in items) {
                SuperbarButton(item, item.id == selected) { onSelect(item.id) }
            }
        }
    }
}

@Composable
private fun SuperbarButton(item: BarItem, selected: Boolean, onClick: () -> Unit) {
    val c = Aero.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sel by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "sel")
    val pr by animateFloatAsState(if (pressed) 1f else 0f, tween(150), label = "pr")
    Column(
        Modifier
            .size(width = 72.dp, height = 56.dp)
            .clip(RoundedCornerShape(6.dp))
            .drawBehind {
                val a = maxOf(sel, pr * 0.7f)
                if (a > 0f) {
                    val r = CornerRadius(6.dp.toPx())
                    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f * a), Color.White.copy(alpha = 0.10f * a))), cornerRadius = r)
                    drawRoundRect(
                        Brush.radialGradient(listOf(c.tint.copy(alpha = 0.65f * a), Color.Transparent), Offset(size.width / 2, size.height), size.width * 0.7f),
                        cornerRadius = r
                    )
                    drawRoundRect(Color.Black.copy(alpha = 0.35f * a), cornerRadius = r, style = Stroke(1.dp.toPx()))
                    drawRoundRect(
                        Color.White.copy(alpha = 0.6f * a),
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                        cornerRadius = r,
                        style = Stroke(1.dp.toPx())
                    )
                }
            }
            .clickable(interactionSource = source, indication = null, role = Role.Tab, onClick = onClick)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(item.icon), item.label, Modifier.size(32.dp).scale(1f + 0.08f * sel - 0.05f * pr))
        Text(item.label, fontSize = 10.5.sp, color = c.text, maxLines = 1)
    }
}
