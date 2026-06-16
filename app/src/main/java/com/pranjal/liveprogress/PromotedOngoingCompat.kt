package com.pranjal.liveprogress

import android.app.Notification
import android.os.Bundle

object PromotedOngoingCompat {
    const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    fun request(builder: Notification.Builder): Notification.Builder {
        val invoked = try {
            builder.javaClass
                .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
            true
        } catch (_: ReflectiveOperationException) {
            false
        }

        if (!invoked) {
            builder.addExtras(Bundle().apply {
                putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
            })
        }
        return builder
    }

    fun isRequested(notification: Notification): Boolean {
        return notification.extras?.getBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, false) == true
    }
}
