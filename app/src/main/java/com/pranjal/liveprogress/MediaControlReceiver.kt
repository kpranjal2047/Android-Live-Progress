package com.pranjal.liveprogress

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

class MediaControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MediaTransportControls.handle(context.applicationContext, intent.action)
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.pranjal.liveprogress.media.PLAY_PAUSE"
        const val ACTION_NEXT = "com.pranjal.liveprogress.media.NEXT"
        const val ACTION_PREVIOUS = "com.pranjal.liveprogress.media.PREVIOUS"
    }
}

object MediaTransportControls {
    fun handle(context: Context, action: String?) {
        if (action == null) return
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(context, NotificationMirrorService::class.java)
        val controller = runCatching {
            manager.getActiveSessions(component)
                .firstOrNull { it.playbackState != null }
        }.getOrNull() ?: return

        val controls = controller.transportControls
        when (action) {
            MediaControlReceiver.ACTION_PLAY_PAUSE -> {
                if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    controls.pause()
                } else {
                    controls.play()
                }
            }

            MediaControlReceiver.ACTION_NEXT -> controls.skipToNext()
            MediaControlReceiver.ACTION_PREVIOUS -> controls.skipToPrevious()
        }
    }
}
