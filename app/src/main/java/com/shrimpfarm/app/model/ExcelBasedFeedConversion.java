package com.shrimpfarm.app.model;

public class ExcelBasedFeedConversion implements FeedConversionModel {

    private static final float[][] PARAMS = {
        {1.00f, 1.30f},
        {1.10f, 1.35f},
        {1.10f, 1.35f},
        {1.15f, 1.40f}
    };

    private boolean containsKeyword(String text, String... keywords) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s", "");
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s", ""))) {
                return true;
            }
        }
        return false;
    }

    private int getGroupIndex(String shrimpType, String feedType) {
        boolean isHaiDaShrimp = containsKeyword(shrimpType, "海大", "海伕");
        boolean isHaiShengShrimp = containsKeyword(shrimpType, "海生", "海升", "海晟");
        boolean isAoHuaFeed = containsKeyword(feedType, "澳华", "傲华", "奥华", "粤华");

        if (isHaiDaShrimp && isAoHuaFeed) return 0;
        if (isHaiDaShrimp && !isAoHuaFeed) return 1;
        if (isHaiShengShrimp && isAoHuaFeed) return 2;
        return 3;
    }

    @Override
    public float getStandardFcr(int daysSinceStocking, String shrimpType, String feedType) {
        int group = getGroupIndex(shrimpType, feedType);
        float[] params = PARAMS[group];
        float phase2End = params[0];
        float phase3End = params[1];

        if (daysSinceStocking <= 35) {
            return 0.85f;
        }
        if (daysSinceStocking <= 90) {
            float progress = (float)(daysSinceStocking - 35) / 55f;
            return 0.85f + (phase2End - 0.85f) * progress;
        }
        if (daysSinceStocking <= 150) {
            float progress = (float)(daysSinceStocking - 90) / 60f;
            return phase2End + (phase3End - phase2End) * progress;
        }
        return phase3End;
    }

    @Override
    public String checkFcrAbnormal(float totalFeedJin, float estimatedYieldJin, int days, String shrimpType, String feedType) {
        if (estimatedYieldJin <= 0 || days <= 0) return null;
        float actualFcr = totalFeedJin / estimatedYieldJin;
        float standardFcr = getStandardFcr(days, shrimpType, feedType);
        if (actualFcr > standardFcr * 1.15f) {
            return String.format(java.util.Locale.ROOT, "当前料比 %.2f 显著高于标准 %.2f，建议检查虾体健康和投喂策略。", actualFcr, standardFcr);
        }
        return null;
    }

    public float estimateYield(float totalFeedJin, int daysSinceStocking, String shrimpType, String feedType) {
        float standardFcr = getStandardFcr(daysSinceStocking, shrimpType, feedType);
        return totalFeedJin / standardFcr;
    }
}