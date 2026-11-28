package com.shrimpfarm.app.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.utils.StoragePermissionHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class BackgroundBackupManager {

    private static final String TAG = "BgBackup";
    private static final String PREF_NAME = "webdav_config";
    private static final String KEY_LAST_BACKUP_TIME = "last_background_backup_time";
    private static final String KEY_LAST_ATTEMPT_TIME = "last_backup_attempt_time";
    private static final String KEY_LAST_BACKUP_HASH = "last_backup_db_hash";
    private static final String DB_NAME = "FeedingRecord.db";
    private static final long SUCCESS_INTERVAL_MS = 2 * 60 * 60 * 1000;
    private static final long RETRY_COOLDOWN_MS = 10 * 60 * 1000;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    public static void onAppBackground(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        WebDavManager webDav = new WebDavManager(context);
        String username = webDav.getSavedUsername();
        String password = webDav.getSavedPassword();
        boolean cloudConfigured = !username.isEmpty() && !password.isEmpty();
        boolean localAllowed = StoragePermissionHelper.hasStoragePermission(context);
        if (!cloudConfigured && !localAllowed) return;

        long now = System.currentTimeMillis();
        boolean allowed = BackupTimingPolicy.shouldRun(now,
                prefs.getLong(KEY_LAST_BACKUP_TIME, 0),
                prefs.getLong(KEY_LAST_ATTEMPT_TIME, 0),
                SUCCESS_INTERVAL_MS, RETRY_COOLDOWN_MS);
        if (!allowed || !RUNNING.compareAndSet(false, true)) return;

        prefs.edit().putLong(KEY_LAST_ATTEMPT_TIME, now).apply();
        Thread t = new Thread(() -> {
            try {
                runBackup(context, prefs, webDav, username, password,
                        cloudConfigured, localAllowed, now);
            } catch (Exception e) {
                Log.e(TAG, "后台备份失败: " + e.getMessage());
                notifyFailure(context, e);
            } finally {
                RUNNING.set(false);
            }
        }, "BackgroundBackup-run");
        t.setDaemon(true);
        t.start();
    }

    private static void runBackup(Context context, SharedPreferences prefs, WebDavManager webDav,
                                  String username, String password, boolean cloudConfigured,
                                  boolean localAllowed, long now) throws Exception {
        File dbFile = resolveDbFile(context);
        if (dbFile == null) return;
        if (!DatabaseSnapshot.prepare(dbFile)) {
            Log.i(TAG, "数据库正被写入，跳过本次自动备份");
            return;
        }
        String currentHash = md5(dbFile);
        if (currentHash.equals(prefs.getString(KEY_LAST_BACKUP_HASH, ""))) {
            Log.i(TAG, "数据库无变化，跳过自动备份");
            return;
        }
        if (localAllowed) backupLocal(context, dbFile);
        if (cloudConfigured) backupCloud(webDav, username, password, dbFile);

        prefs.edit().putLong(KEY_LAST_BACKUP_TIME, now)
                .putString(KEY_LAST_BACKUP_HASH, currentHash).apply();
        Log.i(TAG, "自动备份全部完成并记录快照");
    }

    private static File resolveDbFile(Context context) {
        File dbFile = context.getDatabasePath(DB_NAME);
        if (dbFile == null || !dbFile.exists()) {
            dbFile = new File(context.getFilesDir().getParent() + "/databases/" + DB_NAME);
        }
        if (!dbFile.exists()) {
            Log.w(TAG, "数据库文件不存在，跳过");
            return null;
        }
        return dbFile;
    }

    private static void backupLocal(Context context, File dbFile) throws Exception {
        String localName = new LocalBackupManager(context).exportToLocal(false);
        if (!verifyLocalBackup(context, localName, dbFile.length())) {
            throw new Exception("本地备份校验失败");
        }
        Log.i(TAG, "本地备份完成: " + localName);
    }

    private static void backupCloud(WebDavManager webDav, String username, String password,
                                    File dbFile) throws Exception {
        webDav.initConnection(username, password);
        String fileName = webDav.uploadBackup(false);
        long remoteSize = webDav.getRemoteFileSize(fileName);
        if (remoteSize < dbFile.length()) {
            throw new Exception("云端备份校验失败，远端大小=" + remoteSize);
        }
        Log.i(TAG, "云端备份完成: " + fileName);
    }

    private static void notifyFailure(Context context, Exception e) {
        String msg = context.getString(R.string.backup_toast_auto_failed) + e.getMessage();
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_LONG).show());
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
            Log.w(TAG, "本地备份未在列表中找到或大小不符: " + exportedName);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "本地备份校验异常: " + e.getMessage());
            return false;
        }
    }
}
