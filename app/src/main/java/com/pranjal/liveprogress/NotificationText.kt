package com.pranjal.liveprogress

import android.content.Context

/** Resolve at render time so countdown ticks use the current per-app language. */
class NotificationText(private val resolve: (Int, Array<out Any>) -> String) {
    fun get(id: Int, vararg arguments: Any): String = resolve(id, arguments)

    companion object {
        fun from(context: Context): NotificationText = NotificationText { id, arguments ->
            context.getString(id, *arguments)
        }
    }
}
