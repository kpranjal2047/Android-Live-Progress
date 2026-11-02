package com.pranjal.liveprogress;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;

/** Test APK only. Java keeps standalone launches independent of the target app's Kotlin runtime. */
public class NotificationFixtureActivity extends Activity {
    private static MediaSession session;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        NotificationManager manager = getSystemService(NotificationManager.class);
        String channel = getIntent().getStringExtra("channel");
        if (channel == null) channel = "fixture";
        manager.createNotificationChannel(new NotificationChannel(channel, "Test notifications", NotificationManager.IMPORTANCE_LOW));
        String mode = getIntent().getStringExtra("mode");
        if ("cancel".equals(mode)) {
            manager.cancelAll();
            if (session != null) session.release();
            session = null; finish(); return;
        }
        boolean media = !"progress".equals(mode);
        boolean known = getIntent().getBooleanExtra("known", true);
        boolean alt = getIntent().getBooleanExtra("alt", false);
        int icon = alt ? android.R.drawable.ic_menu_camera : android.R.drawable.ic_media_play;
        Notification.Builder builder = new Notification.Builder(this, channel).setSmallIcon(icon)
            .setContentTitle(media ? "Test media" : "Test download").setContentText("Synthetic notification for validation")
            .setOngoing(true).setOnlyAlertOnce(true);
        if (media) {
            if (session == null) { session = new MediaSession(this, "Live test fixture"); session.setActive(true); }
            Bitmap art = Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888); art.eraseColor(Color.BLUE);
            session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Test media")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Test artist").putLong(MediaMetadata.METADATA_KEY_DURATION, known ? 120_000 : 0)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art).build());
            session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 10_000, 0f)
                .setActions(PlaybackState.ACTION_PLAY_PAUSE).build());
            builder.setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()));
        } else builder.setProgress(known ? 100 : 0, known ? 40 : 0, !known);
        if (getIntent().getBooleanExtra("actions", true)) {
            PendingIntent pending = PendingIntent.getActivity(this, 1, new Intent(this, NotificationFixtureActivity.class).putExtra("mode", "cancel"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(Icon.createWithResource(this, icon), alt ? "Updated action" : "Stop test", pending).build());
        }
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify(media ? 901 : 902, builder.build());
        }
        finish();
    }
}
