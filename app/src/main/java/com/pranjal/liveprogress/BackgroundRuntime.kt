package com.pranjal.liveprogress

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService

object BackgroundRuntime {
    private const val STALE_RESTORE_DELAY_MS = 1_000L

    fun initialize(context: Context, reason: String) {
        val appContext = context.applicationContext
        MirrorNotificationBuilder.ensureChannel(appContext)
        MediaLiveNotificationBuilder.ensureChannel(appContext)
        VisibilityState.register(appContext)

        val listenerRebindRequested = runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(appContext, NotificationMirrorService::class.java)
            )
        }.isSuccess
        val assistantRebindRequested = runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(appContext, NotificationAssistantBridgeService::class.java)
            )
        }.isSuccess

        val rebindStatus =
            "listener rebind=$listenerRebindRequested, assistant rebind=$assistantRebindRequested"
        AppDiagnostics.note(appContext, "background", "$reason; $rebindStatus")

        Handler(Looper.getMainLooper()).postDelayed(
            {
                NotificationMirrorService.requestRefresh(
                    appContext,
                    "background startup after $reason"
                )
            },
            250L
        )

        Handler(Looper.getMainLooper()).postDelayed(
            {
                OriginalSuppressionController.restoreAll(
                    appContext,
                    "background cleanup after $reason"
                )
                PrivilegedAccess.cleanupUnexpectedElevatedStateAsync(
                    appContext,
                    "background cleanup after $reason"
                )
            },
            STALE_RESTORE_DELAY_MS
        )
    }
}
