package gpr.greentune.unblock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gpr.greentune.air.Air
import gpr.greentune.air.AirButton
import gpr.greentune.air.Segmented
import gpr.greentune.air.ToggleRow
import gpr.greentune.air.glass

@Composable
fun ExpertBuilder(onBuild: (String) -> Unit) {
    val c = Air.colors
    var method by rememberSaveable { mutableStateOf("disorder") }
    var pos by rememberSaveable { mutableStateOf("1") }
    var flag by rememberSaveable { mutableStateOf("s") }
    var repeats by rememberSaveable { mutableStateOf("") }
    var tlsrec by rememberSaveable { mutableStateOf(true) }
    var tlsPos by rememberSaveable { mutableStateOf("1") }
    var tlsFlag by rememberSaveable { mutableStateOf("s") }
    var ttl by rememberSaveable { mutableStateOf("8") }
    var fakeSni by rememberSaveable { mutableStateOf("") }
    var md5 by rememberSaveable { mutableStateOf(false) }
    var fakeMod by rememberSaveable { mutableStateOf("rand") }
    var fakeOffset by rememberSaveable { mutableStateOf("") }
    var oobData by rememberSaveable { mutableStateOf("") }
    var hcs by rememberSaveable { mutableStateOf(false) }
    var dcs by rememberSaveable { mutableStateOf(false) }
    var rms by rememberSaveable { mutableStateOf(false) }
    var tlsMinor by rememberSaveable { mutableStateOf("") }
    var udpFake by rememberSaveable { mutableStateOf("0") }
    var dropSack by rememberSaveable { mutableStateOf(false) }
    var tfo by rememberSaveable { mutableStateOf(false) }
    var defTtl by rememberSaveable { mutableStateOf("") }
    var proto by rememberSaveable { mutableStateOf("all") }
    var round by rememberSaveable { mutableStateOf("") }
    var auto by rememberSaveable { mutableStateOf(false) }

    fun posStr(p: String, f: String, r: String = ""): String {
        val base = p.ifBlank { "1" } + if (r.isNotBlank()) ":$r" else ""
        return if (f == "none") base else "$base+$f"
    }

    fun build(): String {
        val t = mutableListOf<String>()
        if (defTtl.isNotBlank()) t += "-g$defTtl"
        if (tfo) t += "-F"
        if (dropSack) t += "-Y"
        when (proto) {
            "tls" -> t += "-Kt"
            "http" -> t += "-Kh"
            "web" -> t += "-Kt,h"
        }
        if (round.isNotBlank()) t += "-R$round"
        val m = when (method) {
            "split" -> "-s"
            "disorder" -> "-d"
            "fake" -> "-f"
            "oob" -> "-o"
            "disoob" -> "-q"
            else -> null
        }
        if (m != null) t += m + posStr(pos, flag, repeats)
        if (method == "fake") {
            if (ttl.isNotBlank()) t += "-t$ttl"
            if (md5) t += "-S"
            if (fakeSni.isNotBlank()) t += "-n$fakeSni"
            if (fakeMod != "none") t += "-Q$fakeMod"
            if (fakeOffset.isNotBlank()) t += "-O$fakeOffset"
        }
        if ((method == "oob" || method == "disoob") && oobData.isNotBlank()) t += "-e${oobData.take(1)}"
        if (tlsrec) t += "-r" + posStr(tlsPos, tlsFlag)
        if (tlsMinor.isNotBlank()) t += "-m$tlsMinor"
        val mods = listOfNotNull("hcsmix".takeIf { hcs }, "dcsmix".takeIf { dcs }, "rmspace".takeIf { rms })
        if (mods.isNotEmpty()) t += "-M" + mods.joinToString(",")
        if ((udpFake.toIntOrNull() ?: 0) > 0) t += "-a$udpFake"
        if (auto) {
            t += "-At,r,s"
            t += "-d1+s"
            t += "-r1+s"
        }
        return t.joinToString(" ")
    }

    Column(
        Modifier.fillMaxWidth().glass(c, 18.dp, glossFraction = 0.2f).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Конструктор", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        Label("Способ разбиения")
        Segmented(
            listOf("split" to "split", "disorder" to "disorder", "fake" to "fake"),
            method, { method = it }, Modifier.fillMaxWidth()
        )
        Segmented(
            listOf("oob" to "oob", "disoob" to "disoob", "none" to "—"),
            method, { method = it }, Modifier.fillMaxWidth()
        )
        if (method != "none") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field("Позиция", pos, { pos = it }, Modifier.weight(1f), number = true)
                Spacer(Modifier.width(8.dp))
                Field("Повторы", repeats, { repeats = it }, Modifier.weight(1f), number = true)
            }
            Segmented(
                listOf("none" to "смещение", "s" to "+SNI", "h" to "+Host", "m" to "+середина", "e" to "+конец"),
                flag, { flag = it }, Modifier.fillMaxWidth()
            )
        }
        if (method == "fake") {
            Row {
                Field("TTL фейка", ttl, { ttl = it }, Modifier.weight(1f), number = true)
                Spacer(Modifier.width(8.dp))
                Field("Смещение фейка", fakeOffset, { fakeOffset = it }, Modifier.weight(1f))
            }
            Field("SNI в фейке (? # *)", fakeSni, { fakeSni = it }, Modifier.fillMaxWidth())
            Segmented(listOf("rand" to "rand", "orig" to "orig", "none" to "без мод."), fakeMod, { fakeMod = it }, Modifier.fillMaxWidth())
            ToggleRow("MD5 Signature", "-S, для фейковых пакетов", md5, { md5 = it })
        }
        if (method == "oob" || method == "disoob") {
            Field("Байт OOB", oobData, { oobData = it }, Modifier.fillMaxWidth())
        }
        ToggleRow("TLS record split", "-r, разбить TLS-запись", tlsrec, { tlsrec = it })
        if (tlsrec) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field("Позиция", tlsPos, { tlsPos = it }, Modifier.weight(1f), number = true)
                Spacer(Modifier.width(8.dp))
                Segmented(listOf("none" to "смещ.", "s" to "+SNI", "m" to "+сер."), tlsFlag, { tlsFlag = it }, Modifier.weight(2f))
            }
        }
        Label("Протоколы")
        Segmented(listOf("all" to "все", "tls" to "TLS", "http" to "HTTP", "web" to "TLS+HTTP"), proto, { proto = it }, Modifier.fillMaxWidth())
        Row {
            Field("UDP фейки", udpFake, { udpFake = it }, Modifier.weight(1f), number = true)
            Spacer(Modifier.width(8.dp))
            Field("TLS minor", tlsMinor, { tlsMinor = it }, Modifier.weight(1f), number = true)
        }
        Row {
            Field("TTL всех пакетов", defTtl, { defTtl = it }, Modifier.weight(1f), number = true)
            Spacer(Modifier.width(8.dp))
            Field("Номера запросов", round, { round = it }, Modifier.weight(1f))
        }
        Label("HTTP")
        ToggleRow("hcsmix", "Смешать регистр заголовка Host", hcs, { hcs = it })
        ToggleRow("dcsmix", "Смешать регистр домена", dcs, { dcs = it })
        ToggleRow("rmspace", "Убрать пробел после Host:", rms, { rms = it })
        ToggleRow("Drop SACK", "-Y", dropSack, { dropSack = it })
        ToggleRow("TCP Fast Open", "-F", tfo, { tfo = it })
        ToggleRow("Запасная стратегия", "-A: при сбросе или ошибке TLS пробовать disorder", auto, { auto = it })
        AirButton("Собрать команду", primary = true, modifier = Modifier.fillMaxWidth(), onClick = { onBuild(build()) })
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 12.5.sp, color = Air.colors.subText, modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, number: Boolean = false) {
    val c = Air.colors
    Column(modifier) {
        Text(label, fontSize = 11.5.sp, color = c.subText)
        BasicTextField(
            value = value,
            onValueChange = { v -> onChange(if (number) v.filter { it.isDigit() || it == '-' }.take(6) else v.filter { !it.isWhitespace() }.take(64)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Ascii),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = c.text),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth().glass(c, 10.dp, glossFraction = 0.2f).padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}
