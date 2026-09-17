package com.wengyj.tv.data.adapter;

import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.wengyj.tv.R;
import com.wengyj.tv.utils.MistakeManager;

import java.util.List;

public class MistakeAdapter extends RecyclerView.Adapter<MistakeAdapter.ViewHolder> {

    public interface OnMistakeClickListener {
        void onMistakeClick(MistakeManager.Mistake mistake);
    }

    private final List<MistakeManager.Mistake> items;
    private final OnMistakeClickListener listener;

    public MistakeAdapter(List<MistakeManager.Mistake> items, OnMistakeClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mistake, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MistakeManager.Mistake m = items.get(position);

        // ★ 重置焦点态（避免 RecyclerView 复用残留）
        holder.card.setScaleX(1.0f);
        holder.card.setScaleY(1.0f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            holder.card.setCardElevation(6f);
        }

        holder.tvChapter.setText("第 " + m.chapterId + " 章 · 第 " + (m.levelId % 100) + " 关");
        holder.tvWrongCount.setText("答错 " + m.wrongCount + " 次");
        holder.tvPrompt.setText(m.prompt);
        holder.tvCorrect.setText("正确答案：" + m.correctAnswer);

        holder.card.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150)
                        .setInterpolator(new AccelerateDecelerateInterpolator()).start();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    holder.card.setCardElevation(16f);
                }
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150)
                        .setInterpolator(new AccelerateDecelerateInterpolator()).start();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    holder.card.setCardElevation(6f);
                }
            }
        });

        holder.card.setOnClickListener(v -> {
            if (listener != null) listener.onMistakeClick(m);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        TextView tvChapter, tvWrongCount, tvPrompt, tvCorrect;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_mistake);
            tvChapter = itemView.findViewById(R.id.tv_chapter);
            tvWrongCount = itemView.findViewById(R.id.tv_wrong_count);
            tvPrompt = itemView.findViewById(R.id.tv_prompt);
            tvCorrect = itemView.findViewById(R.id.tv_correct);
        }
    }
}