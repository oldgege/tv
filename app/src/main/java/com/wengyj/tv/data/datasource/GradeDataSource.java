package com.wengyj.tv.data.datasource;

import com.wengyj.tv.R;
import com.wengyj.tv.data.model.Grade;

import java.util.ArrayList;
import java.util.List;

public class GradeDataSource {

    public static List<Grade> getAllGrades() {
        List<Grade> grades = new ArrayList<>();
        // 一年级上：已开放
        grades.add(new Grade(1, "一年级上", "拼音 · 识字 · 数学", R.drawable.ic_chapter_placeholder, true));
        // 一年级下：已开放
        grades.add(new Grade(2, "一年级下", "识字 · 课文 · 古诗", R.drawable.ic_chapter_placeholder, true));
        // 二年级上：未开放
        grades.add(new Grade(3, "二年级上", "即将上线", R.drawable.ic_chapter_placeholder, false));
        // 二年级下：未开放
        grades.add(new Grade(4, "二年级下", "即将上线", R.drawable.ic_chapter_placeholder, false));
        return grades;
    }
}