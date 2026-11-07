package com.joffile.myplayer;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

class Track {
    final long id;
    final String title;
    final String artist;

    Track(long id, String title, String artist) {
        this.id = id;
        this.title = (title == null || title.isEmpty()) ? "Без названия" : title;
        this.artist = artist;
    }

    Uri uri() {
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    }

    String label() {
        if (artist == null || artist.isEmpty() || MediaStore.UNKNOWN_STRING.equals(artist)) {
            return title;
        }
        return title + " - " + artist;
    }
}

public class MainActivity extends Activity {

    static final String BG = "#0D0D10";
    static final String PANEL = "#17171C";
    static final String ACCENT = "#E58FE6";
    static final String MUTED = "#8A8A94";
    static final int REQ = 42;

    final ArrayList<Track> tracks = new ArrayList<>();
    final ArrayList<String> labels = new ArrayList<>();
    final Handler handler = new Handler();

    ArrayAdapter<String> adapter;
    ListView listView;
    TextView countView, nowView, timeView, shuffleView;
    ImageButton playBtn;
    SeekBar seek;
    Button permBtn;
    boolean dragging = false;
    int shownIndex = -2;

    final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateUi();
            handler.postDelayed(this, 400);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.parseColor(BG));
        getWindow().setNavigationBarColor(Color.parseColor(BG));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor(BG));
        root.setPadding(dp(20), dp(28), dp(20), dp(20));
        setContentView(root);

        // Header
        LinearLayout headRow = new LinearLayout(this);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headCol = new LinearLayout(this);
        headCol.setOrientation(LinearLayout.VERTICAL);
        headCol.addView(text("Мой плеер", 24, "#FFFFFF", true));
        countView = text("Загрузка...", 13, MUTED, false);
        headCol.addView(countView);
        headRow.addView(headCol, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView refresh = text("Обновить", 14, ACCENT, true);
        refresh.setPadding(dp(8), dp(8), dp(8), dp(8));
        refresh.setOnClickListener(v -> loadTracks());
        headRow.addView(refresh);
        root.addView(headRow, new LinearLayout.LayoutParams(-1, -2));

        // Permission button (hidden once access is granted)
        permBtn = new Button(this);
        permBtn.setText("Разрешить доступ к музыке");
        permBtn.setAllCaps(false);
        permBtn.setOnClickListener(v -> askPermission());
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-1, -2);
        pLp.topMargin = dp(12);
        root.addView(permBtn, pLp);

        // Track list
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
        listView = new ListView(this);
        listView.setAdapter(adapter);
        listView.setDividerHeight(0);
        listView.setBackgroundColor(Color.parseColor(BG));
        listView.setOnItemClickListener((parent, view, pos, id) -> startPlay(pos));
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lLp.topMargin = dp(12);
        root.addView(listView, lLp);

        // Player panel
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(rounded(PANEL, 24));
        panel.setPadding(dp(18), dp(16), dp(18), dp(14));

        nowView = text("Выберите трек", 16, "#FFFFFF", true);
        nowView.setSingleLine(true);
        nowView.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(nowView);

        seek = new SeekBar(this);
        seek.setMax(1000);
        seek.getProgressDrawable().setColorFilter(
                new PorterDuffColorFilter(Color.parseColor(ACCENT), PorterDuff.Mode.SRC_IN));
        seek.getThumb().setColorFilter(
                new PorterDuffColorFilter(Color.parseColor(ACCENT), PorterDuff.Mode.SRC_IN));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
                MediaPlayer mp = PlayerService.player;
                if (mp != null) {
                    try {
                        int dur = mp.getDuration();
                        if (dur > 0) {
                            sendSeek((int) (dur * (long) s.getProgress() / 1000L));
                        }
                    } catch (Exception e) {
                        // player not ready
                    }
                }
            }
        });
        panel.addView(seek);

        LinearLayout ctrl = new LinearLayout(this);
        ctrl.setOrientation(LinearLayout.HORIZONTAL);
        ctrl.setGravity(Gravity.CENTER);
        ImageButton prevBtn = iconBtn(android.R.drawable.ic_media_previous);
        playBtn = iconBtn(android.R.drawable.ic_media_play);
        ImageButton nextBtn = iconBtn(android.R.drawable.ic_media_next);
        playBtn.setBackground(oval(ACCENT));
        playBtn.setColorFilter(Color.parseColor("#1A0F1A"));
        playBtn.setPadding(dp(24), dp(24), dp(24), dp(24));
        prevBtn.setOnClickListener(v -> send(PlayerService.ACT_PREV));
        playBtn.setOnClickListener(v -> send(PlayerService.ACT_TOGGLE));
        nextBtn.setOnClickListener(v -> send(PlayerService.ACT_NEXT));
        ctrl.addView(prevBtn, new LinearLayout.LayoutParams(dp(64), dp(64)));
        ctrl.addView(playBtn, new LinearLayout.LayoutParams(dp(84), dp(84)));
        ctrl.addView(nextBtn, new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-1, -2);
        cLp.topMargin = dp(10);
        panel.addView(ctrl, cLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        timeView = text("0:00 / 0:00", 12, MUTED, false);
        shuffleView = text("Перемешать: выкл", 13, MUTED, false);
        shuffleView.setPadding(dp(8), dp(8), dp(8), dp(8));
        shuffleView.setOnClickListener(v -> {
            PlayerService.shuffle = !PlayerService.shuffle;
            updateUi();
        });
        row.addView(timeView, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(shuffleView);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, -2);
        rLp.topMargin = dp(6);
        panel.addView(row, rLp);

        LinearLayout.LayoutParams plLp = new LinearLayout.LayoutParams(-1, -2);
        plLp.topMargin = dp(12);
        root.addView(panel, plLp);

        if (!hasPerm()) {
            askPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean ok = hasPerm();
        permBtn.setVisibility(ok ? View.GONE : View.VISIBLE);
        if (ok && tracks.isEmpty()) {
            loadTracks();
        }
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ) {
            if (hasPerm()) {
                loadTracks();
            } else {
                Toast.makeText(this, "Без доступа к музыке плеер не найдёт песни", Toast.LENGTH_LONG).show();
            }
            permBtn.setVisibility(hasPerm() ? View.GONE : View.VISIBLE);
        }
    }

    String musicPerm() {
        return Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    boolean hasPerm() {
        return checkSelfPermission(musicPerm()) == PackageManager.PERMISSION_GRANTED;
    }

    void askPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{musicPerm(), Manifest.permission.POST_NOTIFICATIONS}, REQ);
        } else {
            requestPermissions(new String[]{musicPerm()}, REQ);
        }
    }

    void loadTracks() {
        new Thread(() -> {
            final ArrayList<Track> found = new ArrayList<>();
            String[] cols = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST
            };
            try (Cursor c = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    cols,
                    MediaStore.Audio.Media.IS_MUSIC + " != 0",
                    null,
                    MediaStore.Audio.Media.TITLE + " COLLATE NOCASE")) {
                if (c != null) {
                    int iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                    int iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                    int iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                    while (c.moveToNext()) {
                        found.add(new Track(c.getLong(iId), c.getString(iTitle), c.getString(iArtist)));
                    }
                }
            } catch (Exception e) {
                // no access or no music
            }
            runOnUiThread(() -> {
                tracks.clear();
                tracks.addAll(found);
                refreshLabels();
                countView.setText(tracks.size() + " песен");
            });
        }).start();
    }

    void refreshLabels() {
        labels.clear();
        for (int i = 0; i < tracks.size(); i++) {
            String prefix = (i == PlayerService.index) ? "♪  " : "";
            labels.add(prefix + tracks.get(i).label());
        }
        shownIndex = PlayerService.index;
        adapter.notifyDataSetChanged();
    }

    void startPlay(int pos) {
        if (pos < 0 || pos >= tracks.size()) return;
        PlayerService.queue = new ArrayList<>(tracks);
        Intent i = new Intent(this, PlayerService.class)
                .setAction(PlayerService.ACT_PLAY)
                .putExtra("index", pos);
        startService(i);
    }

    void send(String action) {
        startService(new Intent(this, PlayerService.class).setAction(action));
    }

    void sendSeek(int ms) {
        Intent i = new Intent(this, PlayerService.class)
                .setAction(PlayerService.ACT_SEEK)
                .putExtra("pos", ms);
        startService(i);
    }

    void updateUi() {
        try {
            MediaPlayer mp = PlayerService.player;
            int idx = PlayerService.index;
            boolean playing = mp != null && mp.isPlaying();

            if (idx >= 0 && idx < PlayerService.queue.size()) {
                nowView.setText(PlayerService.queue.get(idx).label());
            } else {
                nowView.setText("Выберите трек");
            }
            playBtn.setImageResource(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);

            if (idx != shownIndex && !tracks.isEmpty()) {
                refreshLabels();
            }

            if (mp != null && !dragging) {
                int dur = mp.getDuration();
                int pos = mp.getCurrentPosition();
                if (dur > 0) {
                    seek.setProgress((int) (pos * 1000L / dur));
                }
                timeView.setText(fmt(pos) + " / " + fmt(dur));
            }

            shuffleView.setText(PlayerService.shuffle ? "Перемешать: вкл" : "Перемешать: выкл");
            shuffleView.setTextColor(Color.parseColor(PlayerService.shuffle ? ACCENT : MUTED));
        } catch (Exception e) {
            // player not ready yet
        }
    }

    String fmt(int ms) {
        if (ms < 0) ms = 0;
        int s = ms / 1000;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    TextView text(String s, int sp, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    ImageButton iconBtn(int res) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setColorFilter(Color.WHITE);
        b.setPadding(dp(14), dp(14), dp(14), dp(14));
        return b;
    }

    GradientDrawable oval(String color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.parseColor(color));
        return g;
    }

    GradientDrawable rounded(String color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(color));
        g.setCornerRadius(dp(radiusDp));
        return g;
    }
}
