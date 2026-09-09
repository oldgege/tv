package com.wengyj.tv.ui.level;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
    private int chapterId;
    private RecyclerView recyclerView;
    private LevelAdapter adapter;
    private ProgressManager progressManager;
    private List<Level> levels;

    private TextToSpeech tts;
    private boolean isTtsReady = false;

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
        tts = new TextToSpeech(getContext(), this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_level, container, false);
        recyclerView = view.findViewById(R.id.recycler_levels);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 4));
        recyclerView.setHasFixedSize(true);

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
            // ---- 修复：基于列表索引判断解锁 ----
            for (int i = 0; i < levels.size(); i++) {
                Level level = levels.get(i);
                if (i == 0) {
                    level.setLocked(false); // 第一关总是解锁
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
        } else {
            Toast.makeText(getContext(), "章节数据错误", Toast.LENGTH_SHORT).show();
        }
        return view;
    }

    // ---------- TTS ----------
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.CHINESE);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US);
            }
            tts.setSpeechRate(0.7f);
            tts.setPitch(1.1f);
            isTtsReady = true;
        } else {
            isTtsReady = false;
            Toast.makeText(getContext(), "语音播报不可用", Toast.LENGTH_SHORT).show();
        }
    }

    // 在 LevelFragment 中，修改 speakText 方法：
    private void speakText(String text) {
        if (tts != null && isTtsReady && text != null && !text.isEmpty()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null); // 兼容 API 18
        }
    }

    @Override
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }
}