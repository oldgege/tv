package com.wengyj.tv.ui;

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.wengyj.tv.R;
import com.wengyj.tv.ui.chapter.ChapterFragment;
import com.wengyj.tv.ui.level.LevelFragment;
import com.wengyj.tv.ui.game.GameFragment;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private FragmentManager fragmentManager;
    private TextureView videoTexture;
    private FrameLayout fragmentContainer;
    private MediaPlayer mediaPlayer;
    private Handler handler = new Handler();
    private boolean isVideoComplete = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreen();
        setContentView(R.layout.activity_main);

        fragmentManager = getSupportFragmentManager();
        videoTexture = findViewById(R.id.video_texture);
        fragmentContainer = findViewById(R.id.fragment_container);

        fragmentContainer.setFocusable(true);
        fragmentContainer.setFocusableInTouchMode(true);
        fragmentContainer.setClickable(true);
        fragmentContainer.requestFocus();

        startIntroVideo();
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

    private void startIntroVideo() {
        fragmentContainer.setVisibility(View.GONE);
        videoTexture.setVisibility(View.VISIBLE);

        videoTexture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                Surface surface = new Surface(surfaceTexture);
                mediaPlayer = new MediaPlayer();
                try {
                    Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.begin);
                    mediaPlayer.setDataSource(getApplicationContext(), videoUri);
                    mediaPlayer.setSurface(surface);
                    mediaPlayer.prepareAsync();

                    mediaPlayer.setOnPreparedListener(mp -> {
                        mediaPlayer.start();
                        setVideoCenter(width, height);
                    });

                    mediaPlayer.setOnCompletionListener(mp -> {
                        isVideoComplete = true;
                        finishIntro();
                    });

                    mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                        isVideoComplete = true;
                        finishIntro();
                        return true;
                    });

                } catch (IOException e) {
                    e.printStackTrace();
                    isVideoComplete = true;
                    finishIntro();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
                setVideoCenter(width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                if (mediaPlayer != null) {
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}
        });
    }

    private void setVideoCenter(int viewWidth, int viewHeight) {
        if (mediaPlayer == null || videoTexture == null) return;
        int videoWidth = mediaPlayer.getVideoWidth();
        int videoHeight = mediaPlayer.getVideoHeight();
        if (videoWidth <= 0 || videoHeight <= 0) return;

        float scaleX = (float) viewWidth / videoWidth;
        float scaleY = (float) viewHeight / videoHeight;
        float scale = Math.min(scaleX, scaleY);

        int scaledWidth = (int) (videoWidth * scale);
        int scaledHeight = (int) (videoHeight * scale);
        int offsetX = (viewWidth - scaledWidth) / 2;
        int offsetY = (viewHeight - scaledHeight) / 2;

        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(offsetX, offsetY);
        videoTexture.setTransform(matrix);
    }

    private void finishIntro() {
        if (fragmentContainer.getVisibility() == View.VISIBLE) return;
        runOnUiThread(() -> {
            videoTexture.setVisibility(View.GONE);
            fragmentContainer.setVisibility(View.VISIBLE);
            fragmentContainer.requestFocus();
            showChapterFragment();

            handler.post(() -> {
                if (mediaPlayer != null) {
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
            });
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
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}