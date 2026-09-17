package com.wengyj.tv.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * 题目/菜单语音播放器。
 * 优先播放 assets/audio/ 下预录的 MP3；不存在时由 SpeechManager 回退到 TTS。
 */
public class AudioPlayer {

    private static final String TAG = "AudioPlayer";

    private final Context appContext;
    private final AssetManager assetManager;
    private MediaPlayer player;

    public AudioPlayer(Context context) {
        this.appContext = context.getApplicationContext();
        this.assetManager = appContext.getAssets();
    }

    /**
     * 判断 assets 中是否存在指定音频文件。
     */
    public boolean exists(String assetPath) {
        InputStream in = null;
        try {
            in = assetManager.open(assetPath);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
    }

    public boolean playQuestion(int chapterId, int levelIndex) {
        String path = "audio/q_" + chapterId + "_" + levelIndex + ".mp3";
        return playAsset(path);
    }

    public boolean playUi(String scene) {
        String path = "audio/ui_" + scene + ".mp3";
        return playAsset(path);
    }

    public boolean playAsset(String assetPath) {
        stop();
        AssetFileDescriptor afd = null;
        try {
            afd = assetManager.openFd(assetPath);
            player = new MediaPlayer();

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                try { player.setAudioStreamType(AudioManager.STREAM_MUSIC); } catch (Exception ignored) {}
            }

            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            afd = null;

            player.setOnCompletionListener(mp -> {
                mp.release();
                if (player == mp) player = null;
            });
            player.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "播放出错 what=" + what + " extra=" + extra);
                mp.release();
                if (player == mp) player = null;
                return true;
            });

            player.prepare();
            player.start();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "播放失败: " + assetPath + " → " + e.getMessage());
            if (afd != null) try { afd.close(); } catch (Exception ignored) {}
            stop();
            return false;
        }
    }

    public void stop() {
        if (player != null) {
            try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    public void release() { stop(); }
}