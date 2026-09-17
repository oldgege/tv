package com.wengyj.tv.ui.grade;

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
import com.wengyj.tv.data.adapter.GradeAdapter;
import com.wengyj.tv.data.datasource.GradeDataSource;
import com.wengyj.tv.data.model.Grade;
import com.wengyj.tv.ui.MainActivity;
import com.wengyj.tv.utils.MistakeManager;
import com.wengyj.tv.utils.MusicManager;
import com.wengyj.tv.utils.ProgressManager;
import com.wengyj.tv.utils.SpeechManager;

import java.util.List;

public class GradeFragment extends Fragment {
    private RecyclerView recyclerView;
    private GradeAdapter adapter;
    private TextView tvStars;
    private View tvBgmToggle;
    private TextView tvBgmIcon;
    private View tvBack;
    private View tvMistakeBook;       // ★
    private TextView tvMistakeCount;  // ★
    private ProgressManager progressManager;
    private MistakeManager mistakeManager;   // ★
    private SpeechManager speechManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_grade, container, false);
        recyclerView = view.findViewById(R.id.recycler_grades);
        tvStars = view.findViewById(R.id.tv_stars_grade);
        tvBgmToggle = view.findViewById(R.id.tv_bgm_toggle);
        tvBgmIcon = view.findViewById(R.id.tv_bgm_icon);
        tvBack = view.findViewById(R.id.tv_back);
        tvMistakeBook = view.findViewById(R.id.tv_mistake_book);       // ★
        tvMistakeCount = view.findViewById(R.id.tv_mistake_count);     // ★

        progressManager = new ProgressManager(getContext());
        mistakeManager = new MistakeManager(getContext());             // ★
        speechManager = SpeechManager.getInstance(getContext());

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        recyclerView.setHasFixedSize(true);
        recyclerView.setFocusable(true);
        recyclerView.setFocusableInTouchMode(true);

        updateStarsDisplay();
        updateBgmIcon(progressManager.isBgmEnabled());
        updateMistakeCount();   // ★

        tvBgmToggle.setOnClickListener(v -> {
            boolean newState = !progressManager.isBgmEnabled();
            progressManager.setBgmEnabled(newState);
            MusicManager.getInstance(getContext()).setEnabled(newState);
            updateBgmIcon(newState);
        });

        // ★ 错题本入口
        tvMistakeBook.setOnClickListener(v -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.showMistakeBookFragment();
            }
        });

        tvBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        List<Grade> grades = GradeDataSource.getAllGrades();
        adapter = new GradeAdapter(grades, grade -> {
            if (!grade.isAvailable()) {
                speechManager.speakUi("coming_soon", "即将上线");
                Toast.makeText(getContext(), "🔒 " + grade.getTitle() + " 即将上线",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.showChapterFragment(grade.getId());
            }
        });
        recyclerView.setAdapter(adapter);

        recyclerView.post(() -> recyclerView.requestFocus());

        handler.postDelayed(() ->
                speechManager.speakUi("grade_menu", "请选择年级"), 250);

        return view;
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

    /** ★ 更新错题本数量徽标 */
    private void updateMistakeCount() {
        if (tvMistakeCount == null || mistakeManager == null) return;
        int count = mistakeManager.getCount();
        if (count > 0) {
            tvMistakeCount.setVisibility(View.VISIBLE);
            tvMistakeCount.setText(String.valueOf(count));
        } else {
            tvMistakeCount.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStarsDisplay();
        updateBgmIcon(progressManager.isBgmEnabled());
        updateMistakeCount();   // ★ 从错题本返回时刷新
        MusicManager.getInstance(getContext()).restoreVolume();
        MusicManager.getInstance(getContext()).start();
        if (recyclerView != null) {
            recyclerView.post(() -> recyclerView.requestFocus());
        }
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        speechManager = null;
        super.onDestroy();
    }
}