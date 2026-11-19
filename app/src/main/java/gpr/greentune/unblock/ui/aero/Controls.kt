package gpr.greentune.unblock.ui.aero

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AeroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
    icon: Painter? = null,
) {
    val c = Aero.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val glowA by animateFloatAsState(if (enabled && (hovered || focused || pressed)) 1f else 0f, tween(200), label = "g")
    val pressA by animateFloatAsState(if (pressed) 1f else 0f, tween(150), label = "p")
    val base = when {
        danger -> c.danger
        primary -> c.accent
        else -> if (c.dark) Color(0xFF3B5664) else Color(0xFFE9F3F8)
    }
    val colored = primary || danger
    Box(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .glow(if (colored) base else c.tint, glowA * 0.9f, 12.dp, 22.dp)
            .drawBehind {
                val r = size.height / 2
                drawRoundRect(
                    Color.Black.copy(alpha = (if (c.dark) 0.35f else 0.16f) * (1f - pressA * 0.6f)),
                    topLeft = Offset(0f, 2.dp.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(r, r)
                )
            }
            .clip(RoundedCornerShape(50))
            .drawBehind {
                val r = size.height / 2
                val one = 1.dp.toPx()
                val alpha = if (enabled) 1f else 0.45f
                val deep = lerp(base, Color.Black, if (colored) 0.28f + 0.12f * pressA else 0.10f + 0.08f * pressA)
                val mid = lerp(base, Color.Black, 0.06f * pressA)
                val glowC = lerp(base, Color.White, if (colored) 0.55f else 0.85f)
                drawRoundRect(Brush.verticalGradient(0f to deep, 0.45f to mid, 1f to glowC), cornerRadius = CornerRadius(r, r), alpha = alpha)
                drawRoundRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = (if (colored) 0.55f else 0.65f) * alpha), Color.Transparent),
                        Offset(size.width / 2, size.height * 1.15f),
                        size.width * 0.55f
                    ),
                    cornerRadius = CornerRadius(r, r)
                )
                val gx = 1.5f * one + size.height * 0.18f
                val gh = size.height * 0.48f
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = (0.92f - 0.35f * pressA) * alpha), Color.White.copy(alpha = 0.18f * alpha)),
                        startY = one, endY = one + gh
                    ),
                    topLeft = Offset(gx, one * 1.5f),
                    size = Size(size.width - gx * 2, gh),
                    cornerRadius = CornerRadius(gh / 2, gh / 2)
                )
                drawRoundRect(
                    lerp(base, Color.Black, 0.45f).copy(alpha = (if (c.dark) 0.8f else 0.55f) * alpha),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(one)
                )
                if (glowA > 0f) {
                    drawRoundRect(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.30f * glowA), Color.Transparent),
                            Offset(size.width / 2, size.height),
                            size.width * 0.6f
                        ),
                        cornerRadius = CornerRadius(r, r)
                    )
                }
            }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                color = if (primary || danger) Color.White else c.text,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AeroProgressBar(
    fraction: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    color: Color = Aero.colors.accent,
) {
    val c = Aero.colors
    val t = rememberInfiniteTransition(label = "pb")
    val run by t.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart), label = "run")
    val animated by animateFloatAsState((fraction ?: 0f).coerceIn(0f, 1f), tween(250), label = "f")
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = 3.dp.toPx()
        val one = 1.dp.toPx()
        drawRoundRect(
            Brush.verticalGradient(
                if (c.dark) listOf(Color(0xFF1B231E), Color(0xFF2A352E)) else listOf(Color(0xFFDADFDB), Color(0xFFF7F9F8))
            ),
            cornerRadius = CornerRadius(r, r)
        )
        val fillW: Float
        val fillX: Float
        if (fraction == null) {
            fillW = size.width * 0.28f
            fillX = (size.width + fillW) * ((run + 0.4f) / 1.8f) - fillW
        } else {
            fillW = size.width * animated
            fillX = 0f
        }
        clipRect(left = maxOf(0f, fillX), right = minOf(size.width, fillX + fillW)) {
            drawRoundRect(
                Brush.verticalGradient(
                    0f to lerp(color, Color.White, 0.55f),
                    0.45f to lerp(color, Color.White, 0.15f),
                    0.5f to color,
                    1f to lerp(color, Color.Black, 0.18f)
                ),
                cornerRadius = CornerRadius(r, r)
            )
            val bandW = size.width * 0.25f
            val bx = fillX + (fillW + bandW) * ((run + 0.4f) / 1.8f) - bandW
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                    startX = bx, endX = bx + bandW
                )
            )
        }
        drawRoundRect(Color.Black.copy(alpha = if (c.dark) 0.6f else 0.32f), cornerRadius = CornerRadius(r, r), style = Stroke(one))
        drawRoundRect(
            Color.White.copy(alpha = 0.35f),
            topLeft = Offset(one, one),
            size = Size(size.width - 2 * one, size.height - 2 * one),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(one)
        )
    }
}

