package com.shrimpfarm.app.model;

public interface FeedConversionModel {
    float getStandardFcr(int daysSinceStocking, String shrimpType, String feedType);
    String checkFcrAbnormal(float totalFeedKg, float estimatedYieldKg, int days, String shrimpType, String feedType);
}