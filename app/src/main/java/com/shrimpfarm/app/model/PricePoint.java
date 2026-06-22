package com.shrimpfarm.app.model;

public class PricePoint {
    public String date;
    public float price;
    public PricePoint() {}
    public PricePoint(String date, float price) {
        this.date = date;
        this.price = price;
    }
}
