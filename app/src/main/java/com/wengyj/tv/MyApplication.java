package com.wengyj.tv;

import android.app.Application;

import com.wengyj.tv.utils.MusicManager;
import com.wengyj.tv.utils.ProgressManager;
import com.wengyj.tv.utils.TlsCompat;
import com.wengyj.tv.utils.UpdateManager;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Android 4.x 默认只启用 SSLv3/TLSv1，会导致 HTTPS 握手失败，先装兼容层
        TlsCompat.install();
        // 根据偏好设置初始化背景音乐
        boolean bgmEnabled = new ProgressManager(this).isBgmEnabled();
        MusicManager.getInstance(this).setEnabled(bgmEnabled);
        // 开机/启动时在后台线程检查版本并自动升级，不阻塞界面
        new UpdateManager(this).checkInBackground();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        MusicManager.getInstance(this).release();
    }
}