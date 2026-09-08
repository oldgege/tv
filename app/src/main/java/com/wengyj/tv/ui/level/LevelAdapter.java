package com.wengyj.tv.ui.level;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    public interface OnLevelClickListener {
        void onLevelClick(Level level);
    }

    public LevelAdapter(List<Level> levels, OnLevelClickListener listener) {
        this.levels = levels;
        this.listener = listener;
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
            holder.card.setEnabled(false);
            holder.card.setAlpha(0.5f);
        } else {
            holder.ivLock.setVisibility(View.GONE);
            holder.card.setEnabled(true);
            holder.card.setAlpha(1.0f);
            if (level.isCompleted()) {
                holder.ivStar.setVisibility(View.VISIBLE);
            } else {
                holder.ivStar.setVisibility(View.GONE);
            }
        }

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