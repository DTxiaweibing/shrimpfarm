package com.shrimpfarm.app.backup;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.Closeable;
import java.io.File;

/**
 * 备份前的 WAL 落盘门禁。刻意使用独立连接，绝不关闭全局 DatabaseHelper ——
 * 其他线程可能正持有它的 SQLiteDatabase 并在事务中，close 会让 endTransaction 崩掉。
 */
public final class DatabaseSnapshot {

    private static final String TAG = "DbSnapshot";

    private DatabaseSnapshot() {
    }

    /** true 表示主库文件可复制；false 表示数据库正被占用，调用方应跳过本次备份。 */
    public static boolean prepare(File dbFile) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
            cursor = db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null);
            if (!cursor.moveToFirst()) return true;
            return DatabaseSnapshotGate.allowSnapshot(
                    cursor.getInt(0), cursor.getInt(1), cursor.getInt(2));
        } catch (Exception e) {
            Log.w(TAG, "WAL checkpoint 失败，跳过本次备份: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(cursor);
            closeQuietly(db);
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception e) {
            Log.w(TAG, "关闭快照连接失败: " + e.getMessage());
        }
    }
}
