package com.shrimpfarm.app.model;

public class PricePoint {
    public String date;
    /** 区间中值，折线图使用。 */
    public float price;
    public float low;
    public float high;

    public PricePoint() {
    }

    public PricePoint(String date, float low, float high) {
        this.date = date;
        this.low = Math.min(low, high);
        this.high = Math.max(low, high);
        this.price = (this.low + this.high) / 2f;
    }
}
