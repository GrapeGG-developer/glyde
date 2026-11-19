package gpr.greentune.unblock.core

import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import gpr.greentune.unblock.data.ServiceChoice
import gpr.greentune.unblock.data.SettingsStore
import gpr.greentune.unblock.data.TuneResult
import gpr.greentune.unblock.data.TuneStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

object ShareCode {
    private const val PREFIX = "GTU2:"

    fun build(): String? {
        val r = TuneStore.current.value
        val s = SettingsStore.value
        val o = JSONObject()
        if (r != null) {
            o.put("d", r.defaultStrategy ?: JSONObject.NULL)
            o.put("c", JSONArray().apply {
                r.choices.filter { it.strategyId != null && !it.direct }.forEach {
                    put(JSONArray().put(it.serviceId).put(it.strategyId))
                }
            })
            o.put("n", r.label)
        }
        if (s.customDomains.isNotEmpty()) o.put("h", JSONArray(s.customDomains))
        if (s.expert && s.customArgsEnabled && s.customArgs.isNotBlank()) o.put("x", s.customArgs)
        if (o.length() == 0) return null
        val raw = o.toString().toByteArray()
        val def = Deflater(9, true)
        def.setInput(raw)
        def.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!def.finished()) {
            val n = def.deflate(buf)
            out.write(buf, 0, n)
        }
        def.end()
        return PREFIX + Base64.encodeToString(out.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun decode(code: String): JSONObject? {
        val t = code.trim().substringAfter(PREFIX, "").trim()
        if (t.isEmpty()) return null
        return try {
            val bytes = Base64.decode(t, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val inf = Inflater(true)
            inf.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            var guard = 0
            while (!inf.finished() && guard < 4096) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
                guard++
            }
            inf.end()
            JSONObject(String(out.toByteArray()))
        } catch (_: Exception) {
            null
        }
    }

    fun looksLike(text: String?): Boolean = text != null && text.contains(PREFIX)

    fun apply(text: String): Boolean {
        val code = text.substring(text.indexOf(PREFIX)).split(Regex("\\s")).first()
        val o = decode(code) ?: return false
        val presets = Presets.data.value
        val choices = mutableListOf<ServiceChoice>()
        o.optJSONArray("c")?.let { arr ->
            for (i in 0 until arr.length()) {
                val pair = arr.optJSONArray(i) ?: continue
                val sid = pair.optString(0)
                val st = pair.optString(1)
                if (presets.strategy(st) != null) choices.add(ServiceChoice(sid, st, false, true, 0))
            }
        }
        val def = if (o.isNull("d")) null else o.optString("d").takeIf { presets.strategy(it) != null }
        val hosts = o.optJSONArray("h")?.let { a -> (0 until a.length()).mapNotNull { Presets.normalizeDomain(a.optString(it)) } }.orEmpty()
        val args = o.optString("x", "")
        if (choices.isEmpty() && def == null && hosts.isEmpty() && args.isBlank()) return false
        if (choices.isNotEmpty() || def != null) {
            val key = TuneStore.activeKey.ifBlank { "manual" }
            val prev = TuneStore.get(key)
            val kept = prev?.choices.orEmpty().filter { c -> choices.none { it.serviceId == c.serviceId } }
            TuneStore.save(
                TuneResult(
                    key, prev?.label ?: o.optString("n", ""), System.currentTimeMillis(), prev?.full ?: false,
                    def ?: prev?.defaultStrategy, kept + choices
                )
            )
        }
        SettingsStore.update { s ->
            var n = s
            if (hosts.isNotEmpty()) n = n.copy(customDomains = (s.customDomains + hosts).distinct())
            if (args.isNotBlank()) n = n.copy(expert = true, customArgs = args, customArgsEnabled = true)
            val ids = choices.map { it.serviceId }.filter { it != Presets.CUSTOM && presets.service(it) != null }
            if (ids.isNotEmpty()) {
                val cur = Presets.enabledIds(presets, s)
                n = n.copy(servicesSet = true, services = cur + ids)
            }
            n
        }
        return true
    }

    fun qr(text: String, size: Int = 0): BitMatrix =
        QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1)
        )
}
