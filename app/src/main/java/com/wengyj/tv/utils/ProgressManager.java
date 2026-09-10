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
                result.add(Integer.parseInt(id));
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

    // ---------- 重置所有进度（星星和关卡） ----------
    public void resetAllProgress() {
        prefs.edit()
                .putString(KEY_COMPLETED, "")
                .putInt(KEY_STARS, 20)
                .putInt(KEY_CURRENT_CHAPTER, 1)
                .putInt(KEY_CURRENT_LEVEL, 1)
                .apply();
    }

    // ---------- 当前进度 ----------
    public void saveCurrentProgress(int chapterId, int levelId) {
        prefs.edit()
                .putInt(KEY_CURRENT_CHAPTER, chapterId)
                .putInt(KEY_CURRENT_LEVEL, levelId)
                .apply();
    }

    public int[] getCurrentProgress() {
        int chapter = prefs.getInt(KEY_CURRENT_CHAPTER, 1);
        int level = prefs.getInt(KEY_CURRENT_LEVEL, 1);
        return new int[]{chapter, level};
    }
}