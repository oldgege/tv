package com.wengyj.tv;

import android.app.Application;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 视频播放由 VideoView 内部管理，无需预加载
    }
}