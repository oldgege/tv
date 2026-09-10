package com.wengyj.tv.utils;

import android.content.Context;
import android.media.MediaPlayer;

import com.wengyj.tv.R;

public class MusicManager {
    private static MusicManager instance;
    private MediaPlayer mediaPlayer;
    private final Context appContext;
    private boolean enabled = true;

    // 正常音量 & 视频播放时的音量
    private static final float NORMAL_VOLUME = 0.4f;
    private static final float LOWERED_VOLUME = 0.1f;

    private MusicManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized MusicManager getInstance(Context context) {
        if (instance == null) {
            instance = new MusicManager(context);
        }
        return instance;
    }

    public void start() {
        if (mediaPlayer == null) {
            try {
                mediaPlayer = MediaPlayer.create(appContext, R.raw.bgm);
                if (mediaPlayer != null) {
                    mediaPlayer.setLooping(true);
                }
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        if (mediaPlayer != null && enabled && !mediaPlayer.isPlaying()) {
            mediaPlayer.setVolume(NORMAL_VOLUME, NORMAL_VOLUME);
            mediaPlayer.start();
        }
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            start();
        } else {
            pause();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 降低音量（用于视频播放期间） */
    public void lowerVolume() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.setVolume(LOWERED_VOLUME, LOWERED_VOLUME);
        }
    }

    /** 恢复音量 */
    public void restoreVolume() {
        if (mediaPlayer != null && mediaPlayer.isPlaying() && enabled) {
            mediaPlayer.setVolume(NORMAL_VOLUME, NORMAL_VOLUME);
        }
    }

    public void release() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}