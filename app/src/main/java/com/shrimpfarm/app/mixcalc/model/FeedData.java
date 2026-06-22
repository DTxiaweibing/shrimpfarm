package com.shrimpfarm.app.mixcalc.model;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

public class FeedData {
    private String shedNumber;
    private int shedCount;
    private double average;
    private double fermentedFeed;
    private double powderFeed;
    private double feed03;
    private double feed05;
    private double feed10;
    private double water;
    private double weighedFeed;

    private int defaultShedCount = 0;
    private boolean isShedCountManuallyModified = false;

    public FeedData() {
        this.shedNumber = "";
        this.shedCount = 0;
        this.average = 0;
        this.fermentedFeed = 0;
        this.powderFeed = 0;
        this.feed03 = 0;
        this.feed05 = 0;
        this.feed10 = 0;
        this.water = 0;
        this.weighedFeed = 0;
        this.isShedCountManuallyModified = false;
    }

    public void updateAllCalculations(int waterPercentage) {
        updateAllCalculations(waterPercentage, false, false);
    }
    
    public void updateAllCalculations(int waterPercentage, boolean fermentedIncludedInWater) {
        updateAllCalculations(waterPercentage, fermentedIncludedInWater, false);
    }

    public void setShedCount(double value) {
        setShedCount((int)value);
    }

    public void setShedCountDirectly(int shedCount, boolean manuallyModified) {
        this.shedCount = shedCount;
        this.isShedCountManuallyModified = manuallyModified;
    }

