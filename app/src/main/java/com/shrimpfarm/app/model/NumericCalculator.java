package com.shrimpfarm.app.model;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NumericCalculator {

    public static String calculate(String query) {
        Matcher geMatcher = Pattern.compile("(\\d+(\\.\\d+)?)\\s*格").matcher(query);
        Matcher tempMatcher = Pattern.compile("(\\d+(\\.\\d+)?)\\s*度").matcher(query);
        double temp = 25;
        if (tempMatcher.find()) {
            temp = Double.parseDouble(tempMatcher.group(1));
        }
        if (geMatcher.find() && (query.contains("盐") || query.contains("咸") || query.contains("密度") || query.contains("比重"))) {
            double ge = Double.parseDouble(geMatcher.group(1));
            double salinity = SeawaterHelper.geToSalinity(ge);
            double density = SeawaterHelper.calcDensity(temp, salinity);
            return String.format(Locale.ROOT,
                    "🌊 %.0f格 = %.3f PSU，%.0f℃时密度 %.2f kg/m³",
                    ge, salinity, temp, density);
        }
        if (query.contains("盐度") && (query.contains("密度") || query.contains("比重"))) {
            Matcher salMatcher = Pattern.compile("盐度[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
            if (salMatcher.find()) {
                double salinity = Double.parseDouble(salMatcher.group(1));
                double density = SeawaterHelper.calcDensity(temp, salinity);
                return String.format(Locale.ROOT,
                        "🌊 盐度 %.1f PSU，%.0f℃时密度 %.2f kg/m³",
                        salinity, temp, density);
            }
        }
        if ((query.contains("密度") || query.contains("比重")) && query.contains("盐度")) {
            Matcher denMatcher = Pattern.compile("(密度|比重)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
            if (denMatcher.find()) {
                double density = Double.parseDouble(denMatcher.group(2));
                double salinity = SeawaterHelper.solveSalinity(temp, density);
                return String.format(Locale.ROOT,
                        "🌊 密度 %.2f kg/m³，%.0f℃时盐度 %.3f PSU",
                        density, temp, salinity);
            }
        }
        return null;
    }
}
