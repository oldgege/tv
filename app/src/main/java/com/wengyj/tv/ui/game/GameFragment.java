package com.wengyj.tv.ui.game;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import com.wengyj.tv.utils.MusicManager;
import com.wengyj.tv.utils.ProgressManager;
import com.wengyj.tv.ui.MainActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class GameFragment extends Fragment implements TextToSpeech.OnInitListener {
    private static final String ARG_LEVEL_ID = "level_id";

    private static final String IFLYTEK_TTS_ENGINE = "com.iflytek.speechcloud";
    private static boolean hasShownTtsError = false;

    private static final int MAX_SET_LANGUAGE_RETRY = 5;
    private static final long SET_LANGUAGE_RETRY_DELAY = 400;

    private static final long FADE_OUT_DURATION = 150;
    private static final long FADE_IN_DURATION = 250;

    private int levelId;
    private Level currentLevel;
    private int currentChapterId = -1;             // 记录当前章节 ID，用于显示关卡信息
    private TextView tvQuestion, tvHint;
    private TextView tvStars;
    private TextView tvLevelInfo;                  // 关卡信息
    private View tvBack;
    private View btnReplay;
    private LinearLayout optionsContainer;
    private LinearLayout contentContainer;
    private VideoView videoSuccess;
    private VideoView videoError;
    private ProgressManager progressManager;

    private TextToSpeech tts;
    private String currentEngine = null;
    private boolean isInitializing = false;
    private boolean isTtsReady = false;
    private int setLanguageRetryCount = 0;

    private Handler handler = new Handler();
    private boolean isAnimating = false;

    private List<Integer> errorVideoList = null;
    private final Random random = new Random();

    private int firstOptionViewId = View.NO_ID;

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
        initTts(null);
    }

    private void initTts(String engine) {
        releaseTts();
        isInitializing = true;
        currentEngine = engine;
        setLanguageRetryCount = 0;

        if (engine == null) {
            tts = new TextToSpeech(getContext(), this);
        } else {
            tts = new TextToSpeech(getContext(), this, engine);
        }
    }

    private void releaseTts() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {}
            tts = null;
        }
        isTtsReady = false;
        isInitializing = false;
        setLanguageRetryCount = 0;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_game, container, false);
        tvQuestion = view.findViewById(R.id.tv_question);
        tvHint = view.findViewById(R.id.tv_hint);
        tvStars = view.findViewById(R.id.tv_stars);
        tvLevelInfo = view.findViewById(R.id.tv_level_info);
        tvBack = view.findViewById(R.id.tv_back);
        btnReplay = view.findViewById(R.id.btn_replay);
        contentContainer = view.findViewById(R.id.content_container);
        optionsContainer = view.findViewById(R.id.options_container);
        videoSuccess = view.findViewById(R.id.video_success);
        videoError = view.findViewById(R.id.video_error);

        if (tvBack != null) {
            tvBack.setOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            });
        }

        if (btnReplay != null) {
            btnReplay.setOnClickListener(v -> {
                if (isAnimating) return;
                speakCurrentQuestion();
            });
        }

        updateStarsDisplay();

        optionsContainer.setOrientation(LinearLayout.HORIZONTAL);
        optionsContainer.setGravity(android.view.Gravity.CENTER);

        if (!loadLevelById(levelId)) {
            Toast.makeText(getContext(), "关卡数据错误", Toast.LENGTH_SHORT).show();
            return view;
        }

        // 显示关卡信息
        updateLevelInfo();

        Question q = currentLevel.getQuestion();
        tvQuestion.setText(q.getPrompt());
        if (q.getType() == Question.Type.LISTEN_SELECT) {
            tvHint.setText("🔊 仔细听，选出正确的字（按1-4可听选项）");
        } else {
            tvHint.setText(q.getHint() != null ? q.getHint() : "");
        }

        if (isTtsReady) {
            speakCurrentQuestion();
        }

        buildOptions();
        return view;
    }

    /**
     * 加载关卡并记录所属章节 ID
     */
    private boolean loadLevelById(int id) {
        List<Chapter> chapters = ChapterDataSource.getAllChapters(getContext());
        for (Chapter chapter : chapters) {
            for (Level level : chapter.getLevels()) {
                if (level.getId() == id) {
                    currentLevel = level;
                    currentChapterId = chapter.getId();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 更新顶部关卡信息显示
     * 格式：第 X 章 · 第 Y 关
     */
    private void updateLevelInfo() {
        if (tvLevelInfo == null || currentLevel == null) return;

        // levelId 格式为 chapterId * 100 + levelIndex（如 305 表示第3章第5关）
        int levelIndex = currentLevel.getId() % 100;
        int chapterId = currentChapterId > 0 ? currentChapterId : (currentLevel.getId() / 100);

        String info = "第 " + chapterId + " 章 · 第 " + levelIndex + " 关";
        tvLevelInfo.setText(info);
    }

    @Override
    public void onResume() {
        super.onResume();

        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.setNumberKeyListener(this::handleNumberKey);
        }

        if (optionsContainer != null) {
            optionsContainer.post(() -> {
                if (optionsContainer.getChildCount() > 0) {
                    optionsContainer.getChildAt(0).requestFocus();
                }
            });
        }
        if (tts == null && !isInitializing) {
            hasShownTtsError = false;
            initTts(null);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.clearNumberKeyListener();
        }
    }

    private boolean handleNumberKey(int number) {
        if (isAnimating) return true;
        if (currentLevel == null) return true;

        List<String> options = currentLevel.getQuestion().getOptions();
        int index = number - 1;
        if (index < 0 || index >= options.size()) return true;

        speakOption(index);
        return true;
    }

    private void speakOption(int index) {
        if (currentLevel == null) return;
        List<String> options = currentLevel.getQuestion().getOptions();
        if (index < 0 || index >= options.size()) return;

        if (tts != null && isTtsReady) {
            String text = (index + 1) + "号，" + options.get(index);
            speakText(text);
        } else {
            if (!isAdded() || getContext() == null) return;
            try {
                Toast.makeText(getContext(), "语音播报不可用", Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {}
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

        firstOptionViewId = View.NO_ID;

        for (int i = 0; i < options.size(); i++) {
            View item = LayoutInflater.from(getContext()).inflate(R.layout.item_monster_option, optionsContainer, false);
            ImageView body = item.findViewById(R.id.iv_monster_body);
            TextView text = item.findViewById(R.id.tv_option_text);
            RelativeLayout root = (RelativeLayout) item;

            body.setImageResource(monsterColors[i % monsterColors.length]);
            text.setText(options.get(i));
            root.setBackgroundResource(R.drawable.bg_monster_selector);

            int optionViewId = View.generateViewId();
            root.setId(optionViewId);

            if (i == 0) {
                firstOptionViewId = optionViewId;
                root.setNextFocusLeftId(R.id.btn_replay);
            }

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

            root.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (isAnimating) return true;
                        checkAnswer(index, root);
                        return true;
                    default:
                        return true;
                }
            });

            root.setFocusable(true);
            root.setFocusableInTouchMode(true);
            optionsContainer.addView(root);
        }

        if (btnReplay != null) {
            btnReplay.setNextFocusLeftId(R.id.tv_back);
            if (firstOptionViewId != View.NO_ID) {
                btnReplay.setNextFocusRightId(firstOptionViewId);
            }
        }
        if (tvBack != null) {
            tvBack.setNextFocusRightId(R.id.btn_replay);
        }

        optionsContainer.post(() -> {
            if (optionsContainer.getChildCount() > 0) {
                optionsContainer.getChildAt(0).requestFocus();
            }
        });
    }

    private void fadeOutContent(final Runnable onComplete) {
        if (contentContainer == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        contentContainer.animate().cancel();
        contentContainer.animate()
                .alpha(0f)
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(FADE_OUT_DURATION)
                .withEndAction(() -> {
                    if (onComplete != null) onComplete.run();
                })
                .start();
    }

    private void fadeInContent(final Runnable onComplete) {
        if (contentContainer == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        contentContainer.animate().cancel();
        contentContainer.setAlpha(0f);
        contentContainer.setScaleX(0.95f);
        contentContainer.setScaleY(0.95f);
        contentContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(FADE_IN_DURATION)
                .withEndAction(() -> {
                    if (onComplete != null) onComplete.run();
                })
                .start();
    }

    // ---------- TTS 回调 ----------
    @Override
    public void onInit(int status) {
        isInitializing = false;

        if (status != TextToSpeech.SUCCESS) {
            if (currentEngine == null) {
                initTts(IFLYTEK_TTS_ENGINE);
            } else {
                isTtsReady = false;
                showTtsErrorToast();
            }
            return;
        }

        handler.postDelayed(this::trySetLanguage, 300);
    }

    private void trySetLanguage() {
        if (tts == null) return;

        try {
            int result = tts.setLanguage(Locale.CHINESE);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = tts.setLanguage(Locale.CHINA);
            }

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (currentEngine == null) {
                    initTts(IFLYTEK_TTS_ENGINE);
                } else {
                    isTtsReady = false;
                    showTtsErrorToast();
                }
                return;
            }

            try {
                tts.setSpeechRate(0.5f);
                tts.setPitch(1.1f);
            } catch (Exception ignored) {}
            isTtsReady = true;

            if (currentLevel != null && tvQuestion != null) {
                speakCurrentQuestion();
            }

        } catch (Exception e) {
            setLanguageRetryCount++;
            if (setLanguageRetryCount < MAX_SET_LANGUAGE_RETRY) {
                handler.postDelayed(this::trySetLanguage, SET_LANGUAGE_RETRY_DELAY);
            } else {
                if (currentEngine == null) {
                    initTts(IFLYTEK_TTS_ENGINE);
                } else {
                    isTtsReady = false;
                    showTtsErrorToast();
                }
            }
        }
    }

    private void showTtsErrorToast() {
        if (hasShownTtsError) return;
        hasShownTtsError = true;

        if (!isAdded() || getContext() == null) return;
        try {
            Toast.makeText(getContext(), "语音播报不可用", Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {}
    }

    private void speakCurrentQuestion() {
        if (currentLevel == null) return;

        if (tts != null && isTtsReady) {
            Question q = currentLevel.getQuestion();
            if (q.getType() == Question.Type.LISTEN_SELECT
                    && q.getAudioText() != null && !q.getAudioText().isEmpty()) {
                speakText(q.getAudioText() + "，" + q.getAudioText());
            } else {
                speakText(q.getPrompt());
            }
        } else {
            if (!isAdded() || getContext() == null) return;
            try {
                Toast.makeText(getContext(), "语音播报不可用", Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {}
        }
    }

    private void speakText(String text) {
        if (tts != null && isTtsReady && text != null && !text.isEmpty()) {
            try {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDestroy() {
        if (contentContainer != null) {
            contentContainer.animate().cancel();
        }
        releaseTts();
        if (videoSuccess != null) {
            videoSuccess.stopPlayback();
        }
        if (videoError != null) {
            videoError.stopPlayback();
        }
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.clearVideoKeyListener();
            activity.clearNumberKeyListener();
        }
        MusicManager.getInstance(getContext()).restoreVolume();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private List<Integer> findErrorVideos() {
        if (errorVideoList != null) return errorVideoList;

        List<Integer> list = new ArrayList<>();
        String packageName = getContext().getPackageName();

        int baseId = getResources().getIdentifier("error", "raw", packageName);
        if (baseId != 0) {
            list.add(baseId);
        }

        for (int i = 1; i <= 20; i++) {
            int id = getResources().getIdentifier("error" + i, "raw", packageName);
            if (id != 0) {
                list.add(id);
            }
        }

        errorVideoList = list;
        return list;
    }

    private int getRandomErrorVideoResId() {
        List<Integer> list = findErrorVideos();
        if (list.isEmpty()) return 0;
        return list.get(random.nextInt(list.size()));
    }

    private boolean isLastLevelOfChapter() {
        List<Chapter> chapters = ChapterDataSource.getAllChapters(getContext());
        for (Chapter chapter : chapters) {
            List<Level> levels = chapter.getLevels();
            if (!levels.isEmpty() && levels.get(levels.size() - 1).getId() == levelId) {
                return true;
            }
        }
        return false;
    }

    private int getCurrentChapterId() {
        return currentChapterId;
    }

    private int getStoryVideoResId(int chapterId) {
        if (chapterId <= 0) return 0;
        String name = "story_chapter_" + chapterId;
        return getResources().getIdentifier(name, "raw", getContext().getPackageName());
    }

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

    private void playVideoAndWait(VideoView videoView, int rawResId, final Runnable onComplete) {
        if (rawResId == 0) {
            if (onComplete != null) onComplete.run();
            return;
        }

        final boolean[] completed = {false};

        final Runnable safeComplete = () -> {
            if (completed[0]) return;
            completed[0] = true;

            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.clearVideoKeyListener();
            }

            videoView.stopPlayback();
            videoView.setVisibility(View.GONE);

            MusicManager.getInstance(getContext()).restoreVolume();

            if (onComplete != null) {
                onComplete.run();
            }
        };

        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.setVideoKeyListener(safeComplete::run);
        }

        MusicManager.getInstance(getContext()).lowerVolume();

        fadeOutContent(() -> {
            if (completed[0]) return;
            showVideo(videoView, rawResId, safeComplete);
        });
    }

    private void showVideo(VideoView videoView, int rawResId, Runnable safeComplete) {
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

    private void resetAllProgressAndGoHome() {
        int resId = getResources().getIdentifier("game_over", "raw", getContext().getPackageName());
        if (resId != 0) {
            playVideoAndWait(videoError, resId, this::doReset);
        } else {
            doReset();
        }
    }

    private void doReset() {
        progressManager.resetAllProgress();
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.navigateToChapter();
            Toast.makeText(activity, "💫 星星用完了，重新开始吧！", Toast.LENGTH_LONG).show();
            speakText("星星用完了，重新开始吧");
        }
    }

    private void onCorrectAnswer() {
        progressManager.addStar();
        progressManager.saveCompletedLevel(currentLevel.getId());
        updateStarsDisplay();

        isAnimating = true;
        playVideoAndWait(videoSuccess, R.raw.success, () -> {
            if (isLastLevelOfChapter()) {
                int chapterId = getCurrentChapterId();
                int storyResId = getStoryVideoResId(chapterId);
                if (storyResId != 0) {
                    playVideoAndWait(videoSuccess, storyResId, this::finishCorrectSequence);
                    return;
                }
            }
            finishCorrectSequence();
        });
    }

    private void finishCorrectSequence() {
        int nextId = getNextLevelId();
        MainActivity activity = (MainActivity) getActivity();

        if (nextId != -1) {
            switchToNextLevel(nextId);
        } else {
            isAnimating = false;
            if (activity != null) {
                activity.navigateToChapter();
                speakText("恭喜你完成所有关卡！");
            }
        }
    }

    private void switchToNextLevel(int nextLevelId) {
        levelId = nextLevelId;

        if (!loadLevelById(levelId)) {
            isAnimating = false;
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.navigateToChapter();
            }
            return;
        }

        progressManager.saveCurrentProgress(0, levelId);

        // 更新关卡信息
        updateLevelInfo();

        Question q = currentLevel.getQuestion();
        tvQuestion.setText(q.getPrompt());
        if (q.getType() == Question.Type.LISTEN_SELECT) {
            tvHint.setText("🔊 仔细听，选出正确的字（按1-4可听选项）");
        } else {
            tvHint.setText(q.getHint() != null ? q.getHint() : "");
        }

        buildOptions();

        fadeInContent(() -> {
            isAnimating = false;
        });

        speakCurrentQuestion();
    }

    private void checkAnswer(int selectedIndex, RelativeLayout selectedRoot) {
        Question q = currentLevel.getQuestion();
        if (selectedIndex == q.getCorrectAnswerIndex()) {
            onCorrectAnswer();
        } else {
            handleWrongAnswer();
        }
    }

    private void handleWrongAnswer() {
        progressManager.deductStars(2);
        updateStarsDisplay();
        int remainingStars = progressManager.getStars();

        if (remainingStars <= 0) {
            isAnimating = true;
            resetAllProgressAndGoHome();
            return;
        }

        isAnimating = true;
        int errorResId = getRandomErrorVideoResId();

        playVideoAndWait(videoError, errorResId, () -> {
            reshuffleOptions();
            fadeInContent(() -> {
                isAnimating = false;
            });
            speakCurrentQuestion();
        });
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