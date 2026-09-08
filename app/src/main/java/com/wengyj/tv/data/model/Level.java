package com.wengyj.tv.data.model;

public class Level {
    private int id;
    private int chapterId;
    private String title;
    private Question question;
    private boolean locked;
    private boolean completed;
    private int stars; // 0-3

    public Level(int id, int chapterId, String title, Question question, boolean locked, boolean completed, int stars) {
        this.id = id;
        this.chapterId = chapterId;
        this.title = title;
        this.question = question;
        this.locked = locked;
        this.completed = completed;
        this.stars = stars;
    }

    // Getter / Setter 省略
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getChapterId() { return chapterId; }
    public void setChapterId(int chapterId) { this.chapterId = chapterId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }
}