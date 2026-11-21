package gpr.greentune.unblock.core

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

object Ciadpi {
    fun binary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, "libciadpi.so")

    fun start(context: Context, port: Int, args: List<String>, noQuic: Boolean, log: File? = null, dnsPort: Int = 0): Process {
        val cmd = ArrayList<String>()
        cmd.add(binary(context).absolutePath)
        cmd.addAll(listOf("-i", "127.0.0.1", "-p", port.toString()))
        cmd.addAll(args)
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        if (log != null) {
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log))
        } else {
            pb.redirectOutput(ProcessBuilder.Redirect.to(File("/dev/null")))
        }
        if (noQuic) pb.environment()["GT_NOQUIC"] = "1"
        if (dnsPort > 0) pb.environment()["GT_DNS"] = dnsPort.toString()
        return pb.start()
    }

    suspend fun awaitReady(port: Int, process: Process, timeoutMs: Long): Boolean = withContext(Dispatchers.IO) {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        var ready = false
        while (SystemClock.elapsedRealtime() < end) {
            if (!process.isAlive) break
            if (probe(port)) {
                ready = true
                break
            }
            delay(60)
        }
        ready
    }

    private fun probe(port: Int): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", port), 300)
            s.soTimeout = 600
            s.getOutputStream().write(byteArrayOf(5, 1, 0))
            val input = s.getInputStream()
            input.read() == 5 && input.read() == 0
        }
    } catch (_: IOException) {
        false
    }

    fun freePort(): Int = ServerSocket(0).use { it.localPort }

    fun isPortFree(port: Int): Boolean = try {
        ServerSocket(port).use { true }
    } catch (_: IOException) {
        false
    }

    fun stop(process: Process?) {
        if (process == null) return
        try {
            process.destroy()
            if (!process.waitFor(800, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        } catch (_: Exception) {
        }
    }

    suspend fun validate(context: Context, args: List<String>): Boolean {
        val port = freePort()
        val p = withContext(Dispatchers.IO) { start(context, port, args, false) }
        val ok = awaitReady(port, p, 2500)
        stop(p)
        return ok
    }
}
