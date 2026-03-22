package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.os.SystemClock

object RootShell {
    data class Result(
        val stdout: String,
        val exitCode: Int,
        val timedOut: Boolean,
    )

    private var lastCheckAt: Long = 0L
    private var lastCheckResult: Boolean? = null

    fun isAvailable(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val cached = lastCheckResult
        if (cached != null && now - lastCheckAt < CHECK_TTL_MS) {
            return cached
        }
        val result = run("id", timeoutMs = 1500L)
        val ok = result.exitCode == 0 && result.stdout.contains("uid=0")
        lastCheckAt = now
        lastCheckResult = ok
        return ok
    }

    fun run(command: String, timeoutMs: Long = 2000L): Result {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val readerThread = Thread {
                runCatching {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.forEachLine { line ->
                            output.append(line).append('\n')
                        }
                    }
                }
            }
            readerThread.isDaemon = true
            readerThread.start()
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            var finished = false
            while (SystemClock.elapsedRealtime() < deadline) {
                if (hasExited(process)) {
                    finished = true
                    break
                }
                Thread.sleep(25)
            }
            if (!finished && hasExited(process)) {
                finished = true
            }
            if (!finished) {
                process.destroy()
                val destroyDeadline = SystemClock.elapsedRealtime() + 200L
                while (SystemClock.elapsedRealtime() < destroyDeadline && !hasExited(process)) {
                    Thread.sleep(25)
                }
            }
            readerThread.join(200)
            val exitCode = if (finished) process.exitValue() else -1
            Result(output.toString().trim(), exitCode, !finished)
        } catch (t: Throwable) {
            Result(
                "error: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}",
                -1,
                false
            )
        }
    }

    private fun hasExited(process: Process): Boolean {
        return try {
            process.exitValue()
            true
        } catch (_: IllegalThreadStateException) {
            false
        }
    }

    private const val CHECK_TTL_MS = 60000L
}
