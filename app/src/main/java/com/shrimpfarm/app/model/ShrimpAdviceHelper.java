package com.shrimpfarm.app.model;

public class ShrimpAdviceHelper {

    private static double getSalinityFactor(double salinity) {
        if (salinity < 5) return 0.45;
        if (salinity < 10) return 0.65;
        if (salinity < 20) return 0.85;
        return 1.0;
    }

    private static double getBaseSafeNH3(int day) {
        if (day <= 15) return 0.05;
        if (day <= 45) {
            double t = (day - 15) / 30.0;
            return 0.05 + t * (0.20 - 0.05);
        }
        if (day <= 60) {
            double t = (day - 45) / 15.0;
            return 0.20 + t * (0.35 - 0.20);
        }
        return 0.35;
    }

    private static double getFinalSafeNH3(double salinity, int day) {
        double base = getBaseSafeNH3(day);
        double factor = getSalinityFactor(salinity);
        return base * factor;
    }

    private static double calcPKa(double tempC) {
        double Tk = tempC + 273.15;
        return 0.09018 + (2729.92 / Tk);
    }

    private static double calcCurrentNH3(double pH, double tempC, double tan, double salinity) {
        double pKa = calcPKa(tempC);
        double ratio = Math.pow(10, pKa - pH) + 1;
        return tan / ratio;
    }

    public static String getTempAdvice(double tempC) {
        if (tempC > 32) {
            return "温度超标:加大增氧、遮阳、加深水位";
        }
        if (tempC < 22) {
            return "温度超标:请加温至22℃以上";
        }
        return "";
    }

    public static String getPhAdvice(double pH) {
        if (pH >= 9.0) {
            return "pH超标:遮光+芽孢光合+乳酸菌100斤/亩";
        }
        if (pH >= 8.8) {
            return "pH超标:遮光+芽孢光合+乳酸菌50斤/亩";
        }
        if (pH >= 8.7) {
            return "pH超标:遮光控藻";
        }
        if (pH <= 8.0) {
            return "pH超标:泼洒生石灰5-10kg/亩";
        }
        return "";
    }

    public static String getNh3Advice(double pH, double tempC, double tan, double salinity, int day) {
        double safeNH3 = getFinalSafeNH3(salinity, day);
        double currentNH3 = calcCurrentNH3(pH, tempC, tan, salinity);
        if (currentNH3 > safeNH3) {
            String advice = String.format(java.util.Locale.ROOT, "氨氮超标:NH₃ %.4f mg/L，光合菌+红糖，减料增氧", currentNH3);
            if (pH <= 8.0 && tan > 1.0) {
                advice += "，小球藻种+磷酸二氢钾培藻";
            }
            return advice;
        }
        return "";
    }

    public static AdviceResult getAllAdvice(double tempC, double pH, double salinity, double tan, int day) {
        AdviceResult result = new AdviceResult();
        result.tempAdvice = getTempAdvice(tempC);
        result.phAdvice = getPhAdvice(pH);
        result.nh3Advice = getNh3Advice(pH, tempC, tan, salinity, day);
        return result;
    }

    public static class AdviceResult {
        public String tempAdvice = "";
        public String phAdvice = "";
        public String nh3Advice = "";
    }
}
