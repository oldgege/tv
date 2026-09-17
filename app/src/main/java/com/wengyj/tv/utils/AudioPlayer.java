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
 *
 * <p>优化点：
 * <ul>
 *   <li>使用 {@code prepareAsync()} 替代 {@code prepare()}，避免主线程阻塞</li>
 *   <li>复用单个 MediaPlayer 实例，避免频繁 new/release 带来的 100~300ms 延迟</li>
 *   <li>监听器只绑定一次，避免累积</li>
 * </ul>
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

    /**
     * 播放 assets 下的音频（异步）。
     *
     * @return true 表示已成功发起播放；false 表示文件不存在或准备失败
     */
    public boolean playAsset(String assetPath) {
        AssetFileDescriptor afd = null;
        try {
            afd = assetManager.openFd(assetPath);
        } catch (Exception e) {
            Log.w(TAG, "音频不存在: " + assetPath);
            return false;
        }

        ensurePlayer();

        try {
            // reset 回到 Idle 状态，才能重新 setDataSource
            player.reset();
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            afd = null;

            // 每次 prepare 前重新绑定 prepared 回调
            // （reset() 不保证清除监听器，但重新设置总是安全的）
            player.setOnPreparedListener(mp -> {
                try {
                    mp.start();
                } catch (Exception e) {
                    Log.w(TAG, "start 失败: " + e.getMessage());
                }
            });

            // ★ 非阻塞：立刻返回，播放准备在后台完成
            player.prepareAsync();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "播放失败: " + assetPath + " → " + e.getMessage());
            if (afd != null) try { afd.close(); } catch (Exception ignored) {}
            try { player.reset(); } catch (Exception ignored) {}
            return false;
        }
    }

    private void ensurePlayer() {
        if (player != null) return;
        player = new MediaPlayer();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            } catch (Exception ignored) {}
        }

        player.setOnCompletionListener(mp -> {
            // 播放完成：保持在 PlaybackCompleted 状态，
            // 下次 playAsset 时 reset() 复用，不做任何操作
        });

        player.setOnErrorListener((mp, what, extra) -> {
            Log.w(TAG, "播放出错 what=" + what + " extra=" + extra);
            try { mp.reset(); } catch (Exception ignored) {}
            return true;
        });
    }

    /**
     * 停止当前播放，但保留 MediaPlayer 实例供复用。
     */
    public void stop() {
        if (player == null) return;
        try {
            if (player.isPlaying()) player.stop();
        } catch (Exception ignored) {}
        try {
            player.reset();
        } catch (Exception ignored) {}
    }

    /**
     * 彻底释放。只有 Activity 真正结束时才调用。
     */
    public void release() {
        if (player == null) return;
        try { player.stop(); } catch (Exception ignored) {}
        try { player.release(); } catch (Exception ignored) {}
        player = null;
    }
}