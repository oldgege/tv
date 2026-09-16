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
import com.wengyj.tv.ui.grade.GradeFragment;
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

    // 是否需要在 onResume 后补执行 finishIntro
    private boolean pendingFinishIntro = false;

    private Runnable pauseAtEndRunnable;

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

    private OnNumberKeyListener numberKeyListener;

    public interface OnNumberKeyListener {
        boolean onNumberKeyPressed(int number);
    }

    public void setNumberKeyListener(OnNumberKeyListener listener) {
        this.numberKeyListener = listener;
    }

    public void clearNumberKeyListener() {
        this.numberKeyListener = null;
    }

    private int keyCodeToNumber(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_1:
            case KeyEvent.KEYCODE_NUMPAD_1: return 1;
            case KeyEvent.KEYCODE_2:
            case KeyEvent.KEYCODE_NUMPAD_2: return 2;
            case KeyEvent.KEYCODE_3:
            case KeyEvent.KEYCODE_NUMPAD_3: return 3;
            case KeyEvent.KEYCODE_4:
            case KeyEvent.KEYCODE_NUMPAD_4: return 4;
            default: return -1;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                return super.dispatchKeyEvent(event);
            }

            if (numberKeyListener != null) {
                int num = keyCodeToNumber(event.getKeyCode());
                if (num > 0) {
                    if (numberKeyListener.onNumberKeyPressed(num)) return true;
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

        // 处理延迟的 finishIntro（状态已恢复）
        if (pendingFinishIntro) {
            pendingFinishIntro = false;
            doFinishIntro();
        }
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
                        if (videoView.isPlaying()) videoView.pause();
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

    /**
     * finishIntro 入口：判断状态是否已保存
     * 若已保存（即将重建），延迟到 onResume 后再执行
     */
    private void finishIntro() {
        isIntroPlaying = false;
        isWaitingForConfirm = false;

        if (isFinishing() || isDestroyed()) return;

        // 关键：状态已保存时不能 commit，延迟到 onResume
        if (fragmentManager.isStateSaved()) {
            pendingFinishIntro = true;
            return;
        }

        doFinishIntro();
    }

    /**
     * 真正执行界面切换
     */
    private void doFinishIntro() {
        if (isFinishing() || isDestroyed()) return;

        // 再次安全检查
        if (fragmentManager.isStateSaved()) {
            pendingFinishIntro = true;
            return;
        }

        videoView.setVisibility(View.GONE);
        videoView.stopPlayback();
        if (tvVersion != null) tvVersion.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);
        handler.removeCallbacksAndMessages(null);

        int lastLevelId = progressManager.getLastLevelId();
        if (lastLevelId != -1) {
            // 先建立年级根（同步），再压入游戏
            if (!fragmentManager.isStateSaved()) {
                fragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, new GradeFragment())
                        .commitNowAllowingStateLoss();
            }
            showGameFragment(lastLevelId);
        } else {
            showGradeFragment();
        }
    }

    public void showGradeFragment() {
        if (isFinishing() || isDestroyed()) return;
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, new GradeFragment())
                .commitAllowingStateLoss();
    }

    public void showChapterFragment(int gradeId) {
        if (isFinishing() || isDestroyed()) return;
        ChapterFragment fragment = ChapterFragment.newInstance(gradeId);
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commitAllowingStateLoss();
    }

    public void showLevelFragment(int chapterId) {
        if (isFinishing() || isDestroyed()) return;
        LevelFragment fragment = LevelFragment.newInstance(chapterId);
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commitAllowingStateLoss();
    }

    public void showGameFragment(int levelId) {
        if (isFinishing() || isDestroyed()) return;
        GameFragment fragment = GameFragment.newInstance(levelId);
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(GAME_BACK_STACK_TAG)
                .commitAllowingStateLoss();
    }

    public void navigateToGame(int levelId) {
        if (isFinishing() || isDestroyed()) return;
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, GameFragment.newInstance(levelId))
                .addToBackStack(GAME_BACK_STACK_TAG)
                .commitAllowingStateLoss();
    }

    public void navigateToGrade() {
        if (isFinishing() || isDestroyed()) return;
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, new GradeFragment())
                .commitAllowingStateLoss();
    }

    public void navigateToChapter() {
        navigateToGrade();
    }

    private static final String GAME_BACK_STACK_TAG = "game";

    @Override
    public void onBackPressed() {
        if (isFinishing() || isDestroyed()) return;

        int count = fragmentManager.getBackStackEntryCount();
        if (count > 0) {
            fragmentManager.popBackStack();
        } else {
            Fragment current = fragmentManager.findFragmentById(R.id.fragment_container);
            if (current instanceof GradeFragment) {
                super.onBackPressed();
            } else {
                fragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, new GradeFragment())
                        .commitAllowingStateLoss();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (videoView != null) {
            videoView.stopPlayback();
        }
        handler.removeCallbacksAndMessages(null);

        // 仅当 Activity 真正结束时释放 BGM
        if (isFinishing()) {
            MusicManager.getInstance(this).release();
        }

        super.onDestroy();
    }
}