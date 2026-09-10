package com.wengyj.tv;

import android.app.Application;

import com.wengyj.tv.utils.MusicManager;
import com.wengyj.tv.utils.ProgressManager;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 根据偏好设置初始化背景音乐
        boolean bgmEnabled = new ProgressManager(this).isBgmEnabled();
        MusicManager.getInstance(this).setEnabled(bgmEnabled);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        MusicManager.getInstance(this).release();
    }
}