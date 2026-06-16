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
            "Listener" to prefs.getString("listener", "No listener events yet").orEmpty(),
            "Mirror" to prefs.getString("mirror", "No mirrored notification yet").orEmpty(),
            "Media" to prefs.getString("media", "No media events yet").orEmpty(),
            "Visibility" to prefs.getString("visibility", "No visibility changes yet").orEmpty(),
            "Suppression" to prefs.getString("suppression", "Not attempted").orEmpty(),
            "Battery" to BatteryDiagnostics.summary(),
            "Background" to prefs.getString("background", "Not initialized").orEmpty(),
            "Privileged access" to prefs.getString("privileged_setup", "Not attempted").orEmpty()
        )
    }
}
