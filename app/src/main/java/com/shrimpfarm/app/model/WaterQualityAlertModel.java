package com.shrimpfarm.app.model;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.shrimpfarm.app.DatabaseHelper;
import com.shrimpfarm.app.utils.EncryptUtils;

import java.util.ArrayList;
import java.util.List;

public class WaterQualityAlertModel {

    public static List<AlertItem> check(SQLiteDatabase db, String batchId,
        boolean enableCore, boolean enableNitrite, boolean enableVibrio,
        boolean enableChlorine, boolean enableH2S, boolean enableORP, boolean enableDO) {
        List<AlertItem> alerts = new ArrayList<>();

        Cursor c = db.query(DatabaseHelper.TABLE_WATER_QUALITY, null,
                "batch_id=?", new String[]{batchId},
                null, null, "date DESC, rowid DESC", "1");

        if (!c.moveToFirst()) { c.close(); return alerts; }

        try {
            double tempC = parseDecrypted(c, c.getColumnIndexOrThrow("max_temp"));
            double pH = parseDecrypted(c, c.getColumnIndexOrThrow("ph"));
            double salinity = parseDecrypted(c, c.getColumnIndexOrThrow("salinity"));
            double tan = parseDecrypted(c, c.getColumnIndexOrThrow("ammonia"));
            double nitrite = parseDecrypted(c, c.getColumnIndexOrThrow("nitrite"));
            double vibrio = parseDecrypted(c, c.getColumnIndexOrThrow("vibrio"));
            double chlorine = parseDecrypted(c, c.getColumnIndexOrThrow("chlorine"));
            double h2s = parseDecrypted(c, c.getColumnIndexOrThrow("hydrogen_sulfide"));
            double orp = parseDecrypted(c, c.getColumnIndexOrThrow("orp"));
            double doValue = parseDecrypted(c, c.getColumnIndexOrThrow("dissolved_oxygen"));
            c.close();

            int day = 0;
            Cursor dc = db.rawQuery("SELECT value FROM basic_data WHERE batch_id=? AND key='stocking_date'", new String[]{batchId});
            if (dc.moveToFirst()) {
                String sd = EncryptUtils.decrypt(dc.getString(0));
                if (sd != null && !sd.isEmpty() && !sd.equals("选择日期")) {
                    try {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault());
                        java.util.Date stocking = sdf.parse(sd);
                        if (stocking != null) {
                            long diff = System.currentTimeMillis() - stocking.getTime();
                            if (diff > 0) day = (int)(diff / (24L * 60 * 60 * 1000)) + 1;
                        }
                    } catch (Exception ignored) {}
                }
            }
            dc.close();

            if (enableCore && tempC > 0 && pH > 0) {
                ShrimpAdviceHelper.AdviceResult result = ShrimpAdviceHelper.getAllAdvice(tempC, pH, salinity, tan, day);
                if (!result.tempAdvice.isEmpty()) alerts.add(new AlertItem(result.tempAdvice, "WQ_TEMP"));
                if (!result.phAdvice.isEmpty()) alerts.add(new AlertItem(result.phAdvice, "WQ_PH"));
                if (!result.nh3Advice.isEmpty()) alerts.add(new AlertItem(result.nh3Advice, "WQ_NH3"));
            }

            if (enableNitrite && nitrite > 0 && day > 0) {
                String advice = NitriteHelper.getAdvice(nitrite, day, salinity);
                if (!advice.isEmpty()) alerts.add(new AlertItem(advice, "WQ_NITRITE"));
            }

            if (enableVibrio && vibrio > 0 && day > 0) {
                String advice = VibrioHelper.getAdvice(vibrio, day);
                if (!advice.isEmpty()) alerts.add(new AlertItem(advice, "WQ_VIBRIO"));
            }

            if (enableChlorine && day > 0) {
                String advice = ChlorineHelper.getAdvice(chlorine, day);
                if (!advice.isEmpty()) alerts.add(new AlertItem(advice, "WQ_CHLORINE"));
            }

            if (enableH2S && h2s > 0) {
                String advice = H2SHelper.getAdvice(h2s);
                if (!advice.isEmpty()) alerts.add(new AlertItem(advice, "WQ_H2S"));
            }

            if (enableORP && orp > 0) {
                String advice = ORPHelper.getAdvice(orp);
                if (!advice.isEmpty()) alerts.add(new AlertItem(advice, "WQ_ORP"));
            }

            if (enableDO && doValue > 0) {
                String advice = DOHelper.getAdvice(doValue);
                if (!advice.isEmpty()) alerts.add(new AlertItem(advice, "WQ_DO"));
            }
        } catch (Exception e) {
            if (!c.isClosed()) c.close();
        }

        return alerts;
    }

    private static double parseDecrypted(Cursor c, int index) {
        if (index < 0) return 0;
        String enc = c.getString(index);
        if (enc == null || enc.isEmpty()) return 0;
        try {
            String dec = EncryptUtils.decrypt(enc);
            if (dec == null || dec.isEmpty()) return 0;
            return Double.parseDouble(dec);
        } catch (Exception e) {
            return 0;
        }
    }
}
