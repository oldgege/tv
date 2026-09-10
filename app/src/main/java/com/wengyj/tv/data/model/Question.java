package com.wengyj.tv.data.model;

import java.util.List;

public class Question {
    public enum Type {
        CHOICE,         // 普通选择题
        MATCH,          // 配对（暂未实现）
        FILL_BLANK,     // 填空（暂未实现）
        TONE_SELECT,    // 声调选择（暂未实现）
        LISTEN_SELECT   // 听音选字（新增）
    }

    private Type type;
    private String prompt;
    private List<String> options;
    private int correctAnswerIndex;
    private String hint;
    private Integer imageRes;
    private String audioText;   // 新增：听力题要朗读的文本

    public Question(Type type, String prompt, List<String> options, int correctAnswerIndex, String hint, Integer imageRes) {
        this.type = type;
        this.prompt = prompt;
        this.options = options;
        this.correctAnswerIndex = correctAnswerIndex;
        this.hint = hint;
        this.imageRes = imageRes;
        this.audioText = null;
    }

    // 新构造函数，支持 audioText
    public Question(Type type, String prompt, List<String> options, int correctAnswerIndex, String hint, Integer imageRes, String audioText) {
        this.type = type;
        this.prompt = prompt;
        this.options = options;
        this.correctAnswerIndex = correctAnswerIndex;
        this.hint = hint;
        this.imageRes = imageRes;
        this.audioText = audioText;
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    public int getCorrectAnswerIndex() { return correctAnswerIndex; }
    public void setCorrectAnswerIndex(int correctAnswerIndex) { this.correctAnswerIndex = correctAnswerIndex; }
    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }
    public Integer getImageRes() { return imageRes; }
    public void setImageRes(Integer imageRes) { this.imageRes = imageRes; }
    public String getAudioText() { return audioText; }
    public void setAudioText(String audioText) { this.audioText = audioText; }
}