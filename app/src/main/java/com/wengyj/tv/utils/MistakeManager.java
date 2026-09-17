package com.wengyj.tv.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * 错题本管理器（独立于 ProgressManager）。
 *
 * <p>存储：SharedPreferences + JSON 字符串。
 * <p>键：levelId（同一 levelId 反复答错只更新计数）。
 * <p>移除条件：连续答对 {@link #MASTER_THRESHOLD} 次。
 */
public class MistakeManager {

    private static final String TAG = "MistakeManager";
    private static final String PREFS = "mistake_book";
    private static final String KEY_ITEMS = "items";

    /** 连续答对多少次算"掌握"（移出错题本） */
    private static final int MASTER_THRESHOLD = 2;

    // ========== 数据模型 ==========

    public static class Mistake {
        public int levelId;
        public int chapterId;
        public String prompt;         // 题干快照
        public String correctAnswer;  // 正确答案文本快照
        public int wrongCount;        // 累计答错次数
        public int correctStreak;     // 连续答对次数（达到 MASTER_THRESHOLD 移除）
        public long lastWrongAt;      // 最近一次答错时间戳

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("levelId", levelId);
            o.put("chapterId", chapterId);
            o.put("prompt", prompt != null ? prompt : "");
            o.put("correctAnswer", correctAnswer != null ? correctAnswer : "");
            o.put("wrongCount", wrongCount);
            o.put("correctStreak", correctStreak);
            o.put("lastWrongAt", lastWrongAt);
            return o;
        }

        public static Mistake fromJson(JSONObject o) {
            Mistake m = new Mistake();
            m.levelId = o.optInt("levelId");
            m.chapterId = o.optInt("chapterId");
            m.prompt = o.optString("prompt", "");
            m.correctAnswer = o.optString("correctAnswer", "");
            m.wrongCount = o.optInt("wrongCount", 1);
            m.correctStreak = o.optInt("correctStreak", 0);
            m.lastWrongAt = o.optLong("lastWrongAt", 0);
            return m;
        }
    }

    // ========== 实例 ==========

    private final SharedPreferences prefs;

    public MistakeManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 全部错题，按最近答错时间倒序 */
    public synchronized List<Mistake> getAll() {
        JSONObject root = loadRoot();
        List<Mistake> result = new ArrayList<>();
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            JSONObject o = root.optJSONObject(keys.next());
            if (o != null) result.add(Mistake.fromJson(o));
        }
        Collections.sort(result, new Comparator<Mistake>() {
            @Override
            public int compare(Mistake a, Mistake b) {
                return Long.compare(b.lastWrongAt, a.lastWrongAt);
            }
        });
        return result;
    }

    public synchronized int getCount() {
        return loadRoot().length();
    }

    public synchronized Mistake get(int levelId) {
        JSONObject o = loadRoot().optJSONObject(String.valueOf(levelId));
        return o == null ? null : Mistake.fromJson(o);
    }

    /**
     * 记录一次答错。已存在则累加 wrongCount 并清零 correctStreak。
     */
    public synchronized void markWrong(int levelId, int chapterId,
                                       String prompt, String correctAnswer) {
        JSONObject root = loadRoot();
        try {
            String key = String.valueOf(levelId);
            JSONObject existing = root.optJSONObject(key);
            Mistake m;
            if (existing != null) {
                m = Mistake.fromJson(existing);
                m.wrongCount += 1;
                m.correctStreak = 0;
                m.lastWrongAt = System.currentTimeMillis();
                // 题目可能被改过，刷新快照
                m.chapterId = chapterId;
                m.prompt = prompt;
                m.correctAnswer = correctAnswer;
            } else {
                m = new Mistake();
                m.levelId = levelId;
                m.chapterId = chapterId;
                m.prompt = prompt;
                m.correctAnswer = correctAnswer;
                m.wrongCount = 1;
                m.correctStreak = 0;
                m.lastWrongAt = System.currentTimeMillis();
            }
            root.put(key, m.toJson());
            saveRoot(root);
        } catch (JSONException e) {
            Log.w(TAG, "markWrong 失败: " + e.getMessage());
        }
    }

    /**
     * 记录一次答对。
     * @return true 表示连续答对达到阈值，已从错题本移除
     */
    public synchronized boolean markCorrect(int levelId) {
        JSONObject root = loadRoot();
        String key = String.valueOf(levelId);
        JSONObject o = root.optJSONObject(key);
        if (o == null) return false;

        Mistake m = Mistake.fromJson(o);
        m.correctStreak += 1;
        if (m.correctStreak >= MASTER_THRESHOLD) {
            root.remove(key);
            saveRoot(root);
            return true;
        }
        try {
            root.put(key, m.toJson());
            saveRoot(root);
        } catch (JSONException e) {
            Log.w(TAG, "markCorrect 失败: " + e.getMessage());
        }
        return false;
    }

    public synchronized void remove(int levelId) {
        JSONObject root = loadRoot();
        root.remove(String.valueOf(levelId));
        saveRoot(root);
    }

    /** 家长手动清空 */
    public synchronized void clearAll() {
        prefs.edit().remove(KEY_ITEMS).apply();
    }

    // ========== 内部 ==========

    private JSONObject loadRoot() {
        String s = prefs.getString(KEY_ITEMS, "");
        if (s == null || s.isEmpty()) return new JSONObject();
        try {
            return new JSONObject(s);
        } catch (JSONException e) {
            Log.w(TAG, "错题本数据损坏，已重置");
            return new JSONObject();
        }
    }

    private void saveRoot(JSONObject root) {
        prefs.edit().putString(KEY_ITEMS, root.toString()).apply();
    }
}