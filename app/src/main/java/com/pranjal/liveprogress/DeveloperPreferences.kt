package com.pranjal.liveprogress

import android.content.Context

enum class DiagnosticLoggingLevel(val labelRes: Int) {
    OFF(R.string.logging_level_off),
    NORMAL(R.string.logging_level_normal),
    VERBOSE(R.string.logging_level_verbose);

    fun allows(messageLevel: DiagnosticLoggingLevel): Boolean {
        return when (this) {
            OFF -> false
            NORMAL -> messageLevel == NORMAL
            VERBOSE -> messageLevel == NORMAL || messageLevel == VERBOSE
        }
    }
}

enum class LogClearPolicy(
    val labelRes: Int,
    val retentionMillis: Long?
) {
    ON_APP_EXIT(R.string.log_clear_on_app_exit, null),
    AFTER_1_HOUR(R.string.log_clear_after_1_hour, 60L * 60L * 1000L),
    AFTER_6_HOURS(R.string.log_clear_after_6_hours, 6L * 60L * 60L * 1000L),
    AFTER_1_DAY(R.string.log_clear_after_1_day, 24L * 60L * 60L * 1000L),
    AFTER_7_DAYS(R.string.log_clear_after_7_days, 7L * 24L * 60L * 60L * 1000L);
}

class DeveloperPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var loggingLevel: DiagnosticLoggingLevel
        get() = prefs.getString(KEY_LOGGING_LEVEL, null)
            ?.let { value -> DiagnosticLoggingLevel.entries.firstOrNull { it.name == value } }
            ?: DEFAULT_LOGGING_LEVEL
        set(value) = prefs.edit().putString(KEY_LOGGING_LEVEL, value.name).apply()

    var logClearPolicy: LogClearPolicy
        get() = prefs.getString(KEY_LOG_CLEAR_POLICY, null)
            ?.let { value -> LogClearPolicy.entries.firstOrNull { it.name == value } }
            ?: DEFAULT_LOG_CLEAR_POLICY
        set(value) = prefs.edit().putString(KEY_LOG_CLEAR_POLICY, value.name).apply()

    companion object {
        val DEFAULT_LOGGING_LEVEL = DiagnosticLoggingLevel.OFF
        val DEFAULT_LOG_CLEAR_POLICY = LogClearPolicy.AFTER_1_HOUR

        private const val PREFS = "live_progress_developer_preferences"
        private const val KEY_LOGGING_LEVEL = "logging_level"
        private const val KEY_LOG_CLEAR_POLICY = "log_clear_policy"
    }
}
