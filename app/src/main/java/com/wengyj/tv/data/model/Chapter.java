package com.wengyj.tv.data.model;

import java.util.List;

public class Chapter {
    private int id;
    private String title;
    private String subtitle;
    private int iconRes;
    private int levelCount;
    private List<Level> levels;

    public Chapter(int id, String title, String subtitle, int iconRes, int levelCount, List<Level> levels) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.iconRes = iconRes;
        this.levelCount = levelCount;
        this.levels = levels;
    }

    // Getter / Setter
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public int getIconRes() { return iconRes; }
    public void setIconRes(int iconRes) { this.iconRes = iconRes; }
    public int getLevelCount() { return levelCount; }
    public void setLevelCount(int levelCount) { this.levelCount = levelCount; }
    public List<Level> getLevels() { return levels; }
    public void setLevels(List<Level> levels) { this.levels = levels; }
}