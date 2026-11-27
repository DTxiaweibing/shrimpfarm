package com.shrimpfarm.app.finance;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import com.shrimpfarm.app.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class V17Migration {

    public static final String FLAG_MIGRATED = "migrated_v17_income_items";
    public static final String FLAG_FIXUP_JSON = "v17_fixup_json";

    private V17Migration() {
    }

    public static void migrate(SQLiteDatabase db) {
        V17Schema.createAll(db);
        V17Schema.ensureAll(db);
        Map<String, V17Reconcile.Snapshot> before = V17Reconcile.snapshotIncome(db);
        if ("1".equals(getFlag(db, FLAG_MIGRATED))
                || tableCount(db, V17Schema.TABLE_FIN_INCOME_ITEM) > 0) {
            return;
        }
        String fixupJson = backfillIncomeItems(db);
        initDocSeq(db);
        V17Reconcile.reconcileV17(db, before);
        putFlag(db, FLAG_FIXUP_JSON, fixupJson);
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
        db.insertWithOnConflict(DatabaseHelper.TABLE_BASIC_DATA, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static String backfillIncomeItems(SQLiteDatabase db) {
        SQLiteStatement ins = db.compileStatement(
                "INSERT INTO fin_income_item(income_id,shed_id,shed_text,tail_per_jin,"
                        + "weight,price,amount,net_fee,sort_order,spec_range)"
                        + " VALUES (?,?,?,?,?,?,?,?,0,?)");
        Cursor c = db.rawQuery("SELECT id,shed_no,specs,weight,price,net_fee,amount "
                + "FROM fin_income ORDER BY id", null);
        List<String> fixups = new ArrayList<>();
        try {
            db.beginTransaction();
            try {
                while (c.moveToNext()) insertIncomeItem(db, ins, c, fixups);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            c.close();
            ins.close();
        }
        return "[" + String.join(",", fixups) + "]";
    }

    private static void insertIncomeItem(SQLiteDatabase db, SQLiteStatement ins,
                                         Cursor c, List<String> fixups) {
        long id = c.getLong(0);
        String shed = nonNull(c.getString(1));
        String specs = nonNull(c.getString(2));
        double weight = c.getDouble(3);
        double price = c.getDouble(4);
        double netFee = c.getDouble(5);
        double amount = c.getDouble(6);
        V17Parse.SpecParse sp = V17Parse.parseSpec(specs);
        bindIncomeItem(ins, id, resolveSingleShed(db, shed), shed, sp, weight, price, amount, netFee);
        ins.executeInsert();
        collectFixups(fixups, id, specs, weight, price, netFee, amount, sp.value);
    }

    private static void bindIncomeItem(SQLiteStatement ins, long id, long shedId, String shed,
                                       V17Parse.SpecParse sp, double weight, double price,
                                       double amount, double netFee) {
        ins.clearBindings();
        ins.bindLong(1, id);
        ins.bindLong(2, shedId);
        ins.bindString(3, shed);
        ins.bindDouble(4, sp.value);
        ins.bindDouble(5, weight);
        ins.bindDouble(6, price);
        ins.bindDouble(7, amount);
        ins.bindDouble(8, netFee);
        ins.bindString(9, sp.range);
    }

    private static void collectFixups(List<String> fixups, long id, String specs,
                                      double weight, double price, double netFee,
                                      double amount, double tail) {
        if (tail == 0 && !specs.trim().isEmpty()) {
            fixups.add(String.format(Locale.CHINA,
                    "{\"income\":%d,\"issue\":\"spec_unparsed\",\"detail\":\"%s\"}",
                    id, esc(specs)));
        }
        if (V17Parse.needsAmountFixup(amount, weight, price)) {
            fixups.add(String.format(Locale.CHINA,
                    "{\"income\":%d,\"issue\":\"amount_zero\",\"detail\":\"%.2f×%.2f\"}",
                    id, weight, price));
        }
        if (netFee == 0 && weight > 0) {
            fixups.add(String.format(Locale.CHINA,
                    "{\"income\":%d,\"issue\":\"net_fee_zero\",\"detail\":\"%.2f\"}",
                    id, weight));
        }
    }

    private static void initDocSeq(SQLiteDatabase db) {
        Cursor c = db.rawQuery(
                "SELECT substr(order_no,3,8) d, MAX(CAST(substr(order_no,11) AS INTEGER)) n "
                        + "FROM fin_purchase_order WHERE order_no LIKE 'JR%' GROUP BY d", null);
        try {
            while (c.moveToNext()) insertDocSeq(db, "JR", nonNull(c.getString(0)), c.getInt(1));
        } finally {
            c.close();
        }
    }

    private static void insertDocSeq(SQLiteDatabase db, String prefix, String docDate, int seq) {
        db.execSQL("INSERT OR REPLACE INTO fin_doc_seq(prefix,doc_date,next_seq) VALUES (?,?,?)",
                new Object[]{prefix, docDate, seq + 1});
    }

    private static long resolveSingleShed(SQLiteDatabase db, String shedNo) {
        int n = V17Parse.singleShedNumber(shedNo);
        return n > 0 ? ensureShedArchive(db, n) : 0;
    }

    private static long ensureShedArchive(SQLiteDatabase db, int n) {
        String name = n + "号棚";
        Cursor c = db.rawQuery("SELECT id FROM shed_archive WHERE name=?",
                new String[]{name});
        try {
            if (c.moveToFirst()) return c.getLong(0);
        } finally {
            c.close();
        }
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("enabled", 1);
        v.put("sort_order", n);
        return db.insert("shed_archive", null, v);
    }

    private static int tableCount(SQLiteDatabase db, String table) {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' || ch == '\\') sb.append('\\').append(ch);
            else if (ch == '\n') sb.append("\\n");
            else if (ch == '\r') sb.append("\\r");
            else if (ch == '\t') sb.append("\\t");
            else if (ch < 0x20) sb.append(String.format(Locale.US, "\\u%04x", (int) ch));
            else sb.append(ch);
        }
        return sb.toString();
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }
}
