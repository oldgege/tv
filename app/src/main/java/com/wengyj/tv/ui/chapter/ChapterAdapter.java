package com.wengyj.tv.ui.chapter;

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
import com.wengyj.tv.data.model.Chapter;

import java.util.List;

public class ChapterAdapter extends RecyclerView.Adapter<ChapterAdapter.ViewHolder> {
    private List<Chapter> chapters;
    private OnChapterClickListener listener;

    // 默认背景色 & 焦点背景色（亮橙色）
    private static final int COLOR_NORMAL = Color.parseColor("#333333");
    private static final int COLOR_FOCUSED = Color.parseColor("#FF9800");

    public interface OnChapterClickListener {
        void onChapterClick(Chapter chapter);
    }

    public ChapterAdapter(List<Chapter> chapters, OnChapterClickListener listener) {
        this.chapters = chapters;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chapter, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Chapter chapter = chapters.get(position);
        holder.title.setText(chapter.getTitle());
        holder.subtitle.setText(chapter.getSubtitle());
        holder.icon.setImageResource(chapter.getIconRes());

        // 重置为默认背景色（防止复用错乱）
        holder.card.setCardBackgroundColor(COLOR_NORMAL);

        // 焦点变化：放大 + 高亮背景 + 阴影提升
        holder.card.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(200)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
                holder.card.setCardElevation(20f);
                holder.card.setCardBackgroundColor(COLOR_FOCUSED);  // 高亮
            } else {
                v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
                holder.card.setCardElevation(6f);
                holder.card.setCardBackgroundColor(COLOR_NORMAL);   // 恢复
            }
        });

        holder.card.setOnClickListener(v -> {
            if (listener != null) listener.onChapterClick(chapter);
        });
    }

    @Override
    public int getItemCount() {
        return chapters.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        ImageView icon;
        TextView title, subtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_chapter);
            icon = itemView.findViewById(R.id.iv_chapter_icon);
            title = itemView.findViewById(R.id.tv_chapter_title);
            subtitle = itemView.findViewById(R.id.tv_chapter_subtitle);
        }
    }
}