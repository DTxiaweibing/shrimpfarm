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

public final class V18BackupHook {
    private static final String TAG = "V18BackupHook";
    private static final String DB_NAME = "FeedingRecord.db";
    private static final String BACKUP_SUBDIR = "pre_v18";
    private static final long RETAIN_MS = 7L * 24 * 3600 * 1000;

    private V18BackupHook() {
    }

    public static void run(Context context) {
        try {
            File dbFile = context.getDatabasePath(DB_NAME);
            if (dbFile == null || !dbFile.exists()) return;
            int userVersion = readUserVersion(dbFile.getAbsolutePath());
            File dir = new File(new LocalBackupManager(context).getLegacyBackupDir(), BACKUP_SUBDIR);
            safeBackupIfNeeded(dbFile, userVersion, dir, System.currentTimeMillis());
        } catch (Exception e) {
            logW("Pre-v18 backup hook failed", e);
        }
    }

    static boolean safeBackupIfNeeded(File dbFile, int userVersion, File backupDir, long nowMs) {
        try {
            return backupIfNeeded(dbFile, userVersion, backupDir, nowMs) != null;
        } catch (Exception e) {
            logW("Pre-v18 backup hook failed", e);
            return false;
        }
    }

    private static void logW(String msg, Throwable t) {
        try {
            Log.w(TAG, msg, t);
        } catch (Throwable ignored) {
            System.err.println(TAG + ": " + msg);
            if (t != null) t.printStackTrace(System.err);
        }
    }

    static File backupIfNeeded(File dbFile, int userVersion, File backupDir, long nowMs)
            throws Exception {
        if (!needsBackup(userVersion)) return null;
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            logW("Failed to create backup dir: " + backupDir.getAbsolutePath(), null);
            return null;
        }
        if (!DatabaseSnapshot.prepare(dbFile)) {
            logW("WAL snapshot not ready, skipping pre-v18 backup: " + dbFile.getAbsolutePath(), null);
            return null;
        }
        File dst = new File(backupDir, backupFileName(nowMs, userVersion));
        copyDatabase(dbFile, dst);
        cleanupExpired(backupDir, nowMs, RETAIN_MS);
        return dst;
    }

    static boolean needsBackup(int userVersion) {
        return userVersion < 18;
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

    private static String backupFileName(long nowMs, int userVersion) {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(nowMs));
        return "v" + userVersion + "_" + ts + ".db";
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
            if (in != null) try { in.close(); } catch (Exception e) { logW("close in failed", e); }
            if (out != null) try { out.close(); } catch (Exception e) { logW("close out failed", e); }
        }
    }

    private static void cleanupExpired(File dir, long nowMs, long retainMs) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && isExpired(f.lastModified(), nowMs, retainMs) && !f.delete()) {
                logW("Failed to delete expired backup: " + f.getAbsolutePath(), null);
            }
        }
    }
}
