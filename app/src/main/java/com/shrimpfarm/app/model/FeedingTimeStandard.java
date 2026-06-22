package com.shrimpfarm.app.model;

public class FeedingTimeStandard {
    public static long getStandardSeconds(int dayIndex, boolean isFourMeals) {
        final long S1 = 7200L;
        final long E1 = 2100L;
        final long E2 = isFourMeals ? 3600L : 5400L;

        if (dayIndex <= 0) return S1;

        if (dayIndex <= 30) {
            double fraction = (double) (dayIndex - 1) / 29.0;
            return Math.round(S1 + (E1 - S1) * fraction);
        } else if (dayIndex <= 55) {
            double fraction = (double) (dayIndex - 30) / 25.0;
            return Math.round(E1 + (E2 - E1) * fraction);
        } else {
            return E2;
        }
    }
}