@Composable
fun AeroToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Aero.colors
    val pos by animateFloatAsState(if (checked) 1f else 0f, tween(200), label = "t")
    val track by animateColorAsState(if (checked) c.accent else if (c.dark) Color(0xFF39433D) else Color(0xFFC9D1CC), tween(200), label = "c")
    Canvas(
        modifier
            .size(50.dp, 28.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
    ) {
        val h = size.height
        val r = h / 2
        val one = 1.dp.toPx()
        drawRoundRect(
            Brush.verticalGradient(0f to lerp(track, Color.Black, 0.15f), 0.5f to track, 1f to lerp(track, Color.White, 0.25f)),
            cornerRadius = CornerRadius(r, r),
            alpha = if (enabled) 1f else 0.5f
        )
        drawRoundRect(Color.Black.copy(alpha = 0.35f), cornerRadius = CornerRadius(r, r), style = Stroke(one))
        val d = h - 4.dp.toPx()
        val x = 2.dp.toPx() + (size.width - d - 4.dp.toPx()) * pos
        val center = Offset(x + d / 2, h / 2)
        drawCircle(Color.Black.copy(alpha = 0.25f), d / 2 + one, center.copy(y = center.y + one))
        drawCircle(Brush.verticalGradient(listOf(Color.White, Color(0xFFDDE3DF)), center.y - d / 2, center.y + d / 2), d / 2, center)
        drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Transparent), center.y - d / 2, center.y), d / 2 - 2 * one, center.copy(y = center.y - one))
        drawCircle(Color.Black.copy(alpha = 0.25f), d / 2, center, style = Stroke(one))
    }
}

@Composable
fun AeroSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, range: ClosedFloatingPointRange<Float> = 0f..1f) {
    val c = Aero.colors
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = c.accent,
            inactiveTrackColor = c.subText.copy(alpha = 0.3f)
        )
    )
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Aero.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hl by animateFloatAsState(if (pressed) 1f else 0f, tween(150), label = "h")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .drawBehind { if (hl > 0f) drawRect(c.tint.copy(alpha = 0.14f * hl)) }
            .then(if (onClick != null) Modifier.clickable(interactionSource = source, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 15.sp)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = c.subText, fontSize = 12.5.sp)
        }
        trailing()
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    SettingRow(title, subtitle, onClick = { if (enabled) onChange(!checked) }) {
        AeroToggle(checked, onChange, enabled = enabled)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val c = Aero.colors
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp),
        color = if (c.dark) c.accent else c.accentDark,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Aero.colors
    Row(
        modifier
            .fillMaxWidth()
            .glass(c, 14.dp, glossFraction = 0.5f)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for ((id, label) in options) {
            val on = id == selected
            val a by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "s")
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .drawBehind {
                        if (a > 0f) {
                            drawRoundRect(
                                Brush.verticalGradient(
                                    0f to lerp(c.accent, Color.White, 0.4f),
                                    0.5f to c.accent,
                                    1f to lerp(c.accent, Color.Black, 0.1f)
                                ),
                                alpha = a,
                                cornerRadius = CornerRadius(6.dp.toPx())
                            )
                        }
                    }
                    .clickable { onSelect(id) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) Color.White else c.text, fontSize = 13.5.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Canvas(modifier.size(size)) {
        drawCircle(Brush.radialGradient(listOf(lerp(color, Color.White, 0.5f), color, lerp(color, Color.Black, 0.3f)), Offset(this.size.width * 0.35f, this.size.height * 0.35f), this.size.width * 0.7f))
        drawCircle(Color.Black.copy(alpha = 0.3f), style = Stroke(1f))
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = Aero.colors.warn) {
    Box(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.35f), color)))
                drawRoundRect(Color.Black.copy(alpha = 0.25f), cornerRadius = CornerRadius(5.dp.toPx()), style = Stroke(1f))
            }
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.offset(y = (-0.5).dp))
    }
}
