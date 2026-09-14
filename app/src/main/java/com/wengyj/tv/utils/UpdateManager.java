package com.wengyj.tv.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 远程版本升级管理器。
 *
 * <p>原理：App 启动时在后台线程拉取远端 version.json，比较 versionCode。
 * 若远端更新，则静默下载 APK，最后调起系统安装界面完成升级。
 * 版本信息与 APK 均托管在免费静态站点（如 GitHub Releases + jsDelivr），
 * 无需自建服务器。</p>
 */
public class UpdateManager {

    private static final String TAG = "UpdateManager";

    /**
     * 远程版本信息地址（替换成你自己的地址即可）。
     *
     * <p>无需服务器，推荐用 jsDelivr 加速 GitHub（国内可用）：</p>
     * https://cdn.jsdelivr.net/gh/用户名/仓库名@main/version.json
     */
    private static final String UPDATE_JSON_URL =
            "https://cdn.jsdelivr.net/gh/oldgege/tv@main/version.json";

    private static final String PREFS = "app_update";
    private static final String KEY_PENDING_APK = "pending_apk";
    private static final String KEY_PENDING_URL = "pending_url";
    private static final String KEY_PENDING_ETAG = "pending_etag";

    private static final int CONNECT_TIMEOUT = 8000;
    private static final int READ_TIMEOUT = 20000;
    /** HTTP 206 Partial Content */
    private static final int HTTP_PARTIAL = 206;
    /** 同一进程内下载重试次数 */
    private static final int MAX_DOWNLOAD_RETRY = 3;
    /** 重试基础间隔（毫秒），实际间隔 = 基础间隔 × 已重试次数（3s、6s） */
    private static final long RETRY_BASE_DELAY = 3000L;

    private final Context context;
    private final SharedPreferences prefs;

