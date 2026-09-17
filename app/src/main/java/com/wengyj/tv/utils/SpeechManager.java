package com.wengyj.tv.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 语音统一入口。策略：MP3 优先，TTS 兜底。
 *
 * <p>命名规则：
 * <ul>
 *   <li>题目：q_{chapterId}_{levelIndex}.mp3</li>
 *   <li>选项：q_{chapterId}_{levelIndex}_{optionIndex}.mp3</li>
 *   <li>UI：ui_{scene}.mp3</li>
 * </ul>
 *
 * <p><b>选项语音为什么按文本查表</b>：Java 端 {@code ChapterDataSource}
 * 会 {@code Collections.shuffle} 打乱选项顺序，而 MP3 文件名的
 * optionIndex 是"源文件里的第几列"，两者语义不一致。改为通过
 * {@code assets/audio/option_index.json} 按文本查找文件名，彻底规避错位。
 */
public class SpeechManager {

    private static final String TAG = "SpeechManager";
    private static final String OPTION_INDEX_ASSET = "audio/option_index.json";

    /** 选项索引（App 生命周期内只加载一次） */
    private static JSONObject sOptionIndex = null;
    private static boolean sOptionIndexLoaded = false;

    private final Context appContext;
    private final AudioPlayer audioPlayer;
    private final TtsManager ttsManager;

    public SpeechManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.audioPlayer = new AudioPlayer(context);
        this.ttsManager = TtsManager.getInstance(context);
    }

    // ========== 题目语音 ==========

    public void speakQuestion(int chapterId, int levelIndex, String text) {
        String path = "audio/q_" + chapterId + "_" + levelIndex + ".mp3";
        if (audioPlayer.playAsset(path)) {
            Log.d(TAG, "播放题目 MP3: " + path);
            return;
        }
        Log.w(TAG, "题目 MP3 不存在，回退 TTS: " + path + " | text=" + text);
        speakWithTts(text);
    }

    // ========== 选项语音（按文本查表） ==========

    /**
     * 播放选项语音（MP3 优先，TTS 兜底）。
     *
     * @param chapterId  章节 ID
     * @param levelIndex 关卡序号
     * @param optionText 选项文本（用于查表；查不到时作为 TTS 兜底文本）
     */
    public void speakOption(int chapterId, int levelIndex, String optionText) {
        if (optionText == null || optionText.isEmpty()) return;

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
            sOptionIndex = new JSONObject();   // 空对象，后续查询返回 null，回退 TTS
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) {}
            }
        }
        return sOptionIndex;
    }

    // ========== UI 语音 ==========

    public void speakUi(String scene, String fallbackText) {
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

    public void stop() {
        audioPlayer.stop();
        ttsManager.stop();
    }

    public void release() {
        audioPlayer.release();
    }
}