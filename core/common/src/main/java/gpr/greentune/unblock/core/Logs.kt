package gpr.greentune.unblock.core

import android.content.Context
import java.io.File

object Logs {
    fun engineLog(context: Context): File = File(context.filesDir, "logs/engine.log").also { f ->
        f.parentFile?.mkdirs()
        if (f.exists() && f.length() > 512 * 1024) f.delete()
    }

    fun tail(context: Context, lines: Int = 300): String {
        val f = File(context.filesDir, "logs/engine.log")
        if (!f.exists()) return ""
        return try {
            f.readLines().takeLast(lines).joinToString("\n")
        } catch (_: Exception) {
            ""
        }
    }

    fun append(context: Context, line: String) {
        try {
            engineLog(context).appendText("[gt] $line\n")
        } catch (_: Exception) {
        }
    }
}
