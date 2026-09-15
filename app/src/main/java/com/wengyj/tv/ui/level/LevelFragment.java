package com.wengyj.tv.ui.level;

import android.content.ActivityNotFoundException;
import android.content.Intent;
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
    private static boolean hasOpenedTtsSettings = false;

    private int chapterId;
    private RecyclerView recyclerView;
    private LevelAdapter adapter;
    private ProgressManager progressManager;
    private List<Level> levels;
    private TextView tvStars;

    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private boolean triedIflytek = false;

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
        initDefaultTts();
    }

    private void initDefaultTts() {
        releaseTts();
        tts = new TextToSpeech(getContext(), this);
    }

    private void initIflytekTts() {
        releaseTts();
        triedIflytek = true;
        tts = new TextToSpeech(getContext(), this, IFLYTEK_TTS_ENGINE);
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
        // 从 TTS 设置返回后重新初始化
        if (!isTtsReady && tts == null) {
            triedIflytek = false;
            hasOpenedTtsSettings = false;
            initDefaultTts();
        }
    }

    // ---------- TTS 回调 ----------
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.CHINESE);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = tts.setLanguage(Locale.CHINA);
            }
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (!triedIflytek) {
                    initIflytekTts();
                } else {
                    isTtsReady = false;
                    openTtsSettingsOnce();
                }
            } else {
                tts.setSpeechRate(0.7f);
                tts.setPitch(1.1f);
                isTtsReady = true;
            }
        } else {
            if (!triedIflytek) {
                initIflytekTts();
            } else {
                isTtsReady = false;
                openTtsSettingsOnce();
            }
        }
    }

    private void openTtsSettingsOnce() {
        if (hasOpenedTtsSettings) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "语音播报不可用，请检查系统语音设置",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        hasOpenedTtsSettings = true;

        if (getContext() != null) {
            Toast.makeText(getContext(), "语音引擎不可用，正在打开语音设置...",
                    Toast.LENGTH_LONG).show();
        }

        try {
            Intent intent = new Intent("android.speech.tts.engine.TTS_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (ActivityNotFoundException e) {
            // 忽略
        }

        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "无法打开系统设置", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void speakText(String text) {
        if (tts != null && isTtsReady && text != null && !text.isEmpty()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    @Override
    public void onDestroy() {
        releaseTts();
        super.onDestroy();
    }
}