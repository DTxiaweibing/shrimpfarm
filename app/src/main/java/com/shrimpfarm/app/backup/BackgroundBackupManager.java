package com.shrimpfarm.app.backup;

import android.content.Context;
import android.content.SharedPreferences;

public class BackgroundBackupManager {

    private static final String PREF_NAME = "webdav_config";
    private static final String KEY_USERNAME = "webdav_username";
    private static final String KEY_PASSWORD = "webdav_password";
    private static final String KEY_LAST_BACKGROUND_BACKUP = "last_background_backup_time";
    private static final long MIN_INTERVAL_MS = 2 * 60 * 60 * 1000;

    public static void onAppBackground(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String username = prefs.getString(KEY_USERNAME, "");
        String password = prefs.getString(KEY_PASSWORD, "");

        if (username.isEmpty() || password.isEmpty()) return;

        long lastTime = prefs.getLong(KEY_LAST_BACKGROUND_BACKUP, 0);
        long now = System.currentTimeMillis();
        if (now - lastTime < MIN_INTERVAL_MS) return;

        prefs.edit().putLong(KEY_LAST_BACKGROUND_BACKUP, now).apply();

        new Thread(() -> {
            try {
                WebDavManager webDav = new WebDavManager(context);
                webDav.initConnection(username, password);
                String fileName = webDav.uploadBackup();
                android.util.Log.i("BgBackup", "后台备份成功: " + fileName);
            } catch (Exception e) {
                android.util.Log.e("BgBackup", "后台备份失败: " + e.getMessage());
            }
        }).start();
    }
}
