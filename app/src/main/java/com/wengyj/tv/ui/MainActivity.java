package com.wengyj.tv.ui;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.wengyj.tv.R;
import com.wengyj.tv.ui.chapter.ChapterFragment;
import com.wengyj.tv.ui.level.LevelFragment;
import com.wengyj.tv.ui.game.GameFragment;
import com.wengyj.tv.utils.MusicManager;

public class MainActivity extends AppCompatActivity {
    private FragmentManager fragmentManager;
    private VideoView videoView;
    private FrameLayout fragmentContainer;
    private TextView tvVersion;
    private Handler handler = new Handler();

    // 开场视频播放顺序：begin1 → begin2 → begin3 → begin4
    private int[] videoResources = {R.raw.begin1, R.raw.begin2, R.raw.begin3, R.raw.begin4};
    private int currentIndex = 0;

    private boolean isIntroPlaying = false;
    private boolean isWaitingForConfirm = false;

    private Runnable pauseAtEndRunnable;

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
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                return super.dispatchKeyEvent(event);
            }

            if (isWaitingForConfirm) {
                isWaitingForConfirm = false;
                finishIntro();
                return true;
            }

            if (videoKeyListener != null) {
                videoKeyListener.onAnyKeyPressed();
                return true;
            }

            if (isIntroPlaying) {
                skipIntro();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (isWaitingForConfirm && event.getAction() == MotionEvent.ACTION_DOWN) {
            isWaitingForConfirm = false;
            finishIntro();
            return true;
        }
        if (isIntroPlaying && event.getAction() == MotionEvent.ACTION_DOWN) {
            skipIntro();
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreen();
        setContentView(R.layout.activity_main);

        fragmentManager = getSupportFragmentManager();
        videoView = findViewById(R.id.video_view);
        fragmentContainer = findViewById(R.id.fragment_container);
        tvVersion = findViewById(R.id.tv_version);

        // 显示版本号
        tvVersion.setText("v" + getVersionName());
        tvVersion.setVisibility(View.VISIBLE);

        playNextVideo();
    }

    // ---------- 生命周期：处理背景音乐 ----------
    @Override
    protected void onResume() {
        super.onResume();
        // 回到前台：恢复背景音乐
        MusicManager.getInstance(this).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 退到后台：暂停背景音乐
        MusicManager.getInstance(this).pause();
    }
    // -----------------------------------------

    // 获取当前应用的版本号
    private String getVersionName() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return "1.0.0";
        }
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

        if (pauseAtEndRunnable != null) {
            handler.removeCallbacks(pauseAtEndRunnable);
            pauseAtEndRunnable = null;
        }

        isIntroPlaying = true;
        fragmentContainer.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);

        final boolean isLastVideo = (currentIndex == videoResources.length - 1);
        int resId = videoResources[currentIndex];
        String uriPath = "android.resource://" + getPackageName() + "/" + resId;
        videoView.setVideoURI(Uri.parse(uriPath));

        videoView.setOnPreparedListener(mp -> {
            mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            videoView.start();

            if (isLastVideo) {
                int duration = mp.getDuration();
                if (duration > 0) {
                    int delay = Math.max(duration - 300, 0);
                    pauseAtEndRunnable = () -> {
                        if (videoView.isPlaying()) {
                            videoView.pause();
                        }
                        isIntroPlaying = false;
                        isWaitingForConfirm = true;
                    };
                    handler.postDelayed(pauseAtEndRunnable, delay);
                }
            }
        });

        videoView.setOnCompletionListener(mp -> {
            if (!isLastVideo) {
                currentIndex++;
                playNextVideo();
            } else if (!isWaitingForConfirm) {
                isIntroPlaying = false;
                isWaitingForConfirm = true;
            }
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            currentIndex++;
            playNextVideo();
            return true;
        });
    }

    private void skipIntro() {
        isIntroPlaying = false;
        isWaitingForConfirm = false;
        currentIndex = videoResources.length;
        if (pauseAtEndRunnable != null) {
            handler.removeCallbacks(pauseAtEndRunnable);
            pauseAtEndRunnable = null;
        }
        finishIntro();
    }

    private void finishIntro() {
        isIntroPlaying = false;
        isWaitingForConfirm = false;
        runOnUiThread(() -> {
            videoView.setVisibility(View.GONE);
            videoView.stopPlayback();
            if (tvVersion != null) {
                tvVersion.setVisibility(View.GONE);
            }
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