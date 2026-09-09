package com.wengyj.tv.utils;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.wengyj.tv.R;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class VideoCacheManager {
    private static VideoCacheManager instance;
    private final Map<String, MediaPlayer> cache = new HashMap<>();
    private final Context appContext;
    private boolean isLoading = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private VideoCacheManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized VideoCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new VideoCacheManager(context);
        }
        return instance;
    }

    public void preloadAll(Runnable onComplete) {
        if (isLoading) return;
        isLoading = true;
        new Thread(() -> {
            preloadVideo(R.raw.begin2);
            preloadVideo(R.raw.begin);
            preloadVideo(R.raw.success);
            preloadVideo(R.raw.error);
            isLoading = false;
            if (onComplete != null) {
                mainHandler.post(onComplete);
            }
        }).start();
    }

    private void preloadVideo(int rawResId) {
        String key = getKey(rawResId);
        if (cache.containsKey(key)) return;
        try {
            MediaPlayer mp = new MediaPlayer();
            Uri uri = Uri.parse("android.resource://" + appContext.getPackageName() + "/" + rawResId);
            mp.setDataSource(appContext, uri);
            mp.prepare();
            mp.setVolume(0, 0);
            cache.put(key, mp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getKey(int rawResId) {
        return "video_" + rawResId;
    }

    public MediaPlayer getMediaPlayer(int rawResId) {
        String key = getKey(rawResId);
        MediaPlayer mp = cache.remove(key);
        if (mp != null) {
            mp.reset();
            try {
                Uri uri = Uri.parse("android.resource://" + appContext.getPackageName() + "/" + rawResId);
                mp.setDataSource(appContext, uri);
                mp.prepare();
                mp.setVolume(1, 1);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            return mp;
        } else {
            return createMediaPlayer(rawResId);
        }
    }

    private MediaPlayer createMediaPlayer(int rawResId) {
        try {
            MediaPlayer mp = new MediaPlayer();
            Uri uri = Uri.parse("android.resource://" + appContext.getPackageName() + "/" + rawResId);
            mp.setDataSource(appContext, uri);
            mp.prepare();
            return mp;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void releaseAll() {
        for (MediaPlayer mp : cache.values()) {
            mp.release();
        }
        cache.clear();
    }
}