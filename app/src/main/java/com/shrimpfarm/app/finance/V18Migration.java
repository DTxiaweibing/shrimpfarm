package com.shrimpfarm.app.finance;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.shrimpfarm.app.DatabaseHelper;

public final class V18Migration {

    public static final String FLAG_MIGRATED = "migrated_v18_finance_arap";

    private V18Migration() {
    }

    public static void migrate(SQLiteDatabase db) {
        V18Schema.createAll(db);
        V18Schema.ensureAll(db);
        if ("1".equals(getFlag(db, FLAG_MIGRATED))) {
            return;
        }
        putFlag(db, FLAG_MIGRATED, "1");
    }

    public static String getFlag(SQLiteDatabase db, String key) {
        Cursor c = db.query(DatabaseHelper.TABLE_BASIC_DATA,
                new String[]{DatabaseHelper.COLUMN_BD_VALUE},
                DatabaseHelper.COLUMN_BD_KEY + "=? AND " + DatabaseHelper.COLUMN_BATCH_ID + "=?",
                new String[]{key, ""}, null, null, null);
        try {
            return c.moveToFirst() ? nonNull(c.getString(0)) : "";
        } finally {
            c.close();
        }
    }

    public static void putFlag(SQLiteDatabase db, String key, String value) {
        ContentValues v = new ContentValues();
        v.put(DatabaseHelper.COLUMN_BD_KEY, key);
        v.put(DatabaseHelper.COLUMN_BATCH_ID, "");
        v.put(DatabaseHelper.COLUMN_BD_VALUE, value);
        db.insertWithOnConflict(DatabaseHelper.TABLE_BASIC_DATA, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }
}
