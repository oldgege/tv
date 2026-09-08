package com.wengyj.tv.ui.game;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
    private LinearLayout optionsContainer;
    private ImageView ivFullscreenFail;
    private ImageView ivFullscreenSuccess;
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
        optionsContainer = view.findViewById(R.id.options_container);
        ivFullscreenFail = view.findViewById(R.id.iv_fullscreen_fail);
        ivFullscreenSuccess = view.findViewById(R.id.iv_fullscreen_success);

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

        if (optionsContainer.getChildCount() > 0) {
            optionsContainer.getChildAt(0).requestFocus();
        }
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
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
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

    // ---------- 失败全屏动画 ----------
    private void showFailAnimation(final Runnable onComplete) {
        ivFullscreenFail.setVisibility(View.VISIBLE);
        ivFullscreenFail.setScaleX(0f);
        ivFullscreenFail.setScaleY(0f);
        ivFullscreenFail.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        handler.postDelayed(() -> {
            speakText(currentLevel.getQuestion().getPrompt());

            handler.postDelayed(() -> {
                ivFullscreenFail.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction(() -> {
                            ivFullscreenFail.setVisibility(View.GONE);
                            ivFullscreenFail.setAlpha(1f);
                            if (onComplete != null) {
                                onComplete.run();
                            }
                        })
                        .start();
            }, 1500);
        }, 2500);
    }

    // ---------- 成功全屏动画 ----------
    private void showSuccessAnimation(final Runnable onComplete) {
        ivFullscreenSuccess.setVisibility(View.VISIBLE);
        ivFullscreenSuccess.setScaleX(0f);
        ivFullscreenSuccess.setScaleY(0f);
        ivFullscreenSuccess.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // 延迟 2 秒后淡出并执行回调
        handler.postDelayed(() -> {
            ivFullscreenSuccess.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        ivFullscreenSuccess.setVisibility(View.GONE);
                        ivFullscreenSuccess.setAlpha(1f);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    })
                    .start();
        }, 2000); // 2秒后隐藏并跳转
    }

    // ---------- 答题 ----------
    private void checkAnswer(int selectedIndex, RelativeLayout selectedRoot) {
        Question q = currentLevel.getQuestion();
        if (selectedIndex == q.getCorrectAnswerIndex()) {
            // 正确
            isAnimating = true;
            progressManager.saveCompletedLevel(currentLevel.getId());
            speakText("太棒了，闯关成功！");

            showSuccessAnimation(() -> {
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
            // 错误
            isAnimating = true;
            speakText("宝宝请重新来过");

            showFailAnimation(() -> {
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