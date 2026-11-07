package com.joffile.myplayer;

import android.app.Application;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public class MyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                saveReport(sw.toString());
            } catch (Throwable ignored) {
            }
            if (old != null) old.uncaughtException(thread, ex);
        });
    }

    void saveReport(String text) throws Exception {
        if (Build.VERSION.SDK_INT < 29) return;
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, "crash-report.txt");
        v.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) return;
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os != null) os.write(text.getBytes("UTF-8"));
        }
    }
}
