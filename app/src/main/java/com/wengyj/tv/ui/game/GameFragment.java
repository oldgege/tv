package com.wengyj.tv.ui.game;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.wengyj.tv.R;
import com.wengyj.tv.data.datasource.ChapterDataSource;
import com.wengyj.tv.data.model.Chapter;
import com.wengyj.tv.data.model.Level;
import com.wengyj.tv.data.model.Question;
import com.wengyj.tv.utils.ProgressManager;
import com.wengyj.tv.ui.MainActivity;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GameFragment extends Fragment implements TextToSpeech.OnInitListener {
    private static final String ARG_LEVEL_ID = "level_id";
    private int levelId;
    private Level currentLevel;
    private TextView tvQuestion, tvHint;
    private TextView tvStars;
    private LinearLayout optionsContainer;
    private VideoView videoSuccess;
    private VideoView videoError;
    private ProgressManager progressManager;

    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private Handler handler = new Handler();

    private boolean isAnimating = false;

    public static GameFragment newInstance(int levelId) {
        GameFragment fragment = new GameFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_LEVEL_ID, levelId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            levelId = getArguments().getInt(ARG_LEVEL_ID);
        }
        progressManager = new ProgressManager(getContext());
        tts = new TextToSpeech(getContext(), this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_game, container, false);
        tvQuestion = view.findViewById(R.id.tv_question);
        tvHint = view.findViewById(R.id.tv_hint);
        tvStars = view.findViewById(R.id.tv_stars);
        optionsContainer = view.findViewById(R.id.options_container);
        videoSuccess = view.findViewById(R.id.video_success);
        videoError = view.findViewById(R.id.video_error);

        updateStarsDisplay();

        optionsContainer.setOrientation(LinearLayout.HORIZONTAL);
        optionsContainer.setGravity(android.view.Gravity.CENTER);

        List<Chapter> chapters = ChapterDataSource.getAllChapters(getContext());
        for (Chapter chapter : chapters) {
            for (Level level : chapter.getLevels()) {
                if (level.getId() == levelId) {
                    currentLevel = level;
                    break;
                }
            }
            if (currentLevel != null) break;
        }

        if (currentLevel == null) {
            Toast.makeText(getContext(), "关卡数据错误", Toast.LENGTH_SHORT).show();
            return view;
        }

        Question q = currentLevel.getQuestion();
        tvQuestion.setText(q.getPrompt());
        tvHint.setText(q.getHint() != null ? q.getHint() : "");

        if (isTtsReady) {
            speakText(q.getPrompt());
        }

        buildOptions();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从视频或上一关返回时，重新聚焦到第一个选项
        if (optionsContainer != null) {
            optionsContainer.post(() -> {
                if (optionsContainer.getChildCount() > 0) {
                    optionsContainer.getChildAt(0).requestFocus();
                }
            });
        }
    }

    private void updateStarsDisplay() {
        if (tvStars != null) {
            int stars = progressManager.getStars();
            tvStars.setText(String.valueOf(stars));
        }
    }

    private void buildOptions() {
        optionsContainer.removeAllViews();
        Question q = currentLevel.getQuestion();
        List<String> options = q.getOptions();

        int[] monsterColors = {
                R.drawable.monster_body_1,
                R.drawable.monster_body_2,
                R.drawable.monster_body_3,
                R.drawable.monster_body_4
        };

        for (int i = 0; i < options.size(); i++) {
            View item = LayoutInflater.from(getContext()).inflate(R.layout.item_monster_option, optionsContainer, false);
            ImageView body = item.findViewById(R.id.iv_monster_body);
            TextView text = item.findViewById(R.id.tv_option_text);
            RelativeLayout root = (RelativeLayout) item;

            body.setImageResource(monsterColors[i % monsterColors.length]);
            text.setText(options.get(i));
            root.setBackgroundResource(R.drawable.bg_monster_selector);

            root.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    AnimatorSet pulseSet = new AnimatorSet();
                    ObjectAnimator scaleX = ObjectAnimator.ofFloat(v, "scaleX", 1.0f, 1.15f, 1.0f);
                    ObjectAnimator scaleY = ObjectAnimator.ofFloat(v, "scaleY", 1.0f, 1.15f, 1.0f);
                    scaleX.setRepeatCount(ObjectAnimator.INFINITE);
                    scaleY.setRepeatCount(ObjectAnimator.INFINITE);
                    scaleX.setDuration(800);
                    scaleY.setDuration(800);
                    scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
                    scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
                    pulseSet.playTogether(scaleX, scaleY);
                    pulseSet.start();
                    v.setTag(R.id.anim_tag, pulseSet);
                } else {
                    AnimatorSet anim = (AnimatorSet) v.getTag(R.id.anim_tag);
                    if (anim != null) {
                        anim.cancel();
                        v.setTag(R.id.anim_tag, null);
                    }
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(200)
                            .start();
                }
            });

            final int index = i;
            root.setOnClickListener(v -> {
                if (isAnimating) return;
                checkAnswer(index, root);
            });

            root.setFocusable(true);
            root.setFocusableInTouchMode(true);
            optionsContainer.addView(root);
        }

        // 延迟请求焦点，确保视图完成布局
        optionsContainer.post(() -> {
            if (optionsContainer.getChildCount() > 0) {
                optionsContainer.getChildAt(0).requestFocus();
            }
        });
    }

    // ---------- TTS ----------
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.CHINESE);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US);
            }
            tts.setSpeechRate(0.5f);
            tts.setPitch(1.1f);
            isTtsReady = true;

            if (currentLevel != null && tvQuestion != null) {
                speakText(currentLevel.getQuestion().getPrompt());
            }
        } else {
            isTtsReady = false;
            Toast.makeText(getContext(), "语音播报不可用", Toast.LENGTH_SHORT).show();
        }
    }

    private void speakText(String text) {
        if (tts != null && isTtsReady && text != null && !text.isEmpty()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    @Override
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (videoSuccess != null) {
            videoSuccess.stopPlayback();
        }
        if (videoError != null) {
            videoError.stopPlayback();
        }
        // 清除视频按键监听
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.clearVideoKeyListener();
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------- 下一关 ----------
    private int getNextLevelId() {
        List<Chapter> chapters = ChapterDataSource.getAllChapters(getContext());
        Chapter currentChapter = null;
        int currentIndex = -1;
        for (Chapter chapter : chapters) {
            List<Level> levels = chapter.getLevels();
            for (int i = 0; i < levels.size(); i++) {
                if (levels.get(i).getId() == levelId) {
                    currentChapter = chapter;
                    currentIndex = i;
                    break;
                }
            }
            if (currentChapter != null) break;
        }

        if (currentChapter == null) return -1;

        List<Level> levels = currentChapter.getLevels();
        if (currentIndex < levels.size() - 1) {
            return levels.get(currentIndex + 1).getId();
        } else {
            int chapterIndex = chapters.indexOf(currentChapter);
            if (chapterIndex < chapters.size() - 1) {
                Chapter nextChapter = chapters.get(chapterIndex + 1);
                if (nextChapter.getLevels() != null && !nextChapter.getLevels().isEmpty()) {
                    return nextChapter.getLevels().get(0).getId();
                }
            }
            return -1;
        }
    }

    // ---------- 通用视频播放方法（支持按键跳过） ----------
    private void playVideoAndWait(VideoView videoView, int rawResId, final Runnable onComplete) {
        final boolean[] completed = {false};

        // 安全的完成回调，防止重复触发
        final Runnable safeComplete = () -> {
            if (completed[0]) return;
            completed[0] = true;

            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.clearVideoKeyListener();
            }

            videoView.stopPlayback();
            videoView.setVisibility(View.GONE);

            if (onComplete != null) {
                onComplete.run();
            }
        };

        // 注册按键监听器（按任意键跳过）
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.setVideoKeyListener(safeComplete::run);
        }

        // 播放视频
        videoView.setVisibility(View.VISIBLE);
        String uriPath = "android.resource://" + getContext().getPackageName() + "/" + rawResId;
        videoView.setVideoURI(Uri.parse(uriPath));

        videoView.setOnPreparedListener(mp -> {
            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            videoView.start();
        });

        videoView.setOnCompletionListener(mp -> safeComplete.run());

        videoView.setOnErrorListener((mp, what, extra) -> {
            safeComplete.run();
            return true;
        });
    }

    // ---------- 重置所有进度并返回菜单 ----------
    private void resetAllProgressAndGoHome() {
        progressManager.resetAllProgress();
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.navigateToChapter();
            Toast.makeText(activity, "💫 星星用完了，重新开始吧！", Toast.LENGTH_LONG).show();
            speakText("星星用完了，重新开始吧");
        }
    }

    // ---------- 答题 ----------
    private void checkAnswer(int selectedIndex, RelativeLayout selectedRoot) {
        Question q = currentLevel.getQuestion();
        if (selectedIndex == q.getCorrectAnswerIndex()) {
            // 答对
            progressManager.addStar();
            progressManager.saveCompletedLevel(currentLevel.getId());
            updateStarsDisplay();

            isAnimating = true;
            playVideoAndWait(videoSuccess, R.raw.success, () -> {
                int nextId = getNextLevelId();
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null) {
                    if (nextId != -1) {
                        activity.navigateToGame(nextId);
                    } else {
                        activity.navigateToChapter();
                        speakText("恭喜你完成所有关卡！");
                    }
                }
                isAnimating = false;
            });
        } else {
            // 答错
            progressManager.deductStars(2);
            updateStarsDisplay();
            int remainingStars = progressManager.getStars();

            if (remainingStars <= 0) {
                resetAllProgressAndGoHome();
                return;
            }

            isAnimating = true;
            playVideoAndWait(videoError, R.raw.error, () -> {
                speakText(currentLevel.getQuestion().getPrompt());
                reshuffleOptions();
                isAnimating = false;
            });
        }
    }

    private void reshuffleOptions() {
        Question q = currentLevel.getQuestion();
        List<String> options = q.getOptions();
        String correctAnswer = options.get(q.getCorrectAnswerIndex());
        Collections.shuffle(options);
        int newCorrectIndex = options.indexOf(correctAnswer);
        q.setCorrectAnswerIndex(newCorrectIndex);
        buildOptions();
    }
}