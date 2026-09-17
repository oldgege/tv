package com.wengyj.tv.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 语音统一入口（全局单例）。策略：MP3 优先，TTS 兜底。
 *
 * <p>命名规则：
 * <ul>
 *   <li>题目：q_{chapterId}_{levelIndex}.mp3</li>
 *   <li>选项：q_{chapterId}_{levelIndex}_{optionIndex}.mp3</li>
 *   <li>UI：ui_{scene}.mp3</li>
 * </ul>
 *
 * <p><b>为什么是单例</b>：多个 Fragment 若各持一份 SpeechManager，
 * 内部的 AudioPlayer 就各有一份 MediaPlayer，无法互相打断，会造成
 * 多路声音叠加。全局单例保证"任何时刻只有一个 AudioPlayer 在播"。
 *
 * <p><b>打断策略</b>：任何新语音进来之前，先 {@link #stopAllPlayback()}
 * 停掉所有正在播放的 MP3 和 TTS。
 */
public class SpeechManager {

    private static final String TAG = "SpeechManager";
    private static final String OPTION_INDEX_ASSET = "audio/option_index.json";

    // ========== 单例 ==========

    private static volatile SpeechManager sInstance;

    public static SpeechManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (SpeechManager.class) {
                if (sInstance == null) {
                    sInstance = new SpeechManager(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    // ========== 选项索引（App 生命周期内只加载一次） ==========

    private static JSONObject sOptionIndex = null;
    private static boolean sOptionIndexLoaded = false;

    // ========== 实例字段 ==========

    private final Context appContext;
    private final AudioPlayer audioPlayer;
    private final TtsManager ttsManager;

    private SpeechManager(Context appContext) {
        this.appContext = appContext;
        this.audioPlayer = new AudioPlayer(appContext);
        this.ttsManager = TtsManager.getInstance(appContext);
    }

    // ========== 题目语音 ==========

    public void speakQuestion(int chapterId, int levelIndex, String text) {
        stopAllPlayback();   // ★ 打断上一个

        String path = "audio/q_" + chapterId + "_" + levelIndex + ".mp3";
        if (audioPlayer.playAsset(path)) {
            Log.d(TAG, "播放题目 MP3: " + path);
            return;
        }
        Log.w(TAG, "题目 MP3 不存在，回退 TTS: " + path + " | text=" + text);
        speakWithTts(text);
    }

    // ========== 选项语音（按文本查表） ==========

    public void speakOption(int chapterId, int levelIndex, String optionText) {
        if (optionText == null || optionText.isEmpty()) return;
        stopAllPlayback();   // ★ 打断上一个

        String filename = lookupOptionFilename(chapterId, levelIndex, optionText);
        if (filename != null) {
            String path = "audio/" + filename;
            if (audioPlayer.playAsset(path)) {
                Log.d(TAG, "播放选项 MP3: " + path + " (" + optionText + ")");
                return;
            }
        }

        Log.w(TAG, "选项 MP3 未找到，回退 TTS: " + optionText);
        speakWithTts(optionText);
    }

    @Nullable
    private String lookupOptionFilename(int chapterId, int levelIndex, String optionText) {
        JSONObject index = getOptionIndex();
        if (index == null) return null;

        String key = chapterId + "_" + levelIndex;
        JSONObject chapter = index.optJSONObject(key);
        if (chapter == null) {
            Log.d(TAG, "option_index 无此章节条目: " + key);
            return null;
        }

        String filename = chapter.optString(optionText, null);
        if (filename == null || filename.isEmpty()) {
            Log.d(TAG, "option_index 无此选项: " + key + " / " + optionText);
            return null;
        }
        return filename;
    }

    private synchronized JSONObject getOptionIndex() {
        if (sOptionIndexLoaded) return sOptionIndex;
        sOptionIndexLoaded = true;

        InputStream in = null;
        try {
            in = appContext.getAssets().open(OPTION_INDEX_ASSET);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            sOptionIndex = new JSONObject(bos.toString("UTF-8"));
            Log.i(TAG, "加载 option_index.json 成功");
        } catch (Exception e) {
            Log.w(TAG, "加载 option_index.json 失败: " + e.getMessage());
            sOptionIndex = new JSONObject();
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) {}
            }
        }
        return sOptionIndex;
    }

    // ========== UI 语音 ==========

    public void speakUi(String scene, String fallbackText) {
        stopAllPlayback();   // ★ 打断上一个

        String path = "audio/ui_" + scene + ".mp3";
        if (audioPlayer.playAsset(path)) {
            Log.d(TAG, "播放 UI MP3: " + path);
            return;
        }
        Log.w(TAG, "UI MP3 不存在，回退 TTS: " + path + " | text=" + fallbackText);
        speakWithTts(fallbackText);
    }

    // ========== TTS 兜底 ==========

    private void speakWithTts(String text) {
        if (text == null || text.isEmpty()) return;
        ttsManager.ensureInit();
        ttsManager.speak(text);
        if (!ttsManager.isReady()) {
            Log.w(TAG, "TTS 未就绪，已挂起播报: " + text);
        }
    }

    // ========== 停止与释放 ==========

    private void stopAllPlayback() {
        audioPlayer.stop();
        ttsManager.stop();
    }

    public void stop() {
        stopAllPlayback();
    }

    /**
     * 兼容旧调用。单例下这个方法等同于 {@link #stop()}，
     * 不会销毁 AudioPlayer 实例，避免影响其他 Fragment。
     */
    public void release() {
        stopAllPlayback();
    }
}