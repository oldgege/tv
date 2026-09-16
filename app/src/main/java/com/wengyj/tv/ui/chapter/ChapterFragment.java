package com.wengyj.tv.ui.chapter;

import android.os.Bundle;
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
import com.wengyj.tv.utils.MusicManager;
import com.wengyj.tv.utils.ProgressManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChapterFragment extends Fragment {
    private static final String ARG_GRADE_ID = "grade_id";

    private int gradeId = 1;
    private RecyclerView recyclerView;
    private ChapterAdapter adapter;
    private TextView tvStars;
    private View tvBgmToggle;
    private TextView tvBgmIcon;
    private View tvBack;
    private ProgressManager progressManager;

    public static ChapterFragment newInstance(int gradeId) {
        ChapterFragment fragment = new ChapterFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_GRADE_ID, gradeId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            gradeId = getArguments().getInt(ARG_GRADE_ID, 1);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chapter, container, false);
        recyclerView = view.findViewById(R.id.recycler_chapters);
        tvStars = view.findViewById(R.id.tv_stars_chapter);
        tvBgmToggle = view.findViewById(R.id.tv_bgm_toggle);
        tvBgmIcon = view.findViewById(R.id.tv_bgm_icon);
        tvBack = view.findViewById(R.id.tv_back);

        progressManager = new ProgressManager(getContext());

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        recyclerView.setHasFixedSize(true);
        recyclerView.setFocusable(true);
        recyclerView.setFocusableInTouchMode(true);

        updateStarsDisplay();
        updateBgmIcon(progressManager.isBgmEnabled());

        tvBgmToggle.setOnClickListener(v -> {
            boolean newState = !progressManager.isBgmEnabled();
            progressManager.setBgmEnabled(newState);
            MusicManager.getInstance(getContext()).setEnabled(newState);
            updateBgmIcon(newState);
        });

        // 返回按钮 → 回到年级选择
        tvBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        // 加载该年级的章节
        List<Chapter> chapters = ChapterDataSource.getChaptersByGrade(gradeId);
        Set<Integer> unlockedChapterIds = computeUnlockedChapters(chapters);

        adapter = new ChapterAdapter(chapters, unlockedChapterIds, (chapter, isLocked) -> {
            if (isLocked) {
                Toast.makeText(getContext(), "🔒 请先完成上一章", Toast.LENGTH_SHORT).show();
            } else {
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null) {
                    activity.showLevelFragment(chapter.getId());
                }
            }
        });
        recyclerView.setAdapter(adapter);

        recyclerView.post(() -> recyclerView.requestFocus());

        return view;
    }

    private Set<Integer> computeUnlockedChapters(List<Chapter> chapters) {
        Set<Integer> unlocked = new HashSet<>();
        if (chapters.isEmpty()) return unlocked;
        unlocked.add(chapters.get(0).getId());
        for (int i = 1; i < chapters.size(); i++) {
            Chapter prev = chapters.get(i - 1);
            if (isChapterCompleted(prev)) {
                unlocked.add(chapters.get(i).getId());
            } else {
                break;
            }
        }
        return unlocked;
    }

    private boolean isChapterCompleted(Chapter chapter) {
        Set<Integer> completed = progressManager.getCompletedLevels();
        if (chapter.getLevels() == null || chapter.getLevels().isEmpty()) return false;
        for (Level level : chapter.getLevels()) {
            if (!completed.contains(level.getId())) return false;
        }
        return true;
    }

    private void updateStarsDisplay() {
        if (tvStars != null && progressManager != null) {
            tvStars.setText(String.valueOf(progressManager.getStars()));
        }
    }

    private void updateBgmIcon(boolean enabled) {
        if (tvBgmIcon != null) {
            tvBgmIcon.setText(enabled ? "🔊" : "🔇");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStarsDisplay();
        updateBgmIcon(progressManager.isBgmEnabled());
        MusicManager.getInstance(getContext()).restoreVolume();
        MusicManager.getInstance(getContext()).start();

        // 每次返回时刷新解锁状态
        if (adapter != null && recyclerView != null) {
            List<Chapter> chapters = ChapterDataSource.getChaptersByGrade(gradeId);
            Set<Integer> unlockedChapterIds = computeUnlockedChapters(chapters);
            adapter = new ChapterAdapter(chapters, unlockedChapterIds, (chapter, isLocked) -> {
                if (isLocked) {
                    Toast.makeText(getContext(), "🔒 请先完成上一章", Toast.LENGTH_SHORT).show();
                } else {
                    MainActivity activity = (MainActivity) getActivity();
                    if (activity != null) {
                        activity.showLevelFragment(chapter.getId());
                    }
                }
            });
            recyclerView.setAdapter(adapter);
        }

        if (recyclerView != null) {
            recyclerView.post(() -> recyclerView.requestFocus());
        }
    }
}