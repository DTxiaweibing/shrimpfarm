package com.shrimpfarm.app.startup;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.shrimpfarm.app.banner.BannerUpdateWorker;
import com.shrimpfarm.app.utils.UpdateManager;

public class AppStartupManager {

    private static final String TAG = "AppStartupManager";

    private final Context context;
    private final UpdateManager updateManager;
    private final Listener listener;

    public interface Listener {
        void onUpdateAvailable();
    }

    public AppStartupManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.updateManager = new UpdateManager(context);
    }

    /** 启动时和回到前台时统一调用 */
    public void run() {
        // 1. 版本更新：先看缓存（无网络也能立即显示）
        if (updateManager.hasUnseenUpdate()) {
            listener.onUpdateAvailable();
        }
        // 2. 版本更新：异步联网检查
        checkVersionUpdate();
        // 3. 广告图 + help + zhaomu 文件更新
        triggerBannerUpdate();
    }

    private void checkVersionUpdate() {
        String currentVersion = "1.0";
        try {
            currentVersion = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {}

        updateManager.setCallback(new UpdateManager.UpdateCallback() {
            @Override
            public void onUpdateAvailable(String latestVersion, String updateLog) {
                if (updateManager.hasUnseenUpdate()) {
                    listener.onUpdateAvailable();
                }
            }

            @Override
            public void onUpdateChecked(boolean hasUpdate) {
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "版本更新检查失败: " + error);
            }
        });
        updateManager.checkForUpdate(currentVersion);
    }

    private void triggerBannerUpdate() {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BannerUpdateWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();
        WorkManager.getInstance(context).enqueue(request);
    }

    // ========== 委托给 UpdateManager ==========

    public boolean hasUnseenUpdate() {
        return updateManager.hasUnseenUpdate();
    }

    public String getUpdateVersion() {
        return updateManager.getUpdateVersion();
    }

    public String getUpdateLog() {
        return updateManager.getUpdateLog();
    }

    public void markUpdateSeen() {
        updateManager.markUpdateSeen();
    }

    public void ignoreVersion(String version) {
        updateManager.ignoreVersion(version);
    }

    public void remindLater() {
        updateManager.remindLater();
    }

    public static String getUpdatePageUrl() {
        return UpdateManager.getUpdatePageUrl();
    }
}
