package com.shrimpfarm.app.model;

public class DOHelper {
    public static String getAdvice(double doValue) {
        if (doValue < 3) return "溶氧超标:全开增氧机+换水";
        if (doValue < 5) return "溶氧超标:增加增氧机";
        return "";
    }
}