    public UpdateManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * 在后台线程检查并升级，不阻塞 UI、不打扰启动流程。
     * 在 Application.onCreate() 中调用即可实现"开机自动后台检查"。
     */
    public void checkInBackground() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. 若有上次已下载但未安装的包（例如去授权后返回），先补装
                    if (tryInstallPending()) {
                        return;
                    }
                    // 2. 检查远程版本，必要时下载
                    checkAndUpdate();
                } catch (Throwable t) {
                    Log.w(TAG, "自动更新流程异常: " + t.getMessage());
                }
            }
        }, "update-check").start();
    }

    // ---------------- 内部流程 ----------------

    private void checkAndUpdate() {
        int currentCode = getCurrentVersionCode();
        JSONObject json = fetchJson(UPDATE_JSON_URL);
        if (json == null) {
            return;
        }

        int remoteCode = json.optInt("versionCode", 0);
        if (remoteCode <= currentCode) {
            Log.i(TAG, "已是最新版本 current=" + currentCode);
            return;
        }

        String apkUrl = json.optString("apkUrl", "").trim();
        if (apkUrl.isEmpty()) {
            Log.w(TAG, "version.json 缺少 apkUrl");
            return;
        }

        Log.i(TAG, "发现新版本 " + remoteCode + "（当前 " + currentCode + "），开始下载:" + apkUrl);
        File apk = downloadApk(apkUrl, remoteCode);
        if (apk != null) {
            prefs.edit().putString(KEY_PENDING_APK, apk.getAbsolutePath()).apply();
            tryInstallPending();
        }
    }

    /**
     * 尝试安装已下载的 APK。
     *
     * @return true 表示存在待安装包并已处理（或正在处理），false 表示没有待安装包。
     */
    private boolean tryInstallPending() {
        String path = prefs.getString(KEY_PENDING_APK, null);
        if (path == null) {
            return false;
        }
        File apk = new File(path);
        if (!apk.exists()) {
            prefs.edit().remove(KEY_PENDING_APK).apply();
            return false;
        }

        // Android 8.0+ 需要"安装未知应用"权限，未授权时先跳转设置页
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !context.getPackageManager().canRequestPackageInstalls()) {
            requestUnknownSourcesPermission();
            return true;
        }

        installApk(apk);
        prefs.edit().remove(KEY_PENDING_APK).apply();
        return true;
    }

    private void requestUnknownSourcesPermission() {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.i(TAG, "已跳转“安装未知应用”授权页，授权后下次启动将自动安装");
        } catch (Exception e) {
            Log.w(TAG, "无法打开未知来源设置: " + e.getMessage());
        }
    }

    private void installApk(File apk) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".fileprovider", apk);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                uri = Uri.fromFile(apk);
            }

            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            context.startActivity(intent);
            Log.i(TAG, "已调起安装界面: " + apk.getAbsolutePath());
        } catch (Exception e) {
            Log.w(TAG, "调起安装失败: " + e.getMessage());
        }
    }

    /**
     * 下载安装包，支持断点续传，同进程内失败自动重试 {@link #MAX_DOWNLOAD_RETRY} 次。
     * 每次重试都基于已下载的分片续传，不会重复下载已有部分；
     * 重试间隔递增（3s、6s），给网络恢复留出时间。
     * 全部失败时保留分片返回 null，下次 App 启动继续。
     *
     * @return 下载完成的 APK；未完成返回 null
     */
    private File downloadApk(String apkUrl, int versionCode) {
        File dir = context.getExternalFilesDir("update");
        if (dir == null) {
            dir = context.getFilesDir();
        }
        if (dir == null) {
            return null;
        }
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }

        File apk = new File(dir, "app-" + versionCode + ".apk");

        // 只清理其它版本的旧包，保留当前版本的半成品用于续传
        File[] olds = dir.listFiles();
        if (olds != null) {
            for (File f : olds) {
                if (!f.getName().equals(apk.getName())) {
                    f.delete();
                }
            }
        }

        // 下载地址变了，说明是另一个包，已下载的分片作废
        if (!apkUrl.equals(prefs.getString(KEY_PENDING_URL, null))) {
            deleteQuietly(apk);
            prefs.edit().remove(KEY_PENDING_ETAG).apply();
        }

        for (int retry = 1; retry <= MAX_DOWNLOAD_RETRY; retry++) {
            File result = downloadOnce(apkUrl, apk);
            if (result != null) {
                return result;
            }
            if (retry < MAX_DOWNLOAD_RETRY) {
                long wait = RETRY_BASE_DELAY * retry;
                Log.i(TAG, "第 " + retry + " 次下载未完成，"
                        + (wait / 1000) + " 秒后重试（断点续传）");
                sleepQuietly(wait);
            }
        }
        Log.w(TAG, "已重试 " + MAX_DOWNLOAD_RETRY + " 次仍未完成，保留分片等待下次启动续传");
        return null;
    }

    /**
     * 单次下载尝试（支持断点续传）。
     *
     * @return 下载并校验完成返回 APK；否则返回 null（保留已下载分片）
     */
    private File downloadOnce(String apkUrl, File apk) {
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            long downloaded = apk.exists() ? apk.length() : 0;

            conn = (HttpURLConnection) new URL(apkUrl).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", "TV-Updater");
            if (downloaded > 0) {
                // 关键：请求从断点处开始传输
                conn.setRequestProperty("Range", "bytes=" + downloaded + "-");
            }
            conn.connect();

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK && code != HTTP_PARTIAL) {
                Log.w(TAG, "下载失败 HTTP " + code);
                return null;
            }

            String etag = conn.getHeaderField("ETag");
            String savedEtag = prefs.getString(KEY_PENDING_ETAG, null);

            if (code == HttpURLConnection.HTTP_OK) {
                // 服务端不支持/忽略了 Range，只能从头下载
                downloaded = 0;
                deleteQuietly(apk);
            } else if (savedEtag != null && etag != null && !savedEtag.equals(etag)) {
                // 远端文件被替换过，已有分片作废，下次重试将全量下载
                Log.w(TAG, "远端文件已变化，放弃续传，改为全量下载");
                deleteQuietly(apk);
                prefs.edit().remove(KEY_PENDING_ETAG).apply();
                return null;
            }

            prefs.edit().putString(KEY_PENDING_URL, apkUrl).apply();
            if (etag != null) {
                prefs.edit().putString(KEY_PENDING_ETAG, etag).apply();
            }

            // 期望的完整大小，用于校验是否下完
            long expected = parseTotalSize(conn, downloaded);

            in = conn.getInputStream();
            out = new FileOutputStream(apk, downloaded > 0);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
            closeQuietly(out);
            out = null;
            closeQuietly(in);
            in = null;

            long realSize = apk.length();
            if (realSize == 0) {
                deleteQuietly(apk);
                return null;
            }
            if (expected > 0 && realSize != expected) {
                Log.w(TAG, "文件不完整 " + realSize + "/" + expected + "，保留分片等待续传");
                return null;
            }

            Log.i(TAG, "下载完成: " + apk.getAbsolutePath() + " size=" + realSize
                    + (downloaded > 0 ? "（续传自 " + downloaded + " 字节）" : ""));
            prefs.edit().remove(KEY_PENDING_ETAG).apply();
            return apk;
        } catch (Exception e) {
            Log.w(TAG, "下载中断，已保留分片待续传: " + e.getMessage());
            return null;
        } finally {
            closeQuietly(in);
            closeQuietly(out);
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 从响应头解析文件完整大小：优先 Content-Range，其次 Content-Length */
    private static long parseTotalSize(HttpURLConnection conn, long downloaded) {
        String range = conn.getHeaderField("Content-Range");
        if (range != null && range.contains("/")) {
            try {
                return Long.parseLong(range.substring(range.lastIndexOf('/') + 1).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        String len = conn.getHeaderField("Content-Length");
        if (len != null) {
            try {
                return downloaded + Long.parseLong(len.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private static void deleteQuietly(File f) {
        if (f != null && f.exists()) {
            f.delete();
        }
    }

    /** 重试前的等待，本方法运行在后台线程，可以安全睡眠 */
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private JSONObject fetchJson(String url) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", "TV-Updater");
            conn.connect();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "获取版本信息 HTTP " + conn.getResponseCode());
                return null;
            }

            in = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            return new JSONObject(bos.toString("UTF-8"));
        } catch (Exception e) {
            Log.w(TAG, "获取版本信息失败: " + e.getMessage());
            return null;
        } finally {
            closeQuietly(in);
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private int getCurrentVersionCode() {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    private static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }
}
