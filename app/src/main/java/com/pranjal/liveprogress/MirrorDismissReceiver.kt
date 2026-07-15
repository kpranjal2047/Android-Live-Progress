package com.pranjal.liveprogress

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

class MirrorDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_DISMISS_PROGRESS -> {
                val key = intent.getStringExtra(EXTRA_PROGRESS_KEY) ?: return
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                NotificationMirrorService.dismissProgressMirror(appContext, key, notificationId)
            }

            ACTION_DISMISS_MEDIA -> NotificationMirrorService.dismissMediaMirror(appContext)

            ACTION_DISMISS_NOTIFICATION -> {
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                appContext.getSystemService(NotificationManager::class.java).cancel(notificationId)
                AppDiagnostics.note(appContext, "mirror", "Live test notification dismissed by user")
            }
        }
    }

    companion object {
        private const val ACTION_DISMISS_PROGRESS =
            "com.pranjal.liveprogress.mirror.DISMISS_PROGRESS"
        private const val ACTION_DISMISS_MEDIA =
            "com.pranjal.liveprogress.mirror.DISMISS_MEDIA"
        private const val ACTION_DISMISS_NOTIFICATION =
            "com.pranjal.liveprogress.mirror.DISMISS_NOTIFICATION"
        private const val EXTRA_PROGRESS_KEY = "progress_key"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun progressAction(
            context: Context,
            candidate: MirrorCandidate
        ): Notification.Action {
            val intent = Intent(context, MirrorDismissReceiver::class.java)
                .setAction(ACTION_DISMISS_PROGRESS)
                .putExtra(EXTRA_PROGRESS_KEY, candidate.key)
                .putExtra(EXTRA_NOTIFICATION_ID, candidate.notificationId)
            return action(context, progressRequestCode(candidate.notificationId), intent)
        }

        fun progressDeleteIntent(
            context: Context,
            candidate: MirrorCandidate
        ): PendingIntent {
            val intent = Intent(context, MirrorDismissReceiver::class.java)
                .setAction(ACTION_DISMISS_PROGRESS)
                .putExtra(EXTRA_PROGRESS_KEY, candidate.key)
                .putExtra(EXTRA_NOTIFICATION_ID, candidate.notificationId)
            return broadcast(context, progressRequestCode(candidate.notificationId), intent)
        }

        fun mediaAction(context: Context): Notification.Action {
            val intent = Intent(context, MirrorDismissReceiver::class.java)
                .setAction(ACTION_DISMISS_MEDIA)
            return action(context, MEDIA_REQUEST_CODE, intent)
        }

        fun mediaDeleteIntent(context: Context): PendingIntent {
            val intent = Intent(context, MirrorDismissReceiver::class.java)
                .setAction(ACTION_DISMISS_MEDIA)
            return broadcast(context, MEDIA_REQUEST_CODE, intent)
        }

        fun notificationAction(
            context: Context,
            notificationId: Int
        ): Notification.Action {
            val intent = Intent(context, MirrorDismissReceiver::class.java)
                .setAction(ACTION_DISMISS_NOTIFICATION)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            return action(context, notificationId, intent)
        }

        fun notificationDeleteIntent(
            context: Context,
            notificationId: Int
        ): PendingIntent {
            val intent = Intent(context, MirrorDismissReceiver::class.java)
                .setAction(ACTION_DISMISS_NOTIFICATION)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            return broadcast(context, notificationId, intent)
        }

        private fun action(
            context: Context,
            requestCode: Int,
            intent: Intent
        ): Notification.Action {
            return Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_dismiss),
                context.getString(R.string.mirror_action_dismiss),
                broadcast(context, requestCode, intent)
            ).build()
        }

        private fun broadcast(
            context: Context,
            requestCode: Int,
            intent: Intent
        ): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun progressRequestCode(notificationId: Int): Int {
            return notificationId xor PROGRESS_REQUEST_OFFSET
        }

        private const val MEDIA_REQUEST_CODE = 200_100
        private const val PROGRESS_REQUEST_OFFSET = 0x4D50
    }
}
