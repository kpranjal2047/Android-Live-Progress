package com.pranjal.liveprogress

import android.app.NotificationChannel
import android.content.Intent
import android.os.IBinder
import android.os.UserHandle
import android.service.notification.NotificationListenerService

class NotificationAssistantBridgeService : NotificationListenerService() {
    override fun onBind(intent: Intent): IBinder? {
        AppDiagnostics.note(
            this,
            "suppression",
            "Notification assistant bridge bind requested; action=${intent.action.orEmpty()}"
        )
        return super.onBind(intent)
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        AppDiagnostics.note(this, "suppression", "Notification assistant bridge created")
    }

    override fun onListenerConnected() {
        activeService = this
        AppDiagnostics.note(this, "suppression", "Notification assistant bridge connected")
    }

    override fun onListenerDisconnected() {
        if (activeService === this) activeService = null
        AppDiagnostics.note(this, "suppression", "Notification assistant bridge disconnected")
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var activeService: NotificationAssistantBridgeService? = null

        fun isConnected(): Boolean = activeService != null

        fun getSourceChannel(
            packageName: String,
            user: UserHandle,
            channelId: String
        ): Result<NotificationChannel?> {
            val service = activeService
                ?: return Result.failure(
                    IllegalStateException("Notification assistant bridge is not connected")
                )
            return runCatching {
                service.getNotificationChannels(packageName, user)
                    .firstOrNull { it.id == channelId }
            }
        }

        fun updateSourceChannel(
            packageName: String,
            user: UserHandle,
            channel: NotificationChannel
        ): Result<Unit> {
            val service = activeService
                ?: return Result.failure(
                    IllegalStateException("Notification assistant bridge is not connected")
                )
            return runCatching {
                service.updateNotificationChannel(packageName, user, channel)
            }
        }
    }
}
