package com.shrimpfarm.app.model;

public class ChlorineHelper {
    public static double getSafeLimit(int day) {
        if (day <= 10) return 0.0;
        if (day <= 45) return 0.3 * (day - 10) / 35.0;
        return 0.3;
    }

    public static String getAdvice(double chlorine, int day) {
        double limit = getSafeLimit(day);
        if (day <= 10 && chlorine > 0) return "余氯超标:曝气/硫代硫酸钠";
        if (chlorine > limit) return "余氯超标:泼大苏打";
        return "";
    }
}
