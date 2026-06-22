package com.shrimpfarm.app.model;

public class ORPHelper {
    public static String getAdvice(double orp) {
        if (orp < 100) return "ORP超标:增氧改底减料";
        if (orp < 150) return "ORP超标:增氧改底";
        return "";
    }
}
