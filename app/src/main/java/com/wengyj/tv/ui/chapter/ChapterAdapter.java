package com.wengyj.tv.ui.chapter;

import android.graphics.Color;
import android.os.Build;
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
import java.util.Set;

public class ChapterAdapter extends RecyclerView.Adapter<ChapterAdapter.ViewHolder> {
    private List<Chapter> chapters;
    private Set<Integer> unlockedChapterIds;
    private OnChapterClickListener listener;

    private static final int COLOR_NORMAL = Color.parseColor("#333333");
    private static final int COLOR_FOCUSED = Color.parseColor("#FF9800");
    private static final int COLOR_LOCKED = Color.parseColor("#222222");

    public interface OnChapterClickListener {
        void onChapterClick(Chapter chapter, boolean isLocked);
    }

    public ChapterAdapter(List<Chapter> chapters, Set<Integer> unlockedChapterIds,
                          OnChapterClickListener listener) {
        this.chapters = chapters;
        this.unlockedChapterIds = unlockedChapterIds;
        this.listener = listener;
    }

    public void updateUnlockedChapters(Set<Integer> unlockedChapterIds) {
        this.unlockedChapterIds = unlockedChapterIds;
        notifyDataSetChanged();
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
        boolean isLocked = !unlockedChapterIds.contains(chapter.getId());

        holder.title.setText(chapter.getTitle());
        holder.subtitle.setText(chapter.getSubtitle());
        holder.icon.setImageResource(chapter.getIconRes());

        holder.card.setCardBackgroundColor(isLocked ? COLOR_LOCKED : COLOR_NORMAL);
        holder.card.setAlpha(isLocked ? 0.5f : 1.0f);
        holder.ivLock.setVisibility(isLocked ? View.VISIBLE : View.GONE);

        holder.card.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(200)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
                // API 21+ 才能使用 elevation 阴影；API 18 用背景色变化代替
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    holder.card.setCardElevation(20f);
                }
                if (!isLocked) {
                    holder.card.setCardBackgroundColor(COLOR_FOCUSED);
                }
            } else {
                v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    holder.card.setCardElevation(6f);
                }
                holder.card.setCardBackgroundColor(isLocked ? COLOR_LOCKED : COLOR_NORMAL);
            }
        });

        holder.card.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChapterClick(chapter, isLocked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return chapters.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        ImageView icon;
        ImageView ivLock;
        TextView title, subtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_chapter);
            icon = itemView.findViewById(R.id.iv_chapter_icon);
            ivLock = itemView.findViewById(R.id.iv_chapter_lock);
            title = itemView.findViewById(R.id.tv_chapter_title);
            subtitle = itemView.findViewById(R.id.tv_chapter_subtitle);
        }
    }
}