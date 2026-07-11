package com.pranjal.liveprogress

import android.content.Context

object AppDiagnostics {
    private const val PREFS = "android_live_progress_diagnostics"
    private const val KEY_ENTRIES = "log_entries"
    private val deduper = DiagnosticMessageDeduper()

    fun clear(context: Context) {
        deduper.clear()
        BatteryDiagnostics.reset()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun note(context: Context, key: String, value: String) {
        write(context, key, value, DiagnosticLoggingLevel.NORMAL)
    }

    fun verbose(context: Context, key: String, value: String) {
        write(context, key, value, DiagnosticLoggingLevel.VERBOSE)
    }

    fun entries(context: Context): List<DiagnosticLogEntry> {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val developerPreferences = DeveloperPreferences(appContext)
        val entries = DiagnosticLogStore.decode(prefs.getString(KEY_ENTRIES, null))
        val pruned = DiagnosticLogStore.prune(
            entries = entries,
            policy = developerPreferences.logClearPolicy,
            nowMillis = System.currentTimeMillis()
        )
        if (pruned != entries) {
            prefs.edit()
                .putString(KEY_ENTRIES, DiagnosticLogStore.encode(pruned))
                .apply()
        }
        return pruned
    }

    fun pruneExpired(context: Context) {
        entries(context)
    }

    private fun write(
        context: Context,
        key: String,
        value: String,
        messageLevel: DiagnosticLoggingLevel
    ) {
        val appContext = context.applicationContext
        val developerPreferences = DeveloperPreferences(appContext)
        val loggingLevel = developerPreferences.loggingLevel
        if (!loggingLevel.allows(messageLevel)) return
        if (loggingLevel != DiagnosticLoggingLevel.VERBOSE && !deduper.shouldWrite(key, value)) {
            pruneExpired(appContext)
            return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val nowMillis = System.currentTimeMillis()
        val currentEntries = DiagnosticLogStore.decode(prefs.getString(KEY_ENTRIES, null))
        val prunedEntries = DiagnosticLogStore.prune(
            entries = currentEntries,
            policy = developerPreferences.logClearPolicy,
            nowMillis = nowMillis
        )
        val nextEntries = DiagnosticLogStore.prepend(
            entries = prunedEntries,
            entry = DiagnosticLogEntry(
                timestampMillis = nowMillis,
                logType = key,
                message = value
            )
        )
        prefs.edit()
            .putString(KEY_ENTRIES, DiagnosticLogStore.encode(nextEntries))
            .apply()
    }
}
