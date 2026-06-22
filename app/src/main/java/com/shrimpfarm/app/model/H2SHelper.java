package com.shrimpfarm.app.model;

public class H2SHelper {
    public static String getAdvice(double h2s) {
        if (h2s > 0.05) return "硫化氢超标:改底增氧硫酸亚铁";
        if (h2s > 0.01) return "硫化氢超标:加强改底增氧";
        return "";
    }
}
