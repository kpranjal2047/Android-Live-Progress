package com.pranjal.liveprogress

import android.content.Context
import java.text.DateFormat
import java.util.Date

object AppDiagnostics {
    private const val PREFS = "android_live_progress_diagnostics"
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
        if (!deduper.shouldWrite(key, value)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key, "${DateFormat.getTimeInstance().format(Date())}: $value")
            .apply()
    }

    fun snapshot(context: Context): List<Pair<String, String>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return listOf(
            context.getString(R.string.diagnostic_category_listener) to
                prefs.getString("listener", context.getString(R.string.diagnostic_default_listener)).orEmpty(),
            context.getString(R.string.diagnostic_category_mirror) to
                prefs.getString("mirror", context.getString(R.string.diagnostic_default_mirror)).orEmpty(),
            context.getString(R.string.diagnostic_category_media) to
                prefs.getString("media", context.getString(R.string.diagnostic_default_media)).orEmpty(),
            context.getString(R.string.diagnostic_category_visibility) to
                prefs.getString("visibility", context.getString(R.string.diagnostic_default_visibility)).orEmpty(),
            context.getString(R.string.diagnostic_category_suppression) to
                prefs.getString("suppression", context.getString(R.string.diagnostic_default_suppression)).orEmpty(),
            context.getString(R.string.diagnostic_category_battery) to BatteryDiagnostics.summary(context),
            context.getString(R.string.diagnostic_category_background) to
                prefs.getString("background", context.getString(R.string.diagnostic_default_background)).orEmpty(),
            context.getString(R.string.diagnostic_category_privileged_access) to
                prefs.getString(
                    "privileged_setup",
                    context.getString(R.string.diagnostic_default_privileged_access)
                ).orEmpty()
        )
    }
}
