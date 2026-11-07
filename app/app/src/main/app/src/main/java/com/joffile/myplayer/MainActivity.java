package com.joffile.myplayer;

import android.app.Activity;
import android.content.ContentUris;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

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

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#0D0D10"));

        TextView t = new TextView(this);
        t.setText("Тест: приложение открылось");
        t.setTextColor(Color.WHITE);
        t.setTextSize(20);
        root.addView(t);

        setContentView(root);
    }
}
