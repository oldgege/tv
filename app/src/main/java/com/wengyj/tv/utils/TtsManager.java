package com.wengyj.tv.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TTS 单例管理器
 * - 懒加载：只有首次调用 speak() 时才真正初始化引擎
 * - 单例跟随 Application，Activity/Fragment 退出不影响
 * - 初始化未完成时挂起播报请求，就绪后自动补发
 */
public class TtsManager implements TextToSpeech.OnInitListener {

    private static final String IFLYTEK_TTS_ENGINE = "com.iflytek.speechcloud";
    private static final int MAX_SET_LANGUAGE_RETRY = 5;
    private static final long SET_LANGUAGE_RETRY_DELAY = 400;
    private static final long INIT_DELAY = 300;
    private static final int MAX_PENDING = 3;

    private static TtsManager instance;

    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextToSpeech tts;
    private String currentEngine = null;    // null = 默认引擎
    private boolean isInitializing = false;
    private boolean isReady = false;
    private int setLanguageRetryCount = 0;
    private boolean hasShownErrorToast = false;

    // 语速/音调
    private float speechRate = 0.5f;
    private float pitch = 1.1f;

    // 初始化期间挂起的播报请求
    private static class PendingSpeak {
        final String text;
        final float rate;
        PendingSpeak(String text, float rate) {
            this.text = text;
            this.rate = rate;
        }
    }
    private final List<PendingSpeak> pendingSpeaks = new ArrayList<>();

    // 状态回调
    public interface OnTtsStateListener {
        void onTtsReady();
        void onTtsError();
    }
    private final List<OnTtsStateListener> listeners = new ArrayList<>();

    private TtsManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized TtsManager getInstance(Context context) {
        if (instance == null) {
            instance = new TtsManager(context);
        }
        return instance;
    }

    public boolean isReady() {
        return isReady;
    }

    /** 确保 TTS 已初始化（首次调用触发懒加载） */
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
            if (engine == null) {
                tts = new TextToSpeech(appContext, this);
            } else {
                tts = new TextToSpeech(appContext, this, engine);
            }
        } catch (Exception e) {
            isInitializing = false;
            tts = null;
            notifyError();
        }
    }

    private void releaseTtsInternal() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {}
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
                notifyError();
            }
            return;
        }

        // 延迟后设置语言（讯飞在 API 18 上 onInit 回调过早）
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
                if (currentEngine == null) {
                    initTts(IFLYTEK_TTS_ENGINE);
                } else {
                    isReady = false;
                    notifyError();
                }
                return;
            }

            try {
                tts.setSpeechRate(speechRate);
                tts.setPitch(pitch);
            } catch (Exception ignored) {}

            isReady = true;
            notifyReady();
            flushPending();

        } catch (Exception e) {
            setLanguageRetryCount++;
            if (setLanguageRetryCount < MAX_SET_LANGUAGE_RETRY) {
                handler.postDelayed(this::trySetLanguage, SET_LANGUAGE_RETRY_DELAY);
            } else {
                if (currentEngine == null) {
                    initTts(IFLYTEK_TTS_ENGINE);
                } else {
                    isReady = false;
                    notifyError();
                }
            }
        }
    }

    private void flushPending() {
        synchronized (pendingSpeaks) {
            for (PendingSpeak p : pendingSpeaks) {
                doSpeak(p.text, p.rate);
            }
            pendingSpeaks.clear();
        }
    }

    // ========== 公开 API ==========

    /** 用默认语速播报 */
    public void speak(String text) {
        speak(text, speechRate);
    }

    /** 指定语速播报 */
    public void speak(String text, float rate) {
        if (text == null || text.isEmpty()) return;
        ensureInit();

        if (isReady) {
            doSpeak(text, rate);
        } else {
            // 初始化未完成，挂起
            synchronized (pendingSpeaks) {
                if (pendingSpeaks.size() < MAX_PENDING) {
                    pendingSpeaks.add(new PendingSpeak(text, rate));
                }
            }
        }
    }

    private void doSpeak(String text, float rate) {
        if (tts == null || !isReady) return;
        try {
            if (Math.abs(rate - speechRate) > 0.01f) {
                tts.setSpeechRate(rate);
            }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            if (Math.abs(rate - speechRate) > 0.01f) {
                tts.setSpeechRate(speechRate);
            }
        } catch (Exception ignored) {}
    }

    /** 停止播报（不清空引擎） */
    public void stop() {
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) {}
        }
        synchronized (pendingSpeaks) {
            pendingSpeaks.clear();
        }
    }

    /** 完整释放（进程退出时用，一般不调用） */
    public void release() {
        releaseTtsInternal();
        synchronized (pendingSpeaks) {
            pendingSpeaks.clear();
        }
        listeners.clear();
    }

    // ========== 监听器 ==========

    public void addListener(OnTtsStateListener listener) {
        if (listener == null) return;
        if (!listeners.contains(listener)) listeners.add(listener);
        if (isReady) listener.onTtsReady();
    }

    public void removeListener(OnTtsStateListener listener) {
        listeners.remove(listener);
    }

    private void notifyReady() {
        for (OnTtsStateListener l : new ArrayList<>(listeners)) {
            try { l.onTtsReady(); } catch (Exception ignored) {}
        }
    }

    private void notifyError() {
        for (OnTtsStateListener l : new ArrayList<>(listeners)) {
            try { l.onTtsError(); } catch (Exception ignored) {}
        }
    }

    public void showErrorToastOnce() {
        if (hasShownErrorToast) return;
        hasShownErrorToast = true;
        try {
            Toast.makeText(appContext, "语音播报不可用", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }
}