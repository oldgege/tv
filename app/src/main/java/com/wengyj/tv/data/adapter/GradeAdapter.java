package com.wengyj.tv.data.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.wengyj.tv.R;
import com.wengyj.tv.data.model.Grade;

import java.util.List;

public class GradeAdapter extends RecyclerView.Adapter<GradeAdapter.ViewHolder> {
    private List<Grade> grades;
    private OnGradeClickListener listener;

    private static final int COLOR_NORMAL = Color.parseColor("#333333");
    private static final int COLOR_FOCUSED = Color.parseColor("#FF9800");
    private static final int COLOR_LOCKED = Color.parseColor("#222222");

    public interface OnGradeClickListener {
        void onGradeClick(Grade grade);
    }

    public GradeAdapter(List<Grade> grades, OnGradeClickListener listener) {
        this.grades = grades;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_grade, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Grade grade = grades.get(position);
        boolean available = grade.isAvailable();

        holder.title.setText(grade.getTitle());
        holder.subtitle.setText(grade.getSubtitle());
        holder.icon.setImageResource(grade.getIconRes());

        holder.card.setCardBackgroundColor(available ? COLOR_NORMAL : COLOR_LOCKED);
        holder.card.setAlpha(available ? 1.0f : 0.5f);

        holder.card.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(200)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
                holder.card.setCardElevation(20f);
                if (available) {
                    holder.card.setCardBackgroundColor(COLOR_FOCUSED);
                }
            } else {
                v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
                holder.card.setCardElevation(6f);
                holder.card.setCardBackgroundColor(available ? COLOR_NORMAL : COLOR_LOCKED);
            }
        });

        holder.card.setOnClickListener(v -> {
            if (listener != null) listener.onGradeClick(grade);
        });
    }

    @Override
    public int getItemCount() {
        return grades.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        ImageView icon;
        TextView title, subtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_grade);
            icon = itemView.findViewById(R.id.iv_grade_icon);
            title = itemView.findViewById(R.id.tv_grade_title);
            subtitle = itemView.findViewById(R.id.tv_grade_subtitle);
        }
    }
}