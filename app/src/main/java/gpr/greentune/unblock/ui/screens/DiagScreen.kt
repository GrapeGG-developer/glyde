package gpr.greentune.unblock.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gpr.greentune.unblock.R
import gpr.greentune.unblock.core.Diag
import gpr.greentune.unblock.core.DiagItem
import gpr.greentune.unblock.core.Verdict
import gpr.greentune.unblock.ui.aero.Aero
import gpr.greentune.unblock.ui.aero.AeroButton
import gpr.greentune.unblock.ui.aero.AeroProgressBar
import gpr.greentune.unblock.ui.aero.GlassPanel
import gpr.greentune.unblock.ui.aero.StatusDot

@Composable
fun DiagScreen() {
    val c = Aero.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val st by Diag.state.collectAsStateWithLifecycle()

    ScreenColumn(stringResource(R.string.diag_title)) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.diag_explain), fontSize = 13.5.sp, color = c.subText)
                if (st.running) {
                    Text(stringResource(R.string.diag_running, st.done, st.total), fontSize = 14.sp, color = c.text)
                    AeroProgressBar(if (st.total > 0 && st.done < st.total) st.done.toFloat() / st.total else null)
                    AeroButton(stringResource(R.string.cancel), modifier = Modifier.fillMaxWidth(), onClick = { Diag.cancel() })
                } else {
                    AeroButton(stringResource(R.string.diag_start), primary = true, modifier = Modifier.fillMaxWidth(), onClick = { Diag.start(context) })
                }
                st.error?.let { Text(it, fontSize = 12.sp, color = c.danger) }
            }
        }

        if (st.items.isNotEmpty()) {
            GlassPanel(Modifier.fillMaxWidth()) {
                Column {
                    val n = st.items
                    Text(
                        stringResource(
                            R.string.diag_summary,
                            n.count { it.verdict == Verdict.Ok },
                            n.count { it.verdict == Verdict.Dpi },
                            n.count { it.verdict == Verdict.DnsBlocked },
                            n.count { it.verdict == Verdict.IpBlocked }
                        ),
                        fontSize = 13.sp, color = c.subText
                    )
                    Spacer(Modifier.height(8.dp))
                    for (i in st.items) DiagRow(i)
                    AnimatedVisibility(!st.running) {
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AeroButton(stringResource(R.string.diag_copy), modifier = Modifier.weight(1f), onClick = {
                                clipboard.setText(AnnotatedString(Diag.report(context)))
                                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                            })
                            AeroButton(stringResource(R.string.diag_share), modifier = Modifier.weight(1f), onClick = {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Diag.report(context))
                                try {
                                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (_: Exception) {
                                }
                            })
                        }
                    }
                }
            }
        }

        GlassPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.diag_speed), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpeedGauge(st.speed?.mbps ?: 0.0, if (st.speedRunning) st.speedProgress else null)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val sp = st.speed
                        Text(
                            if (sp != null) "%.1f".format(sp.mbps) else "—",
                            fontSize = 34.sp, fontWeight = FontWeight.Light, color = c.text
                        )
                        Text(stringResource(R.string.diag_mbps), fontSize = 13.sp, color = c.subText)
                        if (sp != null) {
                            Text(
                                stringResource(R.string.diag_ping) + " ${sp.pingMs} ms · " + stringResource(R.string.diag_jitter) + " ${sp.jitterMs} ms",
                                fontSize = 13.sp, color = c.subText
                            )
                        }
                    }
                }
                AeroButton(
                    stringResource(R.string.diag_speed_start),
                    enabled = !st.speedRunning,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { Diag.speed(context) }
                )
            }
        }
    }
}

@Composable
private fun DiagRow(i: DiagItem) {
    val c = Aero.colors
    val context = LocalContext.current
    val color = when (i.verdict) {
        Verdict.Ok -> c.ok
        Verdict.DnsBlocked -> c.warn
        else -> if (i.bypass == true) c.warn else c.danger
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        StatusDot(color, Modifier.padding(top = 5.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i.name, fontSize = 14.5.sp, color = c.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (i.direct) Text("${i.directMs} ms", fontSize = 12.sp, color = c.subText)
            }
            Text(Diag.verdictText(context, i.verdict), fontSize = 12.5.sp, color = c.subText)
            if (i.bypass != null) {
                Text(
                    stringResource(if (i.bypass) R.string.diag_bypass_ok else R.string.diag_bypass_fail) +
                        if (i.bypass) " · ${i.bypassMs} ms" else "",
                    fontSize = 12.5.sp, color = if (i.bypass) c.ok else c.danger
                )
            }
        }
    }
}

@Composable
private fun SpeedGauge(mbps: Double, progress: Float?) {
    val c = Aero.colors
    val frac = (kotlin.math.ln(1.0 + mbps) / kotlin.math.ln(1.0 + 500.0)).toFloat().coerceIn(0f, 1f)
    Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(110.dp)) {
            val sw = 10.dp.toPx()
            val inset = sw / 2 + 2.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(c.subText.copy(alpha = 0.22f), 135f, 270f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            val sweep = 270f * (progress ?: frac)
            if (sweep > 0.5f) {
                drawArc(
                    Brush.sweepGradient(listOf(c.accent, Color(0xFF7FE3F5), Color.White, c.accent), Offset(size.width / 2, size.height / 2)),
                    135f, sweep, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent), Offset(size.width * 0.4f, size.height * 0.3f), size.width * 0.4f),
                size.width * 0.3f
            )
        }
    }
}
