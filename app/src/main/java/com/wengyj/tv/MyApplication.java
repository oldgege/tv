package com.wengyj.tv;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import com.wengyj.tv.utils.MusicManager;
import com.wengyj.tv.utils.ProgressManager;
import com.wengyj.tv.utils.TLSSocketFactory;
import com.wengyj.tv.utils.UpdateManager;

import javax.net.ssl.HttpsURLConnection;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
// Android 4.x 全局启用 TLS 1.2
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                HttpsURLConnection.setDefaultSSLSocketFactory(new TLSSocketFactory());
            } catch (Exception e) {
                Log.w("MyApplication", "TLS 兼容初始化失败: " + e.getMessage());
            }
        }
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