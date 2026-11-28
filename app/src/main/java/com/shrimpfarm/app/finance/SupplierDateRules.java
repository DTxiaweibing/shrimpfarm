package com.shrimpfarm.app.finance;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Pure date helpers shared by supplier rest registration and its edit dialog. */
public final class SupplierDateRules {

    private SupplierDateRules() {
    }

    public static String today() {
        return new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(Calendar.getInstance().getTime());
    }

    public static int[] todayParts() {
        Calendar c = Calendar.getInstance();
        return new int[]{c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)};
    }

    /** Invalid dates fall back to today, preserving the original dialog behaviour. */
    public static int[] parseDate(String date) {
        if (date == null) return todayParts();
        String[] parts = date.split("/");
        if (parts.length != 3) return todayParts();
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException e) {
            return todayParts();
        }
    }
}
