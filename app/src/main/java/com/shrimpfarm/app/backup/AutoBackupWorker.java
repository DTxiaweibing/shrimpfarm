package com.shrimpfarm.app.backup;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AutoBackupWorker extends Worker {

    private static final String PREF_NAME = "webdav_config";
    private static final String KEY_USERNAME = "webdav_username";
    private static final String KEY_PASSWORD = "webdav_password";

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String username = prefs.getString(KEY_USERNAME, "");
        String password = prefs.getString(KEY_PASSWORD, "");

        if (username.isEmpty() || password.isEmpty()) {
            return Result.failure();
        }

        WebDavManager webDav = new WebDavManager(getApplicationContext());
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
