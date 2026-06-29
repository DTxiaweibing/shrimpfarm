package com.shrimpfarm.app.backup;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LocalBackupManager {

    private static final String BACKUP_DIR = "小棚养虾备份";
    private static final String DB_NAME = "FeedingRecord.db";
    private static final int MAX_BACKUPS = 20;

    private final Context context;

    public LocalBackupManager(Context context) {
        this.context = context;
    }

    public File getLegacyBackupDir() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), BACKUP_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public String exportToLocal() throws Exception {
        File dbFile = context.getDatabasePath(DB_NAME);
        if (dbFile == null || !dbFile.exists()) {
            dbFile = new File(context.getFilesDir().getParent() + "/databases/" + DB_NAME);
            if (!dbFile.exists()) throw new Exception("数据库文件不存在");
        }

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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault());
        String fileName = "DataBackup_" + sdf.format(new Date()) + ".db";

        if (Build.VERSION.SDK_INT >= 29) {
            return exportViaMediaStore(dbFile, fileName);
        } else {
            return exportLegacy(dbFile, fileName);
        }
    }

    @SuppressLint("NewApi")
    private String exportViaMediaStore(File dbFile, String fileName) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + BACKUP_DIR);

        Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new Exception("无法创建文件，请确保已授予存储权限");

        try (InputStream in = new FileInputStream(dbFile);
             OutputStream out = context.getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new Exception("无法写入文件");
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush();
        }

        cleanupMediaStoreBackups();
        return "Downloads/" + BACKUP_DIR + "/" + fileName;
    }

    @SuppressLint("NewApi")
    private void cleanupMediaStoreBackups() {
        try {
            String[] projection = new String[]{MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.DATE_MODIFIED,
                    MediaStore.Downloads.RELATIVE_PATH};

            android.database.Cursor cursor = context.getContentResolver().query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection,
                    null, null, MediaStore.Downloads.DATE_MODIFIED + " ASC");

            if (cursor == null) return;

            List<Long> deleteIds = new ArrayList<>();
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME));
                String relPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.RELATIVE_PATH));
                if (name != null && name.startsWith("DataBackup_") && relPath != null && relPath.contains(BACKUP_DIR)) {
                    deleteIds.add(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)));
                }
            }
            cursor.close();

            if (deleteIds.size() <= MAX_BACKUPS) return;
            int toDelete = deleteIds.size() - MAX_BACKUPS;
            for (int i = 0; i < toDelete; i++) {
                Uri fileUri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        String.valueOf(deleteIds.get(i)));
                context.getContentResolver().delete(fileUri, null, null);
            }
        } catch (Exception e) {
            android.util.Log.w("LocalBackup", "清理旧备份失败: " + e.getMessage());
        }
    }

    private String exportLegacy(File dbFile, String fileName) throws Exception {
        File backupDir = getLegacyBackupDir();
        File backupFile = new File(backupDir, fileName);
        copyFile(dbFile, backupFile);
        cleanupOldBackups(backupDir);
        return backupFile.getAbsolutePath();
    }

    public List<BackupFileInfo> listLocalBackups() {
        List<BackupFileInfo> combined = new ArrayList<>();
        List<BackupFileInfo> mediaStoreList = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= 29) {
            mediaStoreList = listViaMediaStore();
            combined.addAll(mediaStoreList);
        }

        List<BackupFileInfo> legacyList = listLegacy();
        for (BackupFileInfo legacy : legacyList) {
            boolean dup = false;
            for (BackupFileInfo existing : combined) {
                if (existing.name.equals(legacy.name)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) combined.add(legacy);
        }

        Collections.sort(combined, (a, b) -> Long.compare(b.date, a.date));
        return combined;
    }

    @SuppressLint("NewApi")
    private List<BackupFileInfo> listViaMediaStore() {
        List<BackupFileInfo> list = new ArrayList<>();
        android.database.Cursor cursor = null;
        try {
            Uri collectionUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String[] projection = new String[]{MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.DATE_MODIFIED,
                    MediaStore.Downloads.SIZE, MediaStore.Downloads.RELATIVE_PATH,
                    MediaStore.Downloads.DATA};
            cursor = context.getContentResolver().query(collectionUri, projection,
                    null, null, MediaStore.Downloads.DATE_MODIFIED + " DESC");

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME));
                    String relPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.RELATIVE_PATH));
                    if (name == null || !name.startsWith("DataBackup_")) continue;
                    if (relPath == null || !relPath.contains(BACKUP_DIR)) continue;
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED));
                    long size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE));
                    File fileRef = null;
                    int dataIdx = cursor.getColumnIndex(MediaStore.Downloads.DATA);
                    if (dataIdx >= 0) {
                        String dataPath = cursor.getString(dataIdx);
                        if (dataPath != null) fileRef = new File(dataPath);
                    }
                    if (fileRef == null) {
                        long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                        list.add(new BackupFileInfo(name, date * 1000, size, id));
                    } else {
                        list.add(new BackupFileInfo(name, date * 1000, fileRef.length(), fileRef));
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("LocalBackup", "MediaStore查询失败: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    private List<BackupFileInfo> listLegacy() {
        List<BackupFileInfo> list = new ArrayList<>();
        scanDirectory(getLegacyBackupDir(), list);
        File altDir = new File(Environment.getExternalStorageDirectory(), "Download/" + BACKUP_DIR);
        if (!altDir.equals(getLegacyBackupDir())) {
            scanDirectory(altDir, list);
        }
        File altDir2 = new File(Environment.getExternalStorageDirectory(), "Downloads/" + BACKUP_DIR);
        if (!altDir2.equals(getLegacyBackupDir()) && !altDir2.equals(altDir)) {
            scanDirectory(altDir2, list);
        }
        return list;
    }

    private void scanDirectory(File dir, List<BackupFileInfo> list) {
        if (!dir.isDirectory()) return;
        File[] files = dir.listFiles((d, name) -> name.startsWith("DataBackup_") && name.endsWith(".db"));
        if (files == null) return;
        for (File f : files) {
            boolean dup = false;
            for (BackupFileInfo existing : list) {
                if (existing.name.equals(f.getName())) { dup = true; break; }
            }
            if (!dup) list.add(new BackupFileInfo(f.getName(), f.lastModified(), f.length(), f));
        }
    }

    public String restoreFromBackup(BackupFileInfo backupInfo) throws Exception {
        File dbFile = context.getDatabasePath(DB_NAME);
        if (!dbFile.exists()) {
            dbFile = new File(context.getFilesDir().getParent() + "/databases/" + DB_NAME);
        }
        if (!dbFile.exists()) throw new Exception("数据库文件不存在");

        com.shrimpfarm.app.DatabaseHelper.closeInstance();

        File dbDir = dbFile.getParentFile();
        String dbName = dbFile.getName();
        File bakFile = new File(dbDir, dbName + ".bak");
        File walFile = new File(dbDir, dbName + "-wal");
        File shmFile = new File(dbDir, dbName + "-shm");

        if (bakFile.exists()) bakFile.delete();
        if (!dbFile.renameTo(bakFile)) throw new Exception("无法备份当前数据库");

        try {
            if (backupInfo.fileRef != null) {
                File srcFile = backupInfo.fileRef;
                if (!srcFile.exists()) throw new Exception("备份文件不存在");
                copyFile(srcFile, dbFile);
            } else if (Build.VERSION.SDK_INT >= 29) {
                Uri uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        String.valueOf(backupInfo.mediaStoreId));
                try (InputStream in = context.getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(dbFile)) {
                    if (in == null) throw new Exception("无法读取备份文件");
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    out.flush();
                }
            } else {
                throw new Exception("不支持在此设备版本上还原");
            }

            if (walFile.exists()) walFile.delete();
            if (shmFile.exists()) shmFile.delete();
            if (bakFile.exists()) bakFile.delete();
        } catch (Exception e) {
            if (dbFile.exists()) dbFile.delete();
            bakFile.renameTo(dbFile);
            throw new Exception("还原失败，已恢复原数据库: " + e.getMessage());
        }

        String batchListJson = "";
        try {
            com.shrimpfarm.app.DatabaseHelper helper = new com.shrimpfarm.app.DatabaseHelper(context);
            batchListJson = helper.getBasicData("_meta", "_batch_list_json");
            helper.close();
        } catch (Exception ignored) {
        } finally {
            com.shrimpfarm.app.DatabaseHelper.closeInstance();
        }
        return batchListJson;
    }

    private void cleanupOldBackups(File backupDir) {
        File[] files = backupDir.listFiles((dir, name) -> name.startsWith("DataBackup_") && name.endsWith(".db"));
        if (files == null || files.length <= MAX_BACKUPS) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < files.length - MAX_BACKUPS; i++) {
            files[i].delete();
        }
    }

    private void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush();
        }
    }

    public static class BackupFileInfo {
        public final String name;
        public final long date;
        public final long size;
        public final long mediaStoreId;
        public final File fileRef;

        BackupFileInfo(String name, long date, long size, long mediaStoreId) {
            this.name = name;
            this.date = date;
            this.size = size;
            this.mediaStoreId = mediaStoreId;
            this.fileRef = null;
        }

        BackupFileInfo(String name, long date, long size, File fileRef) {
            this.name = name;
            this.date = date;
            this.size = size;
            this.mediaStoreId = -1;
            this.fileRef = fileRef;
        }
    }
}
