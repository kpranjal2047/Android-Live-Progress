package com.pranjal.liveprogress

import android.content.Context
import android.content.res.Configuration

object SystemColorPalette {
    fun primary(context: Context): Int {
        return context.getColor(
            if (isNightMode(context)) {
                android.R.color.system_accent1_200
            } else {
                android.R.color.system_accent1_600
            }
        )
    }

    private fun isNightMode(context: Context): Boolean {
        val nightMode = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }
}
