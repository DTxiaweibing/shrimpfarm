package com.shrimpfarm.app.finance;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public final class V17Schema {

    public static final String TABLE_FIN_INCOME_ITEM = "fin_income_item";
    public static final String TABLE_SHED_ARCHIVE = "shed_archive";
    public static final String TABLE_FIN_DOC_SEQ = "fin_doc_seq";

    private static final String TAG = "V17Schema";

    private V17Schema() {
    }

    public static void createAll(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FIN_INCOME_ITEM + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "income_id INTEGER NOT NULL,"
                + "shed_id INTEGER DEFAULT 0,"
                + "shed_text TEXT,"
                + "tail_per_jin REAL DEFAULT 0,"
                + "weight REAL DEFAULT 0,"
                + "price REAL DEFAULT 0,"
                + "amount REAL DEFAULT 0,"
                + "net_fee REAL DEFAULT 0,"
                + "sort_order INTEGER DEFAULT 0,"
                + "spec_range TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_fin_income_item ON "
                + TABLE_FIN_INCOME_ITEM + "(income_id)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SHED_ARCHIVE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "sort_order INTEGER DEFAULT 0,"
                + "enabled INTEGER DEFAULT 1)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_shed_name ON "
                + TABLE_SHED_ARCHIVE + "(name)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FIN_DOC_SEQ + " ("
                + "prefix TEXT, doc_date TEXT, next_seq INTEGER DEFAULT 1,"
                + "PRIMARY KEY (prefix, doc_date))");
    }

    public static void ensureAll(SQLiteDatabase db) {
        ensureColumn(db, "fin_income", "doc_no", "TEXT");
        ensureColumn(db, "fin_purchase_order", "pay_type", "INTEGER DEFAULT 0");
        ensureColumn(db, "fin_purchase_order", "settle_date", "TEXT");
        ensureColumn(db, "fin_purchase_order", "settle_account_id", "INTEGER DEFAULT 0");
        ensureColumn(db, "fin_purchase_order", "supplier_doc_no", "TEXT");
        ensureColumn(db, "fin_purchase_order", "supplier_doc_date", "TEXT");
        ensureColumn(db, "fin_expense", "payee_id", "INTEGER DEFAULT 0");
        ensureColumn(db, "fin_expense", "account_id", "INTEGER DEFAULT 0");
        ensureColumn(db, "fin_expense", "doc_no", "TEXT");
        ensureColumn(db, "fin_wage", "doc_no", "TEXT");
        ensureColumn(db, "fin_supplier", "address", "TEXT");
        ensureColumn(db, "fin_supplier", "bank_account", "TEXT");
        ensureColumn(db, "fin_supplier", "bank_name", "TEXT");
    }

    private static void ensureColumn(SQLiteDatabase db, String table, String column, String ddl) {
        if (hasColumn(db, table, column)) return;
        try {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddl);
        } catch (Exception e) {
            Log.w(TAG, "ensure " + table + "." + column, e);
        }
    }

    private static boolean hasColumn(SQLiteDatabase db, String table, String column) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            int nameIdx = c.getColumnIndex("name");
            while (c.moveToNext()) {
                if (column.equalsIgnoreCase(c.getString(nameIdx))) return true;
            }
            return false;
        } finally {
            c.close();
        }
    }
}
