package com.wengyj.tv.utils;

import android.content.Context;
import android.util.Log;

/**
 * 语音统一入口。
 *
 * <p>策略：MP3 优先，TTS 兜底。</p>
 * <p>命名规则：
 * <ul>
 *   <li>题目：q_{chapterId}_{levelIndex}.mp3</li>
 *   <li>选项：q_{chapterId}_{levelIndex}_{optionIndex}.mp3  （optionIndex: 1~4）</li>
 *   <li>UI：ui_{scene}.mp3</li>
 * </ul>
 * </p>
 */
public class SpeechManager {

    private static final String TAG = "SpeechManager";

    private final AudioPlayer audioPlayer;
    private final TtsManager ttsManager;

    public SpeechManager(Context context) {
        this.audioPlayer = new AudioPlayer(context);
        this.ttsManager = TtsManager.getInstance(context);
    }

    // ========== 题目语音 ==========

    /**
     * 播放题目语音（MP3 优先，TTS 兜底）。
     *
     * @param chapterId  章节 ID
     * @param levelIndex 关卡序号
     * @param text       题目文本（TTS 兜底用）
     */
    public void speakQuestion(int chapterId, int levelIndex, String text) {
        String path = "audio/q_" + chapterId + "_" + levelIndex + ".mp3";
        if (audioPlayer.exists(path)) {
            audioPlayer.playAsset(path);
            Log.d(TAG, "播放题目 MP3: " + path);
            return;
        }
        Log.w(TAG, "题目 MP3 不存在，回退 TTS: " + path + " | text=" + text);
        speakWithTts(text);
    }

    // ========== 选项语音 ==========

    /**
     * 播放选项语音（MP3 优先，TTS 兜底）。
     *
     * @param chapterId   章节 ID
     * @param levelIndex  关卡序号
     * @param optionIndex 选项序号（1~4）
     * @param optionText  选项文本（TTS 兜底用）
     */
    public void speakOption(int chapterId, int levelIndex, int optionIndex, String optionText) {
        String path = "audio/q_" + chapterId + "_" + levelIndex + "_" + optionIndex + ".mp3";
        if (audioPlayer.exists(path)) {
            audioPlayer.playAsset(path);
            Log.d(TAG, "播放选项 MP3: " + path + " (" + optionText + ")");
            return;
        }
        Log.w(TAG, "选项 MP3 不存在，回退 TTS: " + path + " | text=" + optionText);
        speakWithTts(optionText);
    }

    // ========== UI 语音 ==========

    /**
     * 播放 UI 场景语音（MP3 优先，TTS 兜底）。
     */
    public void speakUi(String scene, String fallbackText) {
        String path = "audio/ui_" + scene + ".mp3";
        if (audioPlayer.exists(path)) {
            audioPlayer.playAsset(path);
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