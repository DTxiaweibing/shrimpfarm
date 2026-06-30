package com.shrimpfarm.app.model;

public class NitriteHelper {
    public static double getSafeLimit(int day, double salinity) {
        if (day <= 10) return 0.1;
        if (day <= 30) return 0.1 + (0.31 - 0.1) * (day - 10) / 20.0;
        if (day < 60) return 0.31 + (5.0 - 0.31) * (day - 30) / 30.0;
        return salinity / 2.0;
    }

    public static String getAdvice(double nitrite, int day, double salinity) {
        double limit = getSafeLimit(day, salinity);
        if (nitrite > limit) return "亚盐超标:减料/增氧/改底";
        return "";
    }
}
