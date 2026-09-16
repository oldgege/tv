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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.wengyj.tv.R;
import com.wengyj.tv.ui.chapter.ChapterFragment;
import com.wengyj.tv.ui.level.LevelFragment;
import com.wengyj.tv.ui.game.GameFragment;
import com.wengyj.tv.utils.MusicManager;
import com.wengyj.tv.utils.ProgressManager;

public class MainActivity extends AppCompatActivity {
    private FragmentManager fragmentManager;
    private VideoView videoView;
    private FrameLayout fragmentContainer;
    private TextView tvVersion;
    private Handler handler = new Handler();
    private ProgressManager progressManager;

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

    // ---------- 数字键监听器（游戏内使用） ----------
    private OnNumberKeyListener numberKeyListener;

    public interface OnNumberKeyListener {
        /**
         * @param number 按下的数字键（1~4）
         * @return true 表示已消费该按键，false 表示不拦截
         */
        boolean onNumberKeyPressed(int number);
    }

    public void setNumberKeyListener(OnNumberKeyListener listener) {
        this.numberKeyListener = listener;
    }

    public void clearNumberKeyListener() {
        this.numberKeyListener = null;
    }

    /**
     * 将 KeyEvent 的 keyCode 转为数字（1~4），不是数字键返回 -1
     */
    private int keyCodeToNumber(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_1:
            case KeyEvent.KEYCODE_NUMPAD_1:
                return 1;
            case KeyEvent.KEYCODE_2:
            case KeyEvent.KEYCODE_NUMPAD_2:
                return 2;
            case KeyEvent.KEYCODE_3:
            case KeyEvent.KEYCODE_NUMPAD_3:
                return 3;
            case KeyEvent.KEYCODE_4:
            case KeyEvent.KEYCODE_NUMPAD_4:
                return 4;
            default:
                return -1;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // 返回键不拦截
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                return super.dispatchKeyEvent(event);
            }

            // 数字键拦截（用于播报选项）
            if (numberKeyListener != null) {
                int num = keyCodeToNumber(event.getKeyCode());
                if (num > 0) {
                    if (numberKeyListener.onNumberKeyPressed(num)) {
                        return true;
                    }
                }
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
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
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
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreen();
        setContentView(R.layout.activity_main);

        progressManager = new ProgressManager(this);

        fragmentManager = getSupportFragmentManager();
        videoView = findViewById(R.id.video_view);
        fragmentContainer = findViewById(R.id.fragment_container);
        tvVersion = findViewById(R.id.tv_version);

        tvVersion.setText("v" + getVersionName());
        tvVersion.setVisibility(View.VISIBLE);

        playNextVideo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MusicManager.getInstance(this).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MusicManager.getInstance(this).pause();
    }

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
            handler.removeCallbacksAndMessages(null);

            int lastLevelId = progressManager.getLastLevelId();
            if (lastLevelId != -1) {
                fragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, new ChapterFragment())
                        .commitNow();
                showGameFragment(lastLevelId);
            } else {
                showChapterFragment();
            }
        });
    }

    private void showChapterFragment() {
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, new ChapterFragment())
                .commit();
    }

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
        if (fragmentManager.getBackStackEntryCount() > 1) {
            fragmentManager.popBackStack();
        } else if (fragmentManager.getBackStackEntryCount() == 1) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            fragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, new ChapterFragment())
                    .commit();
        } else {
            Fragment current = fragmentManager.findFragmentById(R.id.fragment_container);
            if (current instanceof ChapterFragment) {
                super.onBackPressed();
            } else {
                fragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, new ChapterFragment())
                        .commit();
            }
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