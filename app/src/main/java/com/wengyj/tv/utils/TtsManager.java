package com.wengyj.tv.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * TTS 单例管理器（作为 MP3 缺失时的兜底方案）。
 * 兼容 Android 4.3：使用 HashMap 版本 speak()
 */
public class TtsManager implements TextToSpeech.OnInitListener {

    private static final String TAG = "TtsManager";
    private static final String IFLYTEK_TTS_ENGINE = "com.iflytek.speechcloud";
    private static final long INIT_DELAY = 1500;
    private static final long SET_LANGUAGE_RETRY_DELAY = 800;
    private static final int MAX_SET_LANGUAGE_RETRY = 8;
    private static final int MAX_PENDING = 3;
    private static final String UTTERANCE_ID = "tts_default";

    private static TtsManager instance;
    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextToSpeech tts;
    private String currentEngine = null;
    private boolean isInitializing = false;
    private boolean isReady = false;
    private int setLanguageRetryCount = 0;

    private float speechRate = 0.5f;
    private float pitch = 1.1f;

    private static class PendingSpeak {
        final String text;
        PendingSpeak(String text) { this.text = text; }
    }
    private final List<PendingSpeak> pendingSpeaks = new ArrayList<>();

    private TtsManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized TtsManager getInstance(Context context) {
        if (instance == null) instance = new TtsManager(context);
        return instance;
    }

    public boolean isReady() { return isReady; }

    public void ensureInit() {
        if (tts != null || isInitializing) return;
        initTts(null);
    }

    private void initTts(String engine) {
        releaseTtsInternal();
        isInitializing = true;
        currentEngine = engine;
        setLanguageRetryCount = 0;
        try {
            tts = (engine == null) ? new TextToSpeech(appContext, this)
                    : new TextToSpeech(appContext, this, engine);
        } catch (Exception e) {
            isInitializing = false;
            tts = null;
            Log.w(TAG, "TTS 初始化异常: " + e.getMessage());
        }
    }

    private void releaseTtsInternal() {
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
        isReady = false;
        isInitializing = false;
        setLanguageRetryCount = 0;
    }

    @Override
    public void onInit(int status) {
        isInitializing = false;
        if (status != TextToSpeech.SUCCESS) {
            if (currentEngine == null) {
                initTts(IFLYTEK_TTS_ENGINE);
            } else {
                isReady = false;
                Log.w(TAG, "TTS 初始化失败（默认引擎+讯飞均失败）");
            }
            return;
        }
        handler.postDelayed(this::trySetLanguage, INIT_DELAY);
    }

    private void trySetLanguage() {
        if (tts == null) return;
        try {
            int result = tts.setLanguage(Locale.CHINESE);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = tts.setLanguage(Locale.CHINA);
            }
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (currentEngine == null) initTts(IFLYTEK_TTS_ENGINE);
                else {
                    isReady = false;
                    Log.w(TAG, "TTS 不支持中文");
                }
                return;
            }
            try {
                tts.setSpeechRate(speechRate);
                tts.setPitch(pitch);
            } catch (Exception ignored) {}
            isReady = true;
            setLanguageRetryCount = 0;
            Log.i(TAG, "TTS 就绪");
            flushPending();
        } catch (Exception e) {
            retrySetLanguage();
        }
    }

    private void retrySetLanguage() {
        setLanguageRetryCount++;
        if (setLanguageRetryCount < MAX_SET_LANGUAGE_RETRY) {
            handler.postDelayed(this::trySetLanguage, SET_LANGUAGE_RETRY_DELAY);
        } else {
            if (currentEngine == null) initTts(IFLYTEK_TTS_ENGINE);
            else {
                isReady = false;
                Log.w(TAG, "TTS 重试 " + MAX_SET_LANGUAGE_RETRY + " 次仍失败");
            }
        }
    }

    private void flushPending() {
        synchronized (pendingSpeaks) {
            for (PendingSpeak p : pendingSpeaks) doSpeak(p.text);
            pendingSpeaks.clear();
        }
    }

    public void speak(String text) {
        if (text == null || text.isEmpty()) return;
        ensureInit();
        if (isReady) {
            doSpeak(text);
        } else {
            synchronized (pendingSpeaks) {
                if (pendingSpeaks.size() < MAX_PENDING) pendingSpeaks.add(new PendingSpeak(text));
            }
            Log.d(TAG, "TTS 未就绪，已挂起: " + text);
        }
    }

    private void doSpeak(String text) {
        if (tts == null || !isReady) return;
        try {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
            Log.d(TAG, "TTS 播报: " + text);
        } catch (Exception e) {
            Log.w(TAG, "TTS speak 失败: " + e.getMessage());
        }
    }

    public void stop() {
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) {}
        }
        synchronized (pendingSpeaks) { pendingSpeaks.clear(); }
    }

    public void release() {
        releaseTtsInternal();
        synchronized (pendingSpeaks) { pendingSpeaks.clear(); }
    }
}