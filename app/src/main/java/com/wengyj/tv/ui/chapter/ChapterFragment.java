package com.wengyj.tv.ui.chapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wengyj.tv.R;
import com.wengyj.tv.data.datasource.ChapterDataSource;
import com.wengyj.tv.data.model.Chapter;
import com.wengyj.tv.ui.MainActivity;
import com.wengyj.tv.utils.MusicManager;
import com.wengyj.tv.utils.ProgressManager;

import java.util.List;

public class ChapterFragment extends Fragment {
    private RecyclerView recyclerView;
    private ChapterAdapter adapter;
    private TextView tvStars;
    private TextView tvBgmToggle;
    private ProgressManager progressManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chapter, container, false);
        recyclerView = view.findViewById(R.id.recycler_chapters);
        tvStars = view.findViewById(R.id.tv_stars_chapter);
        tvBgmToggle = view.findViewById(R.id.tv_bgm_toggle);

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

        List<Chapter> chapters = ChapterDataSource.getAllChapters(getContext());
        adapter = new ChapterAdapter(chapters, chapter -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.showLevelFragment(chapter.getId());
            }
        });
        recyclerView.setAdapter(adapter);

        // 请求焦点，确保遥控器可以操作（聚焦到 BGM 开关或 RecyclerView）
        recyclerView.post(() -> recyclerView.requestFocus());

        return view;
    }

    private void updateStarsDisplay() {
        if (tvStars != null && progressManager != null) {
            int stars = progressManager.getStars();
            tvStars.setText(String.valueOf(stars));
        }
    }

    private void updateBgmIcon(boolean enabled) {
        if (tvBgmToggle != null) {
            tvBgmToggle.setText(enabled ? "🔊" : "🔇");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStarsDisplay();
        updateBgmIcon(progressManager.isBgmEnabled());
        // 恢复音乐（如果在游戏中降低了音量）
        MusicManager.getInstance(getContext()).restoreVolume();
        MusicManager.getInstance(getContext()).start();
        if (recyclerView != null) {
            recyclerView.post(() -> recyclerView.requestFocus());
        }
    }
}