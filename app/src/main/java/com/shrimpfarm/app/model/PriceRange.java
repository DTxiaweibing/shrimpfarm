package com.shrimpfarm.app.model;

/** 一条行情的价格区间，low/high 已保证 low <= high。 */
public final class PriceRange {

    public final float low;
    public final float high;

    public PriceRange(float low, float high) {
        this.low = Math.min(low, high);
        this.high = Math.max(low, high);
    }

    public float mid() {
        return (low + high) / 2f;
    }
}
