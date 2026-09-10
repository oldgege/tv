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
import com.wengyj.tv.utils.ProgressManager;

import java.util.List;

public class ChapterFragment extends Fragment {
    private RecyclerView recyclerView;
    private ChapterAdapter adapter;
    private TextView tvStars;
    private ProgressManager progressManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chapter, container, false);
        recyclerView = view.findViewById(R.id.recycler_chapters);
        tvStars = view.findViewById(R.id.tv_stars_chapter);

        progressManager = new ProgressManager(getContext());

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        recyclerView.setHasFixedSize(true);
        recyclerView.setFocusable(true);
        recyclerView.setFocusableInTouchMode(true);

        // 初始化星星显示
        updateStarsDisplay();

        List<Chapter> chapters = ChapterDataSource.getAllChapters(getContext());
        adapter = new ChapterAdapter(chapters, chapter -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.showLevelFragment(chapter.getId());
            }
        });
        recyclerView.setAdapter(adapter);

        // 请求焦点，确保遥控器可以操作
        recyclerView.post(() -> recyclerView.requestFocus());

        return view;
    }

    private void updateStarsDisplay() {
        if (tvStars != null && progressManager != null) {
            int stars = progressManager.getStars();
            tvStars.setText(String.valueOf(stars));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 返回章节菜单时刷新星星数量
        updateStarsDisplay();
        if (recyclerView != null) {
            recyclerView.post(() -> recyclerView.requestFocus());
        }
    }
}