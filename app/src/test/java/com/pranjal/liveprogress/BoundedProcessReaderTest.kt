package com.pranjal.liveprogress

import java.io.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class BoundedProcessReaderTest {
    private class Child(val stream: InputStream, val exit: Int = 0) : Process() {
        var destroyed = false
        var onDestroy: () -> Unit = {}
        override fun getInputStream() = stream
        override fun getErrorStream(): InputStream = ByteArrayInputStream(byteArrayOf())
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun waitFor() = exit
        override fun exitValue() = exit
        override fun destroy() { destroyed = true; onDestroy() }
    }
    private fun child(text: String, exit: Int = 0) = Child(ByteArrayInputStream(text.toByteArray()), exit)

    @Test fun successfulReadPreservesExactlyTheBoundedOutputAndCleansUp() {
        val p = child("current clients\n")
        assertEquals("current clients\n", BoundedProcessReader.read(p, maxChars = 16))
        assertTrue(p.destroyed)
    }
    @Test fun outputAtLimitIsAcceptedButOneCharacterOverIsDiscarded() {
        assertEquals("1234", BoundedProcessReader.read(child("1234"), maxChars = 4))
        val p = child("12345")
        assertNull(BoundedProcessReader.read(p, maxChars = 4))
        assertTrue(p.destroyed)
    }
    @Test fun largeOutputIsNotTruncatedIntoAValidLookingPartialDump() {
        val p = child("x".repeat(20_000))
        assertNull(BoundedProcessReader.read(p, maxChars = 8_192))
        assertTrue(p.destroyed)
    }
    @Test fun nonzeroExitNeverReturnsApparentlyValidDiagnostics() {
        val p = child("valid-looking output", exit = 1)
        assertNull(BoundedProcessReader.read(p))
        assertTrue(p.destroyed)
    }
    @Test fun streamFailureCleansUpAndReturnsUnknown() {
        val p = Child(object : InputStream() { override fun read(): Int = throw IOException("test failure") })
        assertNull(BoundedProcessReader.read(p))
        assertTrue(p.destroyed)
    }
    @Test(timeout = 3_000) fun stalledReadIsDestroyedByTimeoutAndNeverReturnsPartialCount() {
        val released = CountDownLatch(1)
        val p = Child(object : InputStream() {
            override fun read(): Int {
                check(released.await(2, TimeUnit.SECONDS)) { "Watchdog failed to destroy child" }
                return -1
            }
        })
        p.onDestroy = { released.countDown() }
        assertNull(BoundedProcessReader.read(p, timeoutMillis = 25))
        assertTrue(p.destroyed)
    }
    @Test fun emptySuccessfulOutputIsDistinctFromFailure() {
        assertEquals("", BoundedProcessReader.read(child(""), maxChars = 0))
        assertNull(BoundedProcessReader.read(child("x"), maxChars = 0))
    }
}
