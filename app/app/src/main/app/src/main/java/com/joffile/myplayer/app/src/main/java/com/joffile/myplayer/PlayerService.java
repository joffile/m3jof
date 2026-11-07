package com.joffile.myplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Random;

public class PlayerService extends Service {

    static final String CHANNEL = "player_channel";
    static final int NOTE_ID = 7;
    static final String ACT_PLAY = "com.joffile.myplayer.PLAY";
    static final String ACT_TOGGLE = "com.joffile.myplayer.TOGGLE";
    static final String ACT_NEXT = "com.joffile.myplayer.NEXT";
    static final String ACT_PREV = "com.joffile.myplayer.PREV";
    static final String ACT_SEEK = "com.joffile.myplayer.SEEK";

    static MediaPlayer player;
    static ArrayList<Track> queue = new ArrayList<>();
    static int index = -1;
    static boolean shuffle = false;
    static final Random rnd = new Random();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACT_PLAY.equals(action)) {
            playAt(intent.getIntExtra("index", 0));
        } else if (ACT_TOGGLE.equals(action)) {
            toggle();
        } else if (ACT_NEXT.equals(action)) {
            next();
        } else if (ACT_PREV.equals(action)) {
            prev();
        } else if (ACT_SEEK.equals(action)) {
            if (player != null) {
                player.seekTo(intent.getIntExtra("pos", 0));
            }
        }
        return START_NOT_STICKY;
    }

    void playAt(int i) {
        if (i < 0 || i >= queue.size()) return;
        index = i;
        try {
            if (player == null) {
                player = new MediaPlayer();
                player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
                player.setOnCompletionListener(mp -> next());
            } else {
                player.reset();
            }
            player.setDataSource(this, queue.get(i).uri());
            player.prepare();
            player.start();
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть файл", Toast.LENGTH_SHORT).show();
            return;
        }
        showNotification();
    }

    void toggle() {
        try {
            if (player == null || index < 0) {
                playAt(0);
                return;
            }
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.start();
            }
        } catch (Exception e) {
            return;
        }
        showNotification();
    }

    void next() {
        if (queue.isEmpty()) return;
        int n;
        if (shuffle && queue.size() > 1) {
            do {
                n = rnd.nextInt(queue.size());
            } while (n == index);
        } else {
            n = index + 1;
            if (n >= queue.size()) n = 0;
        }
        playAt(n);
    }

    void prev() {
        if (queue.isEmpty()) return;
        if (player != null && player.getCurrentPosition() > 3000) {
            player.seekTo(0);
            return;
        }
        int n = index - 1;
        if (n < 0) n = queue.size() - 1;
        playAt(n);
    }

    void showNotification() {
        if (index < 0 || index >= queue.size()) return;
        Track t = queue.get(index);
        boolean playing = player != null && player.isPlaying();

        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Плеер", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(channel);

        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);

        Notification note = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(t.title)
                .setContentText(t.artist == null ? "" : t.artist)
                .setContentIntent(openPi)
                .setOngoing(playing)
                .addAction(android.R.drawable.ic_media_previous, "Назад", action(ACT_PREV))
                .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Пауза" : "Играть", action(ACT_TOGGLE))
                .addAction(android.R.drawable.ic_media_next, "Далее", action(ACT_NEXT))
                .setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2))
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTE_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTE_ID, note);
        }
    }

    PendingIntent action(String act) {
        Intent i = new Intent(this, PlayerService.class).setAction(act);
        return PendingIntent.getService(this, act.hashCode(), i, PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
