package com.shrimpfarm.app.backup;

import android.content.Context;
import android.content.SharedPreferences;

import com.shrimpfarm.app.utils.StoragePermissionHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

public class BackgroundBackupManager {

    private static final String PREF_NAME = "webdav_config";
    private static final String KEY_USERNAME = "webdav_username";
    private static final String KEY_PASSWORD = "webdav_password";
    private static final String KEY_LAST_BACKGROUND_BACKUP = "last_background_backup_time";
    private static final String KEY_LAST_BACKUP_HASH = "last_backup_db_hash";
    private static final String DB_NAME = "FeedingRecord.db";
    private static final long MIN_INTERVAL_MS = 2 * 60 * 60 * 1000;

    public static void onAppBackground(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String username = prefs.getString(KEY_USERNAME, "");
        String password = prefs.getString(KEY_PASSWORD, "");

        boolean cloudConfigured = !username.isEmpty() && !password.isEmpty();
        boolean localAllowed = StoragePermissionHelper.hasStoragePermission(context);
        if (!cloudConfigured && !localAllowed) return;

        long lastTime = prefs.getLong(KEY_LAST_BACKGROUND_BACKUP, 0);
        long now = System.currentTimeMillis();
        if (now - lastTime < MIN_INTERVAL_MS) return;

        prefs.edit().putLong(KEY_LAST_BACKGROUND_BACKUP, now).apply();

        Thread t = new Thread(() -> {
            try {
                File dbFile = context.getDatabasePath(DB_NAME);
                if (dbFile == null || !dbFile.exists()) {
                    dbFile = new File(context.getFilesDir().getParent() + "/databases/" + DB_NAME);
                    if (!dbFile.exists()) {
                        android.util.Log.w("BgBackup", "数据库文件不存在，跳过");
                        return;
                    }
                }

                checkpointDatabase(context);
                String currentHash = md5(dbFile);

                String lastHash = prefs.getString(KEY_LAST_BACKUP_HASH, "");
                if (!lastHash.isEmpty() && lastHash.equals(currentHash)) {
                    android.util.Log.i("BgBackup", "数据库无变化，跳过自动备份");
                    return;
                }

                String localName = null;
                if (localAllowed) {
                    localName = new LocalBackupManager(context).exportToLocal(false);
                    if (!verifyLocalBackup(context, localName, dbFile.length())) {
                        throw new Exception("本地备份校验失败");
                    }
                    android.util.Log.i("BgBackup", "本地备份完成: " + localName);
                }

                if (cloudConfigured) {
                    WebDavManager webDav = new WebDavManager(context);
                    webDav.initConnection(username, password);
                    String fileName = webDav.uploadBackup(false);
                    long remoteSize = webDav.getRemoteFileSize(fileName);
                    if (remoteSize < dbFile.length()) {
                        throw new Exception("云端备份校验失败，远端大小=" + remoteSize);
                    }
                    android.util.Log.i("BgBackup", "云端备份完成: " + fileName);
                }

                prefs.edit().putString(KEY_LAST_BACKUP_HASH, currentHash).apply();
                android.util.Log.i("BgBackup", "自动备份全部完成并记录快照");
            } catch (Exception e) {
                android.util.Log.e("BgBackup", "后台备份失败: " + e.getMessage());
            }
        }, "BackgroundBackup-run");
        t.setDaemon(true);
        t.start();
    }

    private static void checkpointDatabase(Context context) {
        try {
            com.shrimpfarm.app.DatabaseHelper.closeInstance();
            com.shrimpfarm.app.DatabaseHelper helper = new com.shrimpfarm.app.DatabaseHelper(context);
            android.database.sqlite.SQLiteDatabase db = helper.getWritableDatabase();
            if (db != null) {
                db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)");
            }
            helper.close();
        } catch (Exception ignored) {
        } finally {
            com.shrimpfarm.app.DatabaseHelper.closeInstance();
        }
    }

    private static String md5(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                md.update(buf, 0, len);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format(Locale.US, "%02x", b & 0xFF));
        }
        return sb.toString();
    }

    private static boolean verifyLocalBackup(Context context, String exportedPath, long dbSize) {
        try {
            String exportedName = exportedPath.substring(exportedPath.lastIndexOf("/") + 1);
            List<LocalBackupManager.BackupFileInfo> backups = new LocalBackupManager(context).listLocalBackups();
            for (LocalBackupManager.BackupFileInfo info : backups) {
                if (info.name.equals(exportedName) && info.size == dbSize) {
                    return true;
                }
            }
            android.util.Log.w("BgBackup", "本地备份未在列表中找到或大小不符: " + exportedName);
            return false;
        } catch (Exception e) {
            android.util.Log.e("BgBackup", "本地备份校验异常: " + e.getMessage());
            return false;
        }
    }
}