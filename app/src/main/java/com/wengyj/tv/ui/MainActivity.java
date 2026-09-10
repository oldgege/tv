package com.wengyj.tv.ui;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.wengyj.tv.R;
import com.wengyj.tv.ui.chapter.ChapterFragment;
import com.wengyj.tv.ui.level.LevelFragment;
import com.wengyj.tv.ui.game.GameFragment;

public class MainActivity extends AppCompatActivity {
    private FragmentManager fragmentManager;
    private VideoView videoView;
    private FrameLayout fragmentContainer;
    private Handler handler = new Handler();

    private int[] videoResources = {R.raw.begin2, R.raw.begin};
    private int currentIndex = 0;

    // 标记是否处于开场视频阶段
    private boolean isIntroPlaying = false;

    // ---------- 视频按键监听器（游戏内使用） ----------
    private OnVideoKeyListener videoKeyListener;

    public interface OnVideoKeyListener {
        void onAnyKeyPressed();
    }

    public void setVideoKeyListener(OnVideoKeyListener listener) {
        this.videoKeyListener = listener;
    }

    public void clearVideoKeyListener() {
        this.videoKeyListener = null;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // 返回键不拦截
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                return super.dispatchKeyEvent(event);
            }

            // 游戏内视频播放阶段：拦截任意按键跳过
            if (videoKeyListener != null) {
                videoKeyListener.onAnyKeyPressed();
                return true;
            }

            // 开场视频阶段：拦截任意按键直接进入菜单
            if (isIntroPlaying) {
                skipIntro();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    // ---------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreen();
        setContentView(R.layout.activity_main);

        fragmentManager = getSupportFragmentManager();
        videoView = findViewById(R.id.video_view);
        fragmentContainer = findViewById(R.id.fragment_container);

        playNextVideo();
    }

    private void setFullScreen() {
        View decorView = getWindow().getDecorView();
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        decorView.setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        }
    }

    private void playNextVideo() {
        if (currentIndex >= videoResources.length) {
            finishIntro();
            return;
        }

        isIntroPlaying = true;
        fragmentContainer.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);

        int resId = videoResources[currentIndex];
        String uriPath = "android.resource://" + getPackageName() + "/" + resId;
        videoView.setVideoURI(Uri.parse(uriPath));

        videoView.setOnPreparedListener(mp -> {
            mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            videoView.start();
        });

        videoView.setOnCompletionListener(mp -> {
            currentIndex++;
            playNextVideo();
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            currentIndex++;
            playNextVideo();
            return true;
        });

        handler.postDelayed(() -> {
            if (!videoView.isPlaying()) {
                currentIndex++;
                playNextVideo();
            }
        }, 5000);
    }

    // 跳过开场视频，直接进入菜单
    private void skipIntro() {
        isIntroPlaying = false;
        currentIndex = videoResources.length; // 标记所有视频已跳过
        finishIntro();
    }

    private void finishIntro() {
        isIntroPlaying = false;
        runOnUiThread(() -> {
            videoView.setVisibility(View.GONE);
            videoView.stopPlayback();
            fragmentContainer.setVisibility(View.VISIBLE);
            showChapterFragment();
            handler.removeCallbacksAndMessages(null);
        });
    }

    private void showChapterFragment() {
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, new ChapterFragment())
                .commit();
    }

    // ---------- 导航方法 ----------
    public void showLevelFragment(int chapterId) {
        LevelFragment fragment = LevelFragment.newInstance(chapterId);
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    public void showGameFragment(int levelId) {
        GameFragment fragment = GameFragment.newInstance(levelId);
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(GAME_BACK_STACK_TAG)
                .commit();
    }

    public void navigateToGame(int levelId) {
        fragmentManager.popBackStackImmediate(GAME_BACK_STACK_TAG,
                FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, GameFragment.newInstance(levelId))
                .addToBackStack(GAME_BACK_STACK_TAG)
                .commit();
    }

    public void navigateToChapter() {
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, new ChapterFragment())
                .commit();
    }

    private static final String GAME_BACK_STACK_TAG = "game";

    @Override
    public void onBackPressed() {
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (videoView != null) {
            videoView.stopPlayback();
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}