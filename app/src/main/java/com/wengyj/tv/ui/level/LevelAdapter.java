package com.wengyj.tv.ui.level;

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
import com.wengyj.tv.data.model.Level;

import java.util.List;

public class LevelAdapter extends RecyclerView.Adapter<LevelAdapter.ViewHolder> {
    private List<Level> levels;
    private OnLevelClickListener listener;

    private static final int COLOR_NORMAL = Color.parseColor("#444444");
    private static final int COLOR_FOCUSED = Color.parseColor("#03A9F4");

    public interface OnLevelClickListener {
        void onLevelClick(Level level);
    }

    public LevelAdapter(List<Level> levels, OnLevelClickListener listener) {
        this.levels = levels;
        this.listener = listener;
    }

    /** 数据无变化时（仅解锁/完成状态变化）调用，刷新全部可见项 */
    public void refreshAll() {
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_level, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Level level = levels.get(position);
        holder.tvLevelTitle.setText(level.getTitle());

        if (level.isLocked()) {
            holder.ivLock.setVisibility(View.VISIBLE);
            holder.ivStar.setVisibility(View.GONE);
            holder.card.setAlpha(0.5f);
        } else {
            holder.ivLock.setVisibility(View.GONE);
            holder.card.setAlpha(1.0f);
            if (level.isCompleted()) {
                holder.ivStar.setVisibility(View.VISIBLE);
            } else {
                holder.ivStar.setVisibility(View.GONE);
            }
        }

        holder.card.setCardBackgroundColor(COLOR_NORMAL);

        holder.card.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate()
                        .scaleX(1.15f)
                        .scaleY(1.15f)
                        .setDuration(200)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
                holder.card.setCardElevation(16f);
                holder.card.setCardBackgroundColor(COLOR_FOCUSED);
            } else {
                v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
                holder.card.setCardElevation(4f);
                holder.card.setCardBackgroundColor(COLOR_NORMAL);
            }
        });

        holder.card.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLevelClick(level);
            }
        });
    }

    @Override
    public int getItemCount() {
        return levels.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        TextView tvLevelTitle;
        ImageView ivLock, ivStar;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_level);
            tvLevelTitle = itemView.findViewById(R.id.tv_level_title);
            ivLock = itemView.findViewById(R.id.iv_lock);
            ivStar = itemView.findViewById(R.id.iv_star);
        }
    }
}