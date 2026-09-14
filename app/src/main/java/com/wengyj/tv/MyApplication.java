package com.wengyj.tv;

import android.app.Application;

import com.blankj.utilcode.util.ShellUtils;
import com.wengyj.tv.utils.MusicManager;
import com.wengyj.tv.utils.ProgressManager;
import com.wengyj.tv.utils.UpdateManager;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 根据偏好设置初始化背景音乐
        boolean bgmEnabled = new ProgressManager(this).isBgmEnabled();
        MusicManager.getInstance(this).setEnabled(bgmEnabled);
        //添加设置tts语音为讯飞语音
        ShellUtils.execCmd("settings put secure tts_default_synth com.iflytek.speechcloud", true);
        ShellUtils.execCmd("settings put secure voice_recognition_service com.iflytek.speechcloud/com.iflytek.iatservice.SpeechService", true);

        ShellUtils.execCmd("settings put secure tts_default_synth com.iflytek.speechcloud", false);
        ShellUtils.execCmd("settings put secure voice_recognition_service com.iflytek.speechcloud/com.iflytek.iatservice.SpeechService", false);
        // 开机/启动时在后台线程检查版本并自动升级，不阻塞界面
        new UpdateManager(this).checkInBackground();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        MusicManager.getInstance(this).release();
    }
}