package com.wengyj.tv.ui.grade;

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
import com.wengyj.tv.data.adapter.GradeAdapter;
import com.wengyj.tv.data.datasource.GradeDataSource;
import com.wengyj.tv.data.model.Grade;
import com.wengyj.tv.ui.MainActivity;
import com.wengyj.tv.utils.MusicManager;
import com.wengyj.tv.utils.ProgressManager;

import java.util.List;

public class GradeFragment extends Fragment {
    private RecyclerView recyclerView;
    private GradeAdapter adapter;
    private TextView tvStars;
    private View tvBgmToggle;
    private TextView tvBgmIcon;
    private View tvBack;
    private ProgressManager progressManager;

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

        progressManager = new ProgressManager(getContext());

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
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

        tvBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        List<Grade> grades = GradeDataSource.getAllGrades();
        adapter = new GradeAdapter(grades, grade -> {
            if (!grade.isAvailable()) {
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

    @Override
    public void onResume() {
        super.onResume();
        updateStarsDisplay();
        updateBgmIcon(progressManager.isBgmEnabled());
        MusicManager.getInstance(getContext()).restoreVolume();
        MusicManager.getInstance(getContext()).start();
        if (recyclerView != null) {
            recyclerView.post(() -> recyclerView.requestFocus());
        }
    }
}