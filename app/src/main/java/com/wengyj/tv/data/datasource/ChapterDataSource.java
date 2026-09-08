package com.wengyj.tv.data.datasource;

import android.content.Context;

import com.wengyj.tv.R;
import com.wengyj.tv.data.model.Chapter;
import com.wengyj.tv.data.model.Level;
import com.wengyj.tv.data.model.Question;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChapterDataSource {

    public static List<Chapter> getAllChapters(Context context) {
        List<Chapter> chapters = new ArrayList<>();
        chapters.add(createChapter1());  // 我上学了
        chapters.add(createChapter2());  // 识字（一）
        chapters.add(createChapter3());  // 汉语拼音（一）
        chapters.add(createChapter4());  // 汉语拼音（二）
        chapters.add(createChapter5());  // 汉语拼音（三）
        chapters.add(createChapter6());  // 课文（一）
        chapters.add(createChapter7());  // 识字（二）
        chapters.add(createChapter8());  // 课文（二）
        chapters.add(createChapter9());  // 课文（三）
        return chapters;
    }

    // ---------- 通用方法 ----------
    private static Question createChoiceQuestion(String prompt, String correctAnswer, String[] wrongAnswers, String hint) {
        List<String> options = new ArrayList<>();
        options.add(correctAnswer);
        Collections.addAll(options, wrongAnswers);
        Collections.shuffle(options);
        int correctIndex = options.indexOf(correctAnswer);
        return new Question(Question.Type.CHOICE, prompt, options, correctIndex, hint, null);
    }

    private static List<String> generateWrongPinyin(String correct, String[] allPinyin, int excludeIndex) {
        List<String> wrong = new ArrayList<>();
        int count = 0;
        for (int i = 0; i < allPinyin.length && count < 3; i++) {
            if (i != excludeIndex && !allPinyin[i].equals(correct)) {
                wrong.add(allPinyin[i]);
                count++;
            }
        }
        while (wrong.size() < 3) {
            wrong.add("pinyin" + (wrong.size() + 1));
        }
        return wrong;
    }

    // ========== 第1章：我上学了 ==========
    private static Chapter createChapter1() {
        List<Level> levels = new ArrayList<>();
        String[] words = {"我", "国", "中", "学", "小", "生", "爱", "语", "文",
                "天", "地", "人", "你", "好", "老", "师", "同", "学", "们", "家"};
        String[] pinyin = {"wǒ", "guó", "zhōng", "xué", "xiǎo", "shēng", "ài", "yǔ", "wén",
                "tiān", "dì", "rén", "nǐ", "hǎo", "lǎo", "shī", "tóng", "xué", "men", "jiā"};
        for (int i = 0; i < 20; i++) {
            String word = words[i];
            String correct = pinyin[i];
            List<String> wrongs = generateWrongPinyin(correct, pinyin, i);
            String prompt = "请选择 \"" + word + "\" 的正确读音：";
            Question q = createChoiceQuestion(prompt, correct, wrongs.toArray(new String[0]), "想一想这个字怎么读");
            Level level = new Level(i + 1, 1, "第" + (i + 1) + "关：认识" + word, q, i != 0, false, 0);
            levels.add(level);
        }
        levels.get(0).setLocked(false);
        return new Chapter(1, "我上学了", "认识学校和生活", R.drawable.ic_chapter_placeholder, 20, levels);
    }

    // ========== 第2章：识字（一） ==========
    private static Chapter createChapter2() {
        List<Level> levels = new ArrayList<>();
        String[] words = {"天", "地", "人", "金", "木", "水", "火", "土", "口", "耳",
                "目", "手", "足", "日", "月", "山", "川", "上", "下", "大"};
        String[] pinyin = {"tiān", "dì", "rén", "jīn", "mù", "shuǐ", "huǒ", "tǔ", "kǒu", "ěr",
                "mù", "shǒu", "zú", "rì", "yuè", "shān", "chuān", "shàng", "xià", "dà"};
        for (int i = 0; i < 20; i++) {
            String word = words[i];
            String correct = pinyin[i];
            List<String> wrongs = generateWrongPinyin(correct, pinyin, i);
            String prompt = "请选择 \"" + word + "\" 的正确读音：";
            Question q = createChoiceQuestion(prompt, correct, wrongs.toArray(new String[0]), "想想这个字的拼音");
            Level level = new Level(i + 1, 2, "第" + (i + 1) + "关：认识" + word, q, i != 0, false, 0);
            levels.add(level);
        }
        levels.get(0).setLocked(false);
        return new Chapter(2, "识字（一）", "天地人，金木水火土", R.drawable.ic_chapter_placeholder, 20, levels);
    }

    // ========== 第3章：汉语拼音（一） ==========
    private static Chapter createChapter3() {
        List<Level> levels = new ArrayList<>();
        // 单韵母和声母
        String[] letters = {"a", "o", "e", "i", "u", "ü", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x"};
        String[] names = {"啊", "哦", "鹅", "衣", "乌", "迂", "播", "泼", "摸", "佛", "得", "特", "讷", "勒", "哥", "科", "喝", "鸡", "欺", "希"};
        for (int i = 0; i < 20; i++) {
            String letter = letters[i];
            String name = names[i];
            String prompt = "请选择字母 \"" + letter + "\" 的正确读音：";
            // 构造错误选项：用其他字母的读音
            List<String> wrongs = new ArrayList<>();
            for (int j = 0; j < names.length; j++) {
                if (j != i && wrongs.size() < 3) {
                    wrongs.add(names[j]);
                }
            }
            while (wrongs.size() < 3) wrongs.add("读音" + (wrongs.size() + 1));
            Question q = createChoiceQuestion(prompt, name, wrongs.toArray(new String[0]), "回忆字母的发音");
            Level level = new Level(i + 1, 3, "第" + (i + 1) + "关：学习" + letter, q, i != 0, false, 0);
            levels.add(level);
        }
        levels.get(0).setLocked(false);
        return new Chapter(3, "汉语拼音（一）", "a o e i u ü", R.drawable.ic_chapter_placeholder, 20, levels);
    }

    // ========== 第4章：汉语拼音（二） ==========
    private static Chapter createChapter4() {
        List<Level> levels = new ArrayList<>();
        String[] initials = {"zh", "ch", "sh", "r", "z", "c", "s", "y", "w", "ai", "ei", "ui", "ao", "ou", "iu", "ie", "üe", "er", "an", "en"};
        String[] examples = {"知", "吃", "诗", "日", "资", "此", "思", "衣", "乌", "爱", "诶", "威", "奥", "欧", "优", "也", "约", "儿", "安", "恩"};
        for (int i = 0; i < 20; i++) {
            String init = initials[i];
            String ex = examples[i];
            String prompt = "请选择拼音 \"" + init + "\" 对应的汉字（示例）：";
            List<String> wrongs = new ArrayList<>();
            for (int j = 0; j < examples.length; j++) {
                if (j != i && wrongs.size() < 3) {
                    wrongs.add(examples[j]);
                }
            }
            while (wrongs.size() < 3) wrongs.add("字" + (wrongs.size() + 1));
            Question q = createChoiceQuestion(prompt, ex, wrongs.toArray(new String[0]), "想想这个拼音读什么");
            Level level = new Level(i + 1, 4, "第" + (i + 1) + "关：拼音" + init, q, i != 0, false, 0);
            levels.add(level);
        }
        levels.get(0).setLocked(false);
        return new Chapter(4, "汉语拼音（二）", "zh ch sh r z c s", R.drawable.ic_chapter_placeholder, 20, levels);
    }

    // ========== 第5章：汉语拼音（三） ==========
    private static Chapter createChapter5() {
        List<Level> levels = new ArrayList<>();
        String[] finals = {"ang", "eng", "ing", "ong", "ia", "ua", "uo", "üe", "ian", "iang", "iong", "uang", "uai", "uan", "üan", "uen", "ün", "uang", "ueng", "ong"};
        String[] examples = {"昂", "亨", "英", "轰", "呀", "娃", "窝", "月", "烟", "央", "雍", "汪", "歪", "弯", "圆", "温", "晕", "汪", "翁", "轰"};
        for (int i = 0; i < 20; i++) {
            String fin = finals[i];
            String ex = examples[i];
            String prompt = "请选择拼音 \"" + fin + "\" 对应的汉字（示例）：";
            List<String> wrongs = new ArrayList<>();
            for (int j = 0; j < examples.length; j++) {
                if (j != i && wrongs.size() < 3) {
                    wrongs.add(examples[j]);
                }
            }
            while (wrongs.size() < 3) wrongs.add("字" + (wrongs.size() + 1));
            Question q = createChoiceQuestion(prompt, ex, wrongs.toArray(new String[0]), "注意韵母的发音");
            Level level = new Level(i + 1, 5, "第" + (i + 1) + "关：韵母" + fin, q, i != 0, false, 0);
            levels.add(level);
        }
        levels.get(0).setLocked(false);
        return new Chapter(5, "汉语拼音（三）", "ang eng ing ong", R.drawable.ic_chapter_placeholder, 20, levels);
    }

    // ========== 第6章：课文（一）——秋天、江南、雪地里的小画家、四季 ==========
    private static Chapter createChapter6() {
        List<Level> levels = new ArrayList<>();
        // 课文中的生字和词语
        String[] words = {"秋", "天", "气", "了", "树", "叶", "片", "大", "飞", "落",
                "江", "南", "可", "采", "莲", "鱼", "戏", "东", "西", "北"};
        String[] pinyin = {"qiū", "tiān", "qì", "le", "shù", "yè", "piàn", "dà", "fēi", "luò",
                "jiāng", "nán", "kě", "cǎi", "lián", "yú", "xì", "dōng", "xī", "běi"};
        for (int i = 0; i < 20; i++) {
            String word = words[i];
            String correct = pinyin[i];
            List<String> wrongs = generateWrongPinyin(correct, pinyin, i);
            String prompt = "请选择 \"" + word + "\" 的正确读音：";
            Question q = createChoiceQuestion(prompt, correct, wrongs.toArray(new String[0]), "回忆课文中的字");
            Level level = new Level(i + 1, 6, "第" + (i + 1) + "关：课文生字" + word, q, i != 0, false, 0);
            levels.add(level);
        }
        levels.get(0).setLocked(false);
        return new Chapter(6, "课文（一）", "秋天·江南·雪地·四季", R.drawable.ic_chapter_placeholder, 20, levels);
    }

    // ========== 第7章：识字（二）——对韵歌、日月明、小书包、升国旗 ==========
    private static Chapter createChapter7() {
        List<Level> levels = new ArrayList<>();
        String[] words = {"对", "韵", "歌", "日", "月", "明", "小", "书", "包", "升",
                "国", "旗", "红", "星", "歌", "曲", "敬", "礼", "立", "正"};
        String[] pinyin = {"duì", "yùn", "gē", "rì", "yuè", "míng", "xiǎo", "shū", "bāo", "shēng",
                "guó", "qí", "hóng", "xīng", "gē", "qǔ", "jìng", "lǐ", "lì", "zhèng"};
        for (int i = 0; i < 20; i++) {
            String word = words[i];
            String correct = pinyin[i];
            List<String> wrongs = generateWrongPinyin(correct, pinyin, i);
            String prompt = "请选择 \"" + word + "\" 的正确读音：";
            Question q = createChoiceQuestion(prompt, correct, wrongs.toArray(new String[0]), "想想这个字的拼音");
            Level level = new Level(i + 1, 7, "第" + (i + 1) + "关：识字" + word, q, i != 0, false, 0);
            levels.add(level);
        }
        levels.get(0).setLocked(false);
        return new Chapter(7, "识字（二）", "对韵歌·日月明·小书包·升国旗", R.drawable.ic_chapter_placeholder, 20, levels);
    }

    // ========== 第8章：课文（二）——小小的船、影子、两件宝 ==========
    private static Chapter createChapter8() {
        List<Level> levels = new ArrayList<>();
        String[] words = {"小", "小", "的", "船", "两", "头", "尖", "我", "坐", "在",
                "影", "子", "前", "后", "左", "右", "黑", "狗", "朋", "友"};
        String[] pinyin = {"xiǎo", "xiǎo", "de", "chuán", "liǎng", "tóu", "jiān", "wǒ", "zuò", "zài",
                "yǐng", "zi", "qián", "hòu", "zuǒ", "yòu", "hēi", "gǒu", "péng", "yǒu"};
        for (int i = 0; i < 20; i++) {
            String word = words[i];
            String correct = pinyin[i];
            List<String> wrongs = generateWrongPinyin(correct, pinyin, i);
            String prompt = "请选择 \"" + word + "\" 的正确读音：";
            Question q = createChoiceQuestion(prompt, correct, wrongs.toArray(new String[0]), "回忆课文中的字");
            Level level = new Level(i + 1, 8, "第" + (i + 1) + "关：课文生字" + word, q, i != 0, false, 0);
            levels.add(level);
        }
        levels.get(0).setLocked(false);
        return new Chapter(8, "课文（二）", "小小的船·影子·两件宝", R.drawable.ic_chapter_placeholder, 20, levels);
    }

    // ========== 第9章：课文（三）——比尾巴、乌鸦喝水、雨点儿 ==========
    private static Chapter createChapter9() {
        List<Level> levels = new ArrayList<>();
        String[] words = {"比", "尾", "巴", "乌", "鸦", "喝", "水", "雨", "点", "儿",
                "找", "瓶", "石", "子", "渐", "渐", "出", "现", "花", "草"};
        String[] pinyin = {"bǐ", "wěi", "ba", "wū", "yā", "hē", "shuǐ", "yǔ", "diǎn", "ér",
                "zhǎo", "píng", "shí", "zi", "jiàn", "jiàn", "chū", "xiàn", "huā", "cǎo"};
        for (int i = 0; i < 20; i++) {
            String word = words[i];
            String correct = pinyin[i];
            List<String> wrongs = generateWrongPinyin(correct, pinyin, i);
            String prompt = "请选择 \"" + word + "\" 的正确读音：";
            Question q = createChoiceQuestion(prompt, correct, wrongs.toArray(new String[0]), "想想这个字的拼音");
            Level level = new Level(i + 1, 9, "第" + (i + 1) + "关：课文生字" + word, q, i != 0, false, 0);
            levels.add(level);
        }
        levels.get(0).setLocked(false);
        return new Chapter(9, "课文（三）", "比尾巴·乌鸦喝水·雨点儿", R.drawable.ic_chapter_placeholder, 20, levels);
    }
}