package com.shrimpfarm.app.finance;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.shrimpfarm.app.DatabaseHelper;

public final class V18Schema {

    public static final String TABLE_FIN_AGENT_CUSTOMER = "fin_agent_customer";
    public static final String TABLE_FIN_SETTLEMENT = "fin_settlement";
    public static final String TABLE_FIN_PARK = "fin_park";

    private static final String TAG = "V18Schema";

    private V18Schema() {
    }

    public static void createAll(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FIN_AGENT_CUSTOMER + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "agent_id INTEGER NOT NULL,"
                + "customer_id INTEGER NOT NULL,"
                + "create_time INTEGER DEFAULT 0,"
                + "UNIQUE(agent_id,customer_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_fin_agent_customer_agent ON "
                + TABLE_FIN_AGENT_CUSTOMER + "(agent_id)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FIN_SETTLEMENT + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "direction INTEGER NOT NULL,"
                + "biz_type TEXT NOT NULL,"
                + "biz_id INTEGER NOT NULL,"
                + "amount REAL DEFAULT 0,"
                + "settle_date TEXT,"
                + "pay_account_id INTEGER DEFAULT 0,"
                + "receive_account_id INTEGER DEFAULT 0,"
                + "remark TEXT,"
                + "create_time INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_fin_settlement_biz ON "
                + TABLE_FIN_SETTLEMENT + "(biz_type, biz_id)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FIN_PARK + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT,"
                + "account_name TEXT,"
                + "account_no TEXT,"
                + "enabled INTEGER DEFAULT 1,"
                + "create_time INTEGER DEFAULT 0)");
    }

    public static void ensureAll(SQLiteDatabase db) {
        ensureColumn(db, DatabaseHelper.TABLE_FIN_SUPPLIER, DatabaseHelper.COLUMN_FIN_SUPPLIER_KIND, "INTEGER DEFAULT 1");
        ensureColumn(db, DatabaseHelper.TABLE_FIN_INCOME, DatabaseHelper.COLUMN_FIN_NET_FEE_MODE, "INTEGER DEFAULT 0");
        ensureColumn(db, DatabaseHelper.TABLE_FIN_INCOME, DatabaseHelper.COLUMN_FIN_NET_FEE_UNIT_PRICE, "REAL DEFAULT 0.35");
        ensureColumn(db, DatabaseHelper.TABLE_FIN_INCOME, DatabaseHelper.COLUMN_FIN_NET_FEE_PAYEE_ID, "INTEGER DEFAULT 0");
        ensureColumn(db, DatabaseHelper.TABLE_FIN_INCOME, DatabaseHelper.COLUMN_FIN_NET_FEE_PAYEE_ACCOUNT_ID, "INTEGER DEFAULT 0");
        ensureColumn(db, DatabaseHelper.TABLE_FIN_SEED, DatabaseHelper.COLUMN_FIN_PAY_ACCOUNT_ID, "INTEGER DEFAULT 0");
        ensureColumn(db, DatabaseHelper.TABLE_FIN_SEED, DatabaseHelper.COLUMN_FIN_RECEIVE_ACCOUNT_ID, "INTEGER DEFAULT 0");
        ensureColumn(db, DatabaseHelper.TABLE_FIN_SEED, DatabaseHelper.COLUMN_FIN_SETTLE_TYPE, "INTEGER DEFAULT 0");
        ensureColumn(db, DatabaseHelper.TABLE_FIN_SEED, DatabaseHelper.COLUMN_FIN_SETTLED, "INTEGER DEFAULT 1");
        ensureColumn(db, DatabaseHelper.TABLE_FIN_SEED, DatabaseHelper.COLUMN_FIN_SETTLE_DATE, "TEXT");
    }

    private static void ensureColumn(SQLiteDatabase db, String table, String column, String ddl) {
        if (hasColumn(db, table, column)) return;
        try {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddl);
        } catch (Exception e) {
            String msg = "ensure " + table + "." + column + " failed";
            Log.e(TAG, msg, e);
            throw new RuntimeException(msg, e);
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
