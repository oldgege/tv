package com.wengyj.tv.data.model;

public class Grade {
    private int id;
    private String title;      // 如 "一年级上"
    private String subtitle;   // 如 "拼音识字"
    private int iconRes;
    private boolean available; // 是否已开放

    public Grade(int id, String title, String subtitle, int iconRes, boolean available) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.iconRes = iconRes;
        this.available = available;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public int getIconRes() { return iconRes; }
    public boolean isAvailable() { return available; }
}