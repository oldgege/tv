package com.wengyj.tv.ui.level;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.wengyj.tv.utils.SpeechManager;

import java.util.List;

public class LevelFragment extends Fragment {
    private static final String ARG_CHAPTER_ID = "chapter_id";

    private int chapterId;
    private RecyclerView recyclerView;
    private LevelAdapter adapter;
    private ProgressManager progressManager;
    private List<Level> levels;
    private TextView tvStars;
    private View tvBack;

    private SpeechManager speechManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

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
        speechManager = new SpeechManager(getContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_level, container, false);
        recyclerView = view.findViewById(R.id.recycler_levels);
        tvStars = view.findViewById(R.id.tv_stars);
        tvBack = view.findViewById(R.id.tv_back);

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 4));
        recyclerView.setHasFixedSize(true);
        recyclerView.setFocusable(true);
        recyclerView.setFocusableInTouchMode(true);

        updateStarsDisplay();

        if (tvBack != null) {
            tvBack.setOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            });
        }

        Chapter chapter = findChapterById(chapterId);

        if (chapter != null) {
            levels = chapter.getLevels();
            refreshLevelStates();

            adapter = new LevelAdapter(levels, new LevelAdapter.OnLevelClickListener() {
                @Override
                public void onLevelClick(Level level) {
                    if (level.isLocked()) {
                        speechManager.speakUi("not_unlocked", "还未解锁哟");
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

            handler.postDelayed(() ->
                    speechManager.speakUi("level_menu", "请选择关卡"), 500);
        } else {
            Toast.makeText(getContext(), "章节数据错误: " + chapterId, Toast.LENGTH_SHORT).show();
        }
        return view;
    }

    private Chapter findChapterById(int chapterId) {
        List<Chapter> chapters = ChapterDataSource.getChaptersByGrade(1);
        for (Chapter c : chapters) {
            if (c.getId() == chapterId) return c;
        }
        chapters = ChapterDataSource.getChaptersByGrade(2);
        for (Chapter c : chapters) {
            if (c.getId() == chapterId) return c;
        }
        return null;
    }

    private void refreshLevelStates() {
        if (levels == null) return;
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
            refreshLevelStates();
            adapter.refreshAll();
        }

        if (recyclerView != null) {
            recyclerView.post(() -> recyclerView.requestFocus());
        }
    }

    @Override
    public void onDestroy() {
        if (speechManager != null) {
            speechManager.release();
            speechManager = null;
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}