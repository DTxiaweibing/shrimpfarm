package com.shrimpfarm.app.model;

/**
 * 海水密度与盐度计算工具（EOS-80 标准）
 * 温度范围 0~40℃，盐度范围 0~40PSU
 */
public class SeawaterHelper {

    /**
     * 计算纯水密度 (kg/m³)
     * 温度范围 0~40℃
     */
    private static double pureWaterDensity(double tempC) {
        double T = tempC;
        return 999.842594
                + 6.793952e-2 * T
                - 9.095290e-3 * T * T
                + 1.001685e-4 * T * T * T
                - 1.120083e-6 * T * T * T * T
                + 6.536336e-9 * T * T * T * T * T;
    }

    /**
     * 计算海水密度 (kg/m³)
     * @param tempC 温度 (℃)
     * @param salinity 实用盐度 (PSU, 0~40)
     * @return 密度 (kg/m³)
     */
    public static double calcDensity(double tempC, double salinity) {
        if (salinity < 0) salinity = 0;
        if (salinity > 40) salinity = 40;
        double T = tempC;
        double S = salinity;
        double rho_w = pureWaterDensity(T);
        double A = 0.824493
                - 4.0899e-3 * T
                + 7.6438e-5 * T * T
                - 8.2467e-7 * T * T * T
                + 5.3875e-9 * T * T * T * T;
        double B = -5.72466e-3
                + 1.0227e-4 * T
                - 1.6546e-6 * T * T;
        double C = 4.8314e-4;
        return rho_w + A * S + B * Math.pow(S, 1.5) + C * S * S;
    }

    /**
     * 根据密度反推盐度 (二分法迭代)
     * @param tempC 温度 (℃)
     * @param targetDensity 目标密度 (kg/m³)
     * @return 盐度 (PSU, 0~40)
     */
    /**
     * 格 → 实用盐度 (PSU)
     * 格的定义：18℃时比重计读数 × 1000（如比重 1.020 → 20格）
     * 20格 = 18℃时比重 1.020 = 28.104 PSU
     */
    public static double geToSalinity(double ge) {
        double targetDensity = 1000.0 + ge;
        return solveSalinity(18.0, targetDensity);
    }

    public static double solveSalinity(double tempC, double targetDensity) {
        double S_low = 0.0;
        double S_high = 40.0;
        double rho_low = calcDensity(tempC, S_low);
        double rho_high = calcDensity(tempC, S_high);

        if (targetDensity <= rho_low) return 0.0;
        if (targetDensity >= rho_high) return 40.0;

        double S_mid = 0;
        for (int i = 0; i < 100; i++) {
            S_mid = (S_low + S_high) / 2.0;
            double rho_mid = calcDensity(tempC, S_mid);
            if (rho_mid < targetDensity) {
                S_low = S_mid;
            } else {
                S_high = S_mid;
            }
            if (S_high - S_low < 1e-7) break;
        }
        return (S_low + S_high) / 2.0;
    }
}