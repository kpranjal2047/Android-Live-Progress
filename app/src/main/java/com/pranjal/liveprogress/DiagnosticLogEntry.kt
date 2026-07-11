package com.pranjal.liveprogress

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DiagnosticLogEntry(
    val timestampMillis: Long,
    val logType: String,
    val message: String
)

object DiagnosticLogStore {
    private const val ENTRY_SEPARATOR = "\u001E"
    private const val FIELD_SEPARATOR = "\u001F"
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun prepend(
        entries: List<DiagnosticLogEntry>,
        entry: DiagnosticLogEntry
    ): List<DiagnosticLogEntry> {
        return listOf(entry) + entries
    }

    fun prune(
        entries: List<DiagnosticLogEntry>,
        policy: LogClearPolicy,
        nowMillis: Long
    ): List<DiagnosticLogEntry> {
        val retentionMillis = policy.retentionMillis ?: return entries
        val cutoff = nowMillis - retentionMillis
        return entries.filter { it.timestampMillis >= cutoff }
    }

    fun format(
        entry: DiagnosticLogEntry,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val timestamp = timestampFormatter
            .withZone(zoneId)
            .format(Instant.ofEpochMilli(entry.timestampMillis))
        return "[$timestamp] [${entry.logType}] ${entry.message}"
    }

    fun exportText(
        entries: List<DiagnosticLogEntry>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (entries.isEmpty()) return ""
        return entries.joinToString(separator = "\n", postfix = "\n") { entry ->
            format(entry, zoneId)
        }
    }

    fun encode(entries: List<DiagnosticLogEntry>): String {
        return entries.joinToString(ENTRY_SEPARATOR) { entry ->
            listOf(
                entry.timestampMillis.toString(),
                entry.logType.cleanLogField(),
                entry.message.cleanLogField()
            ).joinToString(FIELD_SEPARATOR)
        }
    }

    fun decode(value: String?): List<DiagnosticLogEntry> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(ENTRY_SEPARATOR)
            .mapNotNull { encoded ->
                val parts = encoded.split(FIELD_SEPARATOR)
                if (parts.size != 3) return@mapNotNull null
                DiagnosticLogEntry(
                    timestampMillis = parts[0].toLongOrNull() ?: return@mapNotNull null,
                    logType = parts[1],
                    message = parts[2]
                )
            }
    }
}

private fun String.cleanLogField(): String {
    return replace('\n', ' ')
        .replace('\r', ' ')
        .replace("\u001E", " ")
        .replace("\u001F", " ")
        .trim()
}
