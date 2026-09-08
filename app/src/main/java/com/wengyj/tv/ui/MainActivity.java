package com.wengyj.tv.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.wengyj.tv.R;
import com.wengyj.tv.ui.chapter.ChapterFragment;
import com.wengyj.tv.ui.level.LevelFragment;
import com.wengyj.tv.ui.game.GameFragment;

public class MainActivity extends AppCompatActivity {
    private FragmentManager fragmentManager;
    private static final String GAME_BACK_STACK_TAG = "game";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fragmentManager = getSupportFragmentManager();

        if (savedInstanceState == null) {
            showChapterFragment();
        }
    }

    public void showChapterFragment() {
        fragmentManager.beginTransaction()
                .replace(R.id.container, new ChapterFragment())
                .commit();
    }

    public void showLevelFragment(int chapterId) {
        LevelFragment fragment = LevelFragment.newInstance(chapterId);
        fragmentManager.beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)   // 关卡列表加入回退栈，用于返回章节列表
                .commit();
    }

    public void showGameFragment(int levelId) {
        GameFragment fragment = GameFragment.newInstance(levelId);
        fragmentManager.beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(GAME_BACK_STACK_TAG)   // 游戏用独立标签
                .commit();
    }

    /**
     * 跳转到下一关（替换当前游戏，并重置回退栈中的游戏条目）
     */
    public void navigateToGame(int levelId) {
        // 移除之前所有 "game" 标签的回退条目（包括当前的游戏）
        fragmentManager.popBackStackImmediate(GAME_BACK_STACK_TAG,
                FragmentManager.POP_BACK_STACK_INCLUSIVE);
        // 添加新的游戏页面，并重新加入回退栈
        fragmentManager.beginTransaction()
                .replace(R.id.container, GameFragment.newInstance(levelId))
                .addToBackStack(GAME_BACK_STACK_TAG)
                .commit();
    }

    /**
     * 返回章节列表（全部通关后使用）
     */
    public void navigateToChapter() {
        // 清空所有回退栈，避免残留
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fragmentManager.beginTransaction()
                .replace(R.id.container, new ChapterFragment())
                .commit();
    }

    @Override
    public void onBackPressed() {
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();   // 正常回退
        } else {
            super.onBackPressed();
        }
    }
}