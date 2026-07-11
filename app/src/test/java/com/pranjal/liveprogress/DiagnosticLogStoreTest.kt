package com.pranjal.liveprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class DiagnosticLogStoreTest {
    @Test
    fun formatsLogLineInStandardFormat() {
        val line = DiagnosticLogStore.format(
            DiagnosticLogEntry(
                timestampMillis = 1_700_000_000_000L,
                logType = "mirror",
                message = "shown"
            ),
            ZoneId.of("UTC")
        )

        assertEquals("[2023-11-14 22:13:20] [mirror] shown", line)
    }

    @Test
    fun prependsEntriesForReverseChronologicalOrder() {
        val older = DiagnosticLogEntry(1_000L, "media", "old")
        val newer = DiagnosticLogEntry(2_000L, "media", "new")

        val entries = DiagnosticLogStore.prepend(
            DiagnosticLogStore.prepend(emptyList(), older),
            newer
        )

        assertEquals(listOf(newer, older), entries)
    }

    @Test
    fun exportTextUsesFormattedLinesNewestFirst() {
        val entries = listOf(
            DiagnosticLogEntry(2_000L, "media", "new"),
            DiagnosticLogEntry(1_000L, "mirror", "old")
        )

        val text = DiagnosticLogStore.exportText(entries, ZoneId.of("UTC"))

        assertEquals(
            "[1970-01-01 00:00:02] [media] new\n" +
                "[1970-01-01 00:00:01] [mirror] old\n",
            text
        )
    }

    @Test
    fun encodeDecodeRoundTripSanitizesUnsafeSeparators() {
        val decoded = DiagnosticLogStore.decode(
            DiagnosticLogStore.encode(
                listOf(DiagnosticLogEntry(1_000L, "mirror", "line one\nline two"))
            )
        )

        assertEquals(listOf(DiagnosticLogEntry(1_000L, "mirror", "line one line two")), decoded)
    }

    @Test
    fun timedRetentionPrunesOldEntries() {
        val fresh = DiagnosticLogEntry(10_000L, "mirror", "fresh")
        val old = DiagnosticLogEntry(1_000L, "mirror", "old")

        val pruned = DiagnosticLogStore.prune(
            entries = listOf(fresh, old),
            policy = LogClearPolicy.AFTER_1_HOUR,
            nowMillis = 10_000L + 60L * 60L * 1000L
        )

        assertEquals(listOf(fresh), pruned)
    }

    @Test
    fun onAppExitPolicyDoesNotPruneByTime() {
        val old = DiagnosticLogEntry(1_000L, "mirror", "old")

        val pruned = DiagnosticLogStore.prune(
            entries = listOf(old),
            policy = LogClearPolicy.ON_APP_EXIT,
            nowMillis = Long.MAX_VALUE
        )

        assertEquals(listOf(old), pruned)
    }

    @Test
    fun loggingLevelRulesAndDefaults() {
        assertEquals(DiagnosticLoggingLevel.OFF, DeveloperPreferences.DEFAULT_LOGGING_LEVEL)
        assertEquals(LogClearPolicy.AFTER_1_HOUR, DeveloperPreferences.DEFAULT_LOG_CLEAR_POLICY)

        assertFalse(DiagnosticLoggingLevel.OFF.allows(DiagnosticLoggingLevel.NORMAL))
        assertTrue(DiagnosticLoggingLevel.NORMAL.allows(DiagnosticLoggingLevel.NORMAL))
        assertFalse(DiagnosticLoggingLevel.NORMAL.allows(DiagnosticLoggingLevel.VERBOSE))
        assertTrue(DiagnosticLoggingLevel.VERBOSE.allows(DiagnosticLoggingLevel.NORMAL))
        assertTrue(DiagnosticLoggingLevel.VERBOSE.allows(DiagnosticLoggingLevel.VERBOSE))
    }
}
