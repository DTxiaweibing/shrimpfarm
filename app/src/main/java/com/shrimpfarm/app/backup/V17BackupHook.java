package com.shrimpfarm.app.backup;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class V17BackupHook {
    private static final String TAG = "V17BackupHook";
    private static final String DB_NAME = "FeedingRecord.db";
    private static final String BACKUP_SUBDIR = "pre_v17";
    private static final long RETAIN_MS = 7L * 24 * 3600 * 1000;

    private V17BackupHook() {
    }

    public static void run(Context context) {
        try {
            File dbFile = context.getDatabasePath(DB_NAME);
            if (dbFile == null || !dbFile.exists()) return;
            int userVersion = readUserVersion(dbFile.getAbsolutePath());
            if (!needsBackup(userVersion)) return;
            File dir = new File(new LocalBackupManager(context).getLegacyBackupDir(), BACKUP_SUBDIR);
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "Failed to create backup dir: " + dir.getAbsolutePath());
                return;
            }
            copyDatabase(dbFile, new File(dir, backupFileName()));
            cleanupExpired(dir, System.currentTimeMillis(), RETAIN_MS);
        } catch (Exception e) {
            Log.w(TAG, "Pre-v17 backup hook failed", e);
        }
    }

    static boolean needsBackup(int userVersion) {
        return userVersion < 17;
    }

    static boolean isExpired(long fileTime, long nowMs, long retainMs) {
        return nowMs - fileTime > retainMs;
    }

    private static int readUserVersion(String path) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
            cursor = db.rawQuery("PRAGMA user_version", null);
            if (cursor.moveToFirst()) return cursor.getInt(0);
            return 0;
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }
    }

    private static String backupFileName() {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return "v16_" + ts + ".db";
    }

    private static void copyDatabase(File src, File dst) throws Exception {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.getFD().sync();
        } finally {
            if (in != null) try { in.close(); } catch (Exception e) { Log.w(TAG, "close in failed", e); }
            if (out != null) try { out.close(); } catch (Exception e) { Log.w(TAG, "close out failed", e); }
        }
    }

    private static void cleanupExpired(File dir, long nowMs, long retainMs) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && isExpired(f.lastModified(), nowMs, retainMs) && !f.delete()) {
                Log.w(TAG, "Failed to delete expired backup: " + f.getAbsolutePath());
            }
        }
    }
}