    public JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("shedNumber", shedNumber != null ? shedNumber : "");
            json.put("shedCount", shedCount);
            json.put("average", average);
            json.put("fermentedFeed", fermentedFeed);
            json.put("powderFeed", powderFeed);
            json.put("feed03", feed03);
            json.put("feed05", feed05);
            json.put("feed10", feed10);
            json.put("water", water);
            json.put("weighedFeed", weighedFeed);
            json.put("defaultShedCount", defaultShedCount);
            json.put("isShedCountManuallyModified", isShedCountManuallyModified);
            return json;
        } catch (JSONException e) {
            Log.e("FeedData", "转换为JSON异常: " + e.getMessage());
            return new JSONObject();
        }
    }

    public void fromJson(JSONObject json) {
        try {
            if (json != null) {
                shedNumber = json.optString("shedNumber", "");
                shedCount = json.optInt("shedCount", 0);
                average = json.optDouble("average", 0);
                fermentedFeed = json.optDouble("fermentedFeed", 0);
                powderFeed = json.optDouble("powderFeed", 0);
                feed03 = json.optDouble("feed03", 0);
                feed05 = json.optDouble("feed05", 0);
                feed10 = json.optDouble("feed10", 0);
                water = json.optDouble("water", 0);
                weighedFeed = json.optDouble("weighedFeed", 0);
                defaultShedCount = json.optInt("defaultShedCount", 0);
                isShedCountManuallyModified = json.optBoolean("isShedCountManuallyModified", false);
            }
        } catch (Exception e) {
            Log.e("FeedData", "从JSON恢复数据异常: " + e.getMessage());
        }
    }

    public void calculateShedCount() {
        if (isShedCountManuallyModified) {
            return;
        }
        if (shedNumber == null || shedNumber.trim().isEmpty()) {
            this.shedCount = 0;
            this.defaultShedCount = 0;
            return;
        }
        String cleanNumber = shedNumber.trim();
        this.shedCount = 0;
        this.defaultShedCount = 0;
        cleanNumber = cleanNumber.replace("，", ",")
            .replace("、", ",")
            .replace(" ", ",")
            .replace(",,", ",")
            .replace(",,", ",");
        String[] parts = cleanNumber.split(",");
        int totalCount = 0;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            int partCount = parsePartCount(part);
            totalCount += partCount;
        }
        this.shedCount = Math.max(0, totalCount);
        this.defaultShedCount = this.shedCount;
    }

    private int parsePartCount(String part) {
        if (part == null || part.trim().isEmpty()) {
            return 0;
        }
        if (part.contains("-") || part.contains("到") || part.contains("至") || part.contains("~") || part.contains("—")) {
            String rangePart = part.replace("到", "-")
                .replace("至", "-")
                .replace("~", "-")
                .replace("—", "-")
                .replace("号到", "-")
                .replace("号至", "-");
            String[] rangeParts = rangePart.split("-", 2);
            if (rangeParts.length == 2) {
                try {
                    String startStr = rangeParts[0].replaceAll("[^0-9]", "");
                    String endStr = rangeParts[1].replaceAll("[^0-9]", "");
                    if (!startStr.isEmpty() && !endStr.isEmpty()) {
                        int start = Integer.parseInt(startStr);
                        int end = Integer.parseInt(endStr);
                        if (start > 0 && end > 0) {
                            int result = (start <= end) ? (end - start + 1) : (start - end + 1);
                            return result;
                        }
                    }
                } catch (NumberFormatException e) {
                }
            }
        }
        if (isValidNumber(part)) {
            return 1;
        }
        return 0;
    }

    private boolean isValidNumber(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        try {
            String numStr = str.replaceAll("[^0-9]", "");
            return !numStr.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public void calculateAverage(boolean fermentedIncludedInAverage) {
        double sum = powderFeed + feed03 + feed05 + feed10;
        if (fermentedIncludedInAverage) {
            sum += fermentedFeed;
        }
        this.average = shedCount > 0 ? sum / shedCount : 0;
    }

    public void calculateWater(int waterPercentage, boolean fermentedIncludedInWater) {
        double sum = powderFeed + feed03 + feed05 + feed10;
        if (fermentedIncludedInWater) {
            sum += fermentedFeed;
        }
        this.water = sum * waterPercentage / 100.0;
    }

    public void calculateWeighedFeed(boolean fermentedIncludedInWater) {
        double totalWeight = powderFeed + feed03 + feed05 + feed10 + water;
        if (fermentedIncludedInWater) {
            totalWeight += fermentedFeed;
        } else {
            if (fermentedFeed > 0) {
                totalWeight += fermentedFeed;
            }
        }
        this.weighedFeed = shedCount > 0 ? totalWeight / shedCount : 0;
    }

    public void updateAllCalculations(int waterPercentage, boolean fermentedIncludedInWater, boolean fermentedIncludedInAverage) {
        if (!isShedCountManuallyModified) {
            calculateShedCount();
        }
        calculateAverage(fermentedIncludedInAverage);
        calculateWater(waterPercentage, fermentedIncludedInWater);
        calculateWeighedFeed(fermentedIncludedInWater);
    }

    public String getShedNumber() {
        return shedNumber != null ? shedNumber : "";
    }

    public void setShedNumber(String shedNumber) {
        this.shedNumber = shedNumber != null ? shedNumber : "";
        if (!isShedCountManuallyModified) {
            calculateShedCount();
        }
    }

    public int getShedCount() {
        return shedCount;
    }

    public void setShedCount(int shedCount) {
        this.shedCount = shedCount;
        this.isShedCountManuallyModified = true;
    }

    public int getDefaultShedCount() {
        return defaultShedCount;
    }

    public double getAverage() {
        return average;
    }

    public double getAverage(boolean fermentedIncludedInAverage) {
        double sum = powderFeed + feed03 + feed05 + feed10;
        if (fermentedIncludedInAverage) {
            sum += fermentedFeed;
        }
        return shedCount > 0 ? sum / shedCount : 0;
    }

    public void setAverage(double average) {
        this.average = average;
    }

    public double getFermentedFeed() {
        return fermentedFeed;
    }

    public void setFermentedFeed(double fermentedFeed) {
        this.fermentedFeed = fermentedFeed;
    }

    public double getPowderFeed() {
        return powderFeed;
    }

    public void setPowderFeed(double powderFeed) {
        this.powderFeed = powderFeed;
    }

    public double getFeed03() {
        return feed03;
    }

    public void setFeed03(double feed03) {
        this.feed03 = feed03;
    }

    public double getFeed05() {
        return feed05;
    }

    public void setFeed05(double feed05) {
        this.feed05 = feed05;
    }

    public double getFeed10() {
        return feed10;
    }

    public void setFeed10(double feed10) {
        this.feed10 = feed10;
    }

    public double getWater() {
        return water;
    }

    public void setWater(double water) {
        this.water = water;
    }

    public double getWeighedFeed() {
        return weighedFeed;
    }

    public void setWeighedFeed(double weighedFeed) {
        this.weighedFeed = weighedFeed;
    }

    public boolean isShedCountManuallyModified() {
        return isShedCountManuallyModified;
    }

    public void setShedCountManuallyModified(boolean manuallyModified) {
        this.isShedCountManuallyModified = manuallyModified;
    }

    public boolean isShedCountValid() {
        return shedCount > 0;
    }

    public String getShedCountDisplay() {
        return shedCount == 0 ? "" : String.valueOf(shedCount);
    }

    public String getAverageDisplay() {
        return average == 0 ? "" : formatDisplayNumber(average);
    }

    public String getFermentedFeedDisplay() {
        return fermentedFeed == 0 ? "" : formatDisplayNumber(fermentedFeed);
    }

    public String getPowderFeedDisplay() {
        return powderFeed == 0 ? "" : formatDisplayNumber(powderFeed);
    }

    public String getFeed03Display() {
        return feed03 == 0 ? "" : formatDisplayNumber(feed03);
    }

    public String getFeed05Display() {
        return feed05 == 0 ? "" : formatDisplayNumber(feed05);
    }

    public String getFeed10Display() {
        return feed10 == 0 ? "" : formatDisplayNumber(feed10);
    }

    public String getWaterDisplay() {
        return water == 0 ? "" : formatDisplayNumber(water);
    }

    public String getWeighedFeedDisplay() {
        if (weighedFeed == 0 || shedCount == 0) {
            return "";
        }
        return formatDisplayNumber(weighedFeed) + "×" + shedCount;
    }

    private String formatDisplayNumber(double value) {
        if (value == (int) value) {
            return String.valueOf((int) value);
        } else {
            return String.format(java.util.Locale.ROOT, "%.2f", value);
        }
    }
}
