package com.wengyj.tv.ui.mistake;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wengyj.tv.R;
import com.wengyj.tv.data.adapter.MistakeAdapter;
import com.wengyj.tv.ui.MainActivity;
import com.wengyj.tv.utils.MistakeManager;
import com.wengyj.tv.utils.SpeechManager;

import java.util.List;

public class MistakeBookFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private View tvBack;
    private View tvClear;
    private MistakeManager mistakeManager;
    private SpeechManager speechManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_mistake_book, container, false);

        recyclerView = v.findViewById(R.id.recycler_mistakes);
        tvEmpty = v.findViewById(R.id.tv_empty);
        tvBack = v.findViewById(R.id.tv_back);
        tvClear = v.findViewById(R.id.tv_clear);

        mistakeManager = new MistakeManager(getContext());
        speechManager = SpeechManager.getInstance(getContext());

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setFocusable(true);
        recyclerView.setFocusableInTouchMode(true);

        tvBack.setOnClickListener(view -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });

        tvClear.setOnClickListener(view -> confirmClear());

        refreshList();
        return v;
    }

    private void refreshList() {
        List<MistakeManager.Mistake> items = mistakeManager.getAll();

        if (items.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            tvClear.setVisibility(View.GONE);
            return;
        }

        recyclerView.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        tvClear.setVisibility(View.VISIBLE);

        MistakeAdapter adapter = new MistakeAdapter(items, m -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.showGameFragmentForReview(m.levelId);
            }
        });
        recyclerView.setAdapter(adapter);

        recyclerView.post(() -> recyclerView.requestFocus());
    }

    private void confirmClear() {
        new AlertDialog.Builder(requireContext())
                .setTitle("清空错题本")
                .setMessage("确定要清空所有错题吗？此操作不可撤销。")
                .setPositiveButton("清空", (d, w) -> {
                    mistakeManager.clearAll();
                    Toast.makeText(getContext(), "错题本已清空", Toast.LENGTH_SHORT).show();
                    refreshList();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 复习完返回后刷新（错题可能已被移除）
        refreshList();
    }
}