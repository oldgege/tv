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
    private RecyclerView recyclerView;
    private ChapterAdapter adapter;
    private TextView tvStars;
    private View tvBgmToggle;
    private TextView tvBgmIcon;
    private View tvBack;
    private ProgressManager progressManager;

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

        // 初始化星星显示
        updateStarsDisplay();
        // 初始化 BGM 图标
        updateBgmIcon(progressManager.isBgmEnabled());

        // BGM 开关点击
        tvBgmToggle.setOnClickListener(v -> {
            boolean newState = !progressManager.isBgmEnabled();
            progressManager.setBgmEnabled(newState);
            MusicManager.getInstance(getContext()).setEnabled(newState);
            updateBgmIcon(newState);
        });

        // 退出按钮
        tvBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        // 构建章节列表 + 解锁状态
        List<Chapter> chapters = ChapterDataSource.getAllChapters(getContext());
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

        // 请求焦点
        recyclerView.post(() -> recyclerView.requestFocus());

        return view;
    }

    /**
     * 计算已解锁的章节：
     * 第1章默认解锁；后续章节需要前一章全部通关才解锁。
     */
    private Set<Integer> computeUnlockedChapters(List<Chapter> chapters) {
        Set<Integer> unlocked = new HashSet<>();
        if (chapters.isEmpty()) return unlocked;

        // 第一章总是解锁
        unlocked.add(chapters.get(0).getId());

        // 依次检查后续章节
        for (int i = 1; i < chapters.size(); i++) {
            Chapter prevChapter = chapters.get(i - 1);
            if (isChapterCompleted(prevChapter)) {
                unlocked.add(chapters.get(i).getId());
            } else {
                // 前一章未通关，后续全部锁定，直接跳出
                break;
            }
        }
        return unlocked;
    }

    /**
     * 判断某章节是否所有关卡都已完成
     */
    private boolean isChapterCompleted(Chapter chapter) {
        Set<Integer> completed = progressManager.getCompletedLevels();
        if (chapter.getLevels() == null || chapter.getLevels().isEmpty()) {
            return false;
        }
        for (Level level : chapter.getLevels()) {
            if (!completed.contains(level.getId())) {
                return false;
            }
        }
        return true;
    }

    private void updateStarsDisplay() {
        if (tvStars != null && progressManager != null) {
            int stars = progressManager.getStars();
            tvStars.setText(String.valueOf(stars));
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

        // 每次返回时重新计算解锁状态（因为可能完成了新的章节）
        if (adapter != null && recyclerView != null) {
            List<Chapter> chapters = ChapterDataSource.getAllChapters(getContext());
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