package com.pranjal.liveprogress

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Keeps diagnostic output bounded and private; always releases the child and watchdog. */
internal object BoundedProcessReader {
    fun read(process: Process, timeoutMillis: Long = 4_000, maxChars: Int = 4_000_000): String? {
        require(timeoutMillis > 0 && maxChars >= 0)
        val expired = AtomicBoolean(false)
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        return try {
            watchdog.schedule({ expired.set(true); process.destroy() }, timeoutMillis, TimeUnit.MILLISECONDS)
            val output = process.inputStream.bufferedReader().use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (count > maxChars - result.length) return null
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
            if (process.waitFor() == 0 && !expired.get()) output else null
        } catch (_: Exception) {
            null
        } finally {
            process.destroy()
            watchdog.shutdownNow()
        }
    }
}
