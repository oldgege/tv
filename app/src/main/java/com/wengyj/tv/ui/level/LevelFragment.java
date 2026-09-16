package com.wengyj.tv.ui.level;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wengyj.tv.R;
import com.wengyj.tv.data.datasource.ChapterDataSource;
import com.wengyj.tv.data.model.Chapter;
import com.wengyj.tv.data.model.Level;
import com.wengyj.tv.ui.MainActivity;
import com.wengyj.tv.utils.ProgressManager;

import java.util.List;
import java.util.Locale;

public class LevelFragment extends Fragment implements TextToSpeech.OnInitListener {
    private static final String ARG_CHAPTER_ID = "chapter_id";

    private static final String IFLYTEK_TTS_ENGINE = "com.iflytek.speechcloud";
    private static boolean hasShownTtsError = false;

    private static final int MAX_SET_LANGUAGE_RETRY = 5;
    private static final long SET_LANGUAGE_RETRY_DELAY = 400;

    private int chapterId;
    private RecyclerView recyclerView;
    private LevelAdapter adapter;
    private ProgressManager progressManager;
    private List<Level> levels;
    private TextView tvStars;

    private TextToSpeech tts;
    private String currentEngine = null;
    private boolean isInitializing = false;
    private boolean isTtsReady = false;
    private int setLanguageRetryCount = 0;

    private android.os.Handler handler = new android.os.Handler();

    public static LevelFragment newInstance(int chapterId) {
        LevelFragment fragment = new LevelFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_CHAPTER_ID, chapterId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            chapterId = getArguments().getInt(ARG_CHAPTER_ID);
        }
        progressManager = new ProgressManager(getContext());
        initTts(null);
    }

    private void initTts(String engine) {
        releaseTts();
        isInitializing = true;
        currentEngine = engine;
        setLanguageRetryCount = 0;

        if (engine == null) {
            tts = new TextToSpeech(getContext(), this);
        } else {
            tts = new TextToSpeech(getContext(), this, engine);
        }
    }

    private void releaseTts() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {}
            tts = null;
        }
        isTtsReady = false;
        isInitializing = false;
        setLanguageRetryCount = 0;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_level, container, false);
        recyclerView = view.findViewById(R.id.recycler_levels);
        tvStars = view.findViewById(R.id.tv_stars);

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 4));
        recyclerView.setHasFixedSize(true);
        recyclerView.setFocusable(true);
        recyclerView.setFocusableInTouchMode(true);

        updateStarsDisplay();

        List<Chapter> all = ChapterDataSource.getAllChapters(getContext());
        Chapter chapter = null;
        for (Chapter c : all) {
            if (c.getId() == chapterId) {
                chapter = c;
                break;
            }
        }
        if (chapter != null) {
            levels = chapter.getLevels();
            for (int i = 0; i < levels.size(); i++) {
                Level level = levels.get(i);
                if (i == 0) {
                    level.setLocked(false);
                } else {
                    Level prevLevel = levels.get(i - 1);
                    boolean prevCompleted = progressManager.isLevelCompleted(prevLevel.getId());
                    level.setLocked(!prevCompleted);
                }
                level.setCompleted(progressManager.isLevelCompleted(level.getId()));
            }

            adapter = new LevelAdapter(levels, new LevelAdapter.OnLevelClickListener() {
                @Override
                public void onLevelClick(Level level) {
                    if (level.isLocked()) {
                        speakText("还未解锁哟");
                        Toast.makeText(getContext(), "🔒 还未解锁哟", Toast.LENGTH_SHORT).show();
                    } else {
                        progressManager.saveCurrentProgress(chapterId, level.getId());
                        MainActivity activity = (MainActivity) getActivity();
                        if (activity != null) {
                            activity.showGameFragment(level.getId());
                        }
                    }
                }
            });
            recyclerView.setAdapter(adapter);

            recyclerView.post(() -> recyclerView.requestFocus());
        }
        return view;
    }

    private void updateStarsDisplay() {
        if (tvStars != null) {
            tvStars.setText("⭐ " + progressManager.getStars());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStarsDisplay();
        if (adapter != null && levels != null) {
            for (int i = 0; i < levels.size(); i++) {
                Level level = levels.get(i);
                if (i == 0) {
                    level.setLocked(false);
                } else {
                    Level prevLevel = levels.get(i - 1);
                    boolean prevCompleted = progressManager.isLevelCompleted(prevLevel.getId());
                    level.setLocked(!prevCompleted);
                }
                level.setCompleted(progressManager.isLevelCompleted(level.getId()));
            }
            adapter.notifyDataSetChanged();
        }
        if (recyclerView != null) {
            recyclerView.post(() -> recyclerView.requestFocus());
        }
        if (tts == null && !isInitializing) {
            hasShownTtsError = false;
            initTts(null);
        }
    }

    // ---------- TTS 回调 ----------
    @Override
    public void onInit(int status) {
        isInitializing = false;

        if (status != TextToSpeech.SUCCESS) {
            if (currentEngine == null) {
                initTts(IFLYTEK_TTS_ENGINE);
            } else {
                isTtsReady = false;
                showTtsErrorToast();
            }
            return;
        }

        handler.postDelayed(this::trySetLanguage, 300);
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
                    isTtsReady = false;
                    showTtsErrorToast();
                }
                return;
            }

            try {
                tts.setSpeechRate(0.7f);
                tts.setPitch(1.1f);
            } catch (Exception ignored) {}
            isTtsReady = true;

        } catch (Exception e) {
            setLanguageRetryCount++;
            if (setLanguageRetryCount < MAX_SET_LANGUAGE_RETRY) {
                handler.postDelayed(this::trySetLanguage, SET_LANGUAGE_RETRY_DELAY);
            } else {
                if (currentEngine == null) {
                    initTts(IFLYTEK_TTS_ENGINE);
                } else {
                    isTtsReady = false;
                    showTtsErrorToast();
                }
            }
        }
    }

    /**
     * TTS 不可用时只弹 Toast 提示，不跳转设置页
     */
    private void showTtsErrorToast() {
        if (hasShownTtsError) return;
        hasShownTtsError = true;

        if (!isAdded() || getContext() == null) return;
        try {
            Toast.makeText(getContext(), "语音播报不可用", Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {}
    }

    private void speakText(String text) {
        if (tts != null && isTtsReady && text != null && !text.isEmpty()) {
            try {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDestroy() {
        releaseTts();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}