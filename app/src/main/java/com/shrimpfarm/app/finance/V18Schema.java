package com.shrimpfarm.app.finance;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

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
        ensureColumn(db, "fin_supplier", "supplier_kind", "INTEGER DEFAULT 1");
        ensureColumn(db, "fin_income", "net_fee_mode", "INTEGER DEFAULT 0");
        ensureColumn(db, "fin_income", "net_fee_unit_price", "REAL DEFAULT 0.35");
        ensureColumn(db, "fin_income", "net_fee_payee_id", "INTEGER DEFAULT 0");
        ensureColumn(db, "fin_income", "net_fee_payee_account_id", "INTEGER DEFAULT 0");
        ensureColumn(db, "fin_seed", "pay_account_id", "INTEGER DEFAULT 0");
        ensureColumn(db, "fin_seed", "receive_account_id", "INTEGER DEFAULT 0");
        ensureColumn(db, "fin_seed", "settle_type", "INTEGER DEFAULT 0");
        ensureColumn(db, "fin_seed", "settled", "INTEGER DEFAULT 1");
        ensureColumn(db, "fin_seed", "settle_date", "TEXT");
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
