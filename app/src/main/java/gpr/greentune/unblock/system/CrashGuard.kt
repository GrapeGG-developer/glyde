package gpr.greentune.unblock.system

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object CrashGuard {
    fun file(context: Context) = File(context.filesDir, "crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                file(app).writeText("thread=${t.name}\n$sw")
            } catch (_: Exception) {
            }
            previous?.uncaughtException(t, e)
        }
    }
}
