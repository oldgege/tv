package com.wengyj.tv;

import android.app.Application;

import com.wengyj.tv.utils.VideoCacheManager;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        VideoCacheManager.getInstance(this).preloadAll(() -> {
            // 预加载完成，可选日志
        });
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        VideoCacheManager.getInstance(this).releaseAll();
    }
}