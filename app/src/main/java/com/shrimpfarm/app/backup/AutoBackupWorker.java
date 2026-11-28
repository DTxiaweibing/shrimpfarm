package com.shrimpfarm.app.backup;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AutoBackupWorker extends Worker {

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        WebDavManager webDav = new WebDavManager(getApplicationContext());
        // pref 里存的是密文，必须通过 getSavedPassword() 解密后再做 Basic 认证。
        String username = webDav.getSavedUsername();
        String password = webDav.getSavedPassword();
        if (username.isEmpty() || password.isEmpty()) {
            return Result.failure();
        }

        webDav.initConnection(username, password);
        try {
            webDav.testConnection();
            String fileName = webDav.uploadBackup();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            android.util.Log.i("AutoBackup", "自动备份成功: " + fileName + " at " + sdf.format(new Date()));
            return Result.success();
        } catch (Exception e) {
            android.util.Log.e("AutoBackup", "自动备份失败: " + e.getMessage());
            return Result.retry();
        }
    }
}
