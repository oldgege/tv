package com.wengyj.tv.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.Set;

public class ProgressManager {
    private static final String PREFS_NAME = "chinese_game_progress";
    private static final String KEY_COMPLETED = "completed_levels";
    private static final String KEY_CURRENT_CHAPTER = "current_chapter";
    private static final String KEY_CURRENT_LEVEL = "current_level";
    private static final String KEY_STARS = "stars";
    private static final String KEY_BGM_ENABLED = "bgm_enabled";
    private static final String KEY_HAS_PROGRESS = "has_progress";

    private SharedPreferences prefs;

    public ProgressManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ---------- BGM 开关 ----------
    public boolean isBgmEnabled() {
        return prefs.getBoolean(KEY_BGM_ENABLED, true);
    }

    public void setBgmEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BGM_ENABLED, enabled).apply();
    }

    // ---------- 星星管理 ----------
    public int getStars() {
        return prefs.getInt(KEY_STARS, 20);
    }

    public void addStar() {
        int current = getStars();
        prefs.edit().putInt(KEY_STARS, current + 1).apply();
    }

    public void deductStars(int count) {
        int current = getStars();
        int newVal = current - count;
        if (newVal < 0) newVal = 0;
        prefs.edit().putInt(KEY_STARS, newVal).apply();
    }

    public void resetStars() {
        prefs.edit().putInt(KEY_STARS, 20).apply();
    }

    // ---------- 关卡进度 ----------
    public void saveCompletedLevel(int levelId) {
        Set<String> set = getCompletedLevelSet();
        set.add(String.valueOf(levelId));
        prefs.edit().putString(KEY_COMPLETED, TextUtils.join(",", set)).apply();
    }

    public Set<Integer> getCompletedLevels() {
        Set<Integer> result = new HashSet<>();
        String str = prefs.getString(KEY_COMPLETED, "");
        if (!str.isEmpty()) {
            for (String id : str.split(",")) {
                try {
                    result.add(Integer.parseInt(id));
                } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    private Set<String> getCompletedLevelSet() {
        String str = prefs.getString(KEY_COMPLETED, "");
        Set<String> set = new HashSet<>();
        if (!str.isEmpty()) {
            for (String id : str.split(",")) {
                set.add(id);
            }
        }
        return set;
    }

    public boolean isLevelCompleted(int levelId) {
        return getCompletedLevels().contains(levelId);
    }

    // ---------- 是否已有游戏进度 ----------
    public boolean hasProgress() {
        return prefs.getBoolean(KEY_HAS_PROGRESS, false);
    }

    // ---------- 重置单个章节的进度 ----------
    /**
     * 只清除指定章节的所有关卡完成记录，并恢复星星为 20。
     * 其他章节的进度保持不变。
     *
     * @param chapterId 要重置的章节 ID
     */
    public void resetChapterProgress(int chapterId) {
        Set<Integer> completed = getCompletedLevels();
        Set<String> newSet = new HashSet<>();

        for (Integer levelId : completed) {
            // 关卡 ID 格式：chapterId * 100 + levelIndex
            int belongChapter = levelId / 100;
            if (belongChapter != chapterId) {
                newSet.add(String.valueOf(levelId));
            }
        }

        prefs.edit()
                .putString(KEY_COMPLETED, TextUtils.join(",", newSet))
                .putInt(KEY_STARS, 20)
                .putBoolean(KEY_HAS_PROGRESS, false)
                .apply();
    }

    // ---------- 重置所有进度（保留旧接口，谨慎使用） ----------
    public void resetAllProgress() {
        prefs.edit()
                .putString(KEY_COMPLETED, "")
                .putInt(KEY_STARS, 20)
                .putInt(KEY_CURRENT_CHAPTER, 1)
                .putInt(KEY_CURRENT_LEVEL, 1)
                .putBoolean(KEY_HAS_PROGRESS, false)
                .apply();
    }

    // ---------- 当前进度 ----------
    public void saveCurrentProgress(int chapterId, int levelId) {
        prefs.edit()
                .putInt(KEY_CURRENT_CHAPTER, chapterId)
                .putInt(KEY_CURRENT_LEVEL, levelId)
                .putBoolean(KEY_HAS_PROGRESS, true)
                .apply();
    }

    public int getLastLevelId() {
        if (!hasProgress()) return -1;
        int chapter = prefs.getInt(KEY_CURRENT_CHAPTER, -1);
        int level = prefs.getInt(KEY_CURRENT_LEVEL, -1);
        if (chapter == -1 || level == -1) return -1;
        return level;
    }

    public int[] getCurrentProgress() {
        int chapter = prefs.getInt(KEY_CURRENT_CHAPTER, 1);
        int level = prefs.getInt(KEY_CURRENT_LEVEL, 1);
        return new int[]{chapter, level};
    }
}