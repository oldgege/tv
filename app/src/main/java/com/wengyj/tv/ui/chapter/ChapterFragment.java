package com.wengyj.tv.ui.chapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wengyj.tv.R;
import com.wengyj.tv.data.datasource.ChapterDataSource;
import com.wengyj.tv.data.model.Chapter;
import com.wengyj.tv.ui.MainActivity;

import java.util.List;

public class ChapterFragment extends Fragment {
    private RecyclerView recyclerView;
    private ChapterAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chapter, container, false);
        recyclerView = view.findViewById(R.id.recycler_chapters);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        recyclerView.setHasFixedSize(true);

        // 支持遥控器焦点
        recyclerView.setFocusable(true);
        recyclerView.setFocusableInTouchMode(true);

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
}