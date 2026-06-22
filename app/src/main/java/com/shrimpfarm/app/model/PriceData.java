package com.shrimpfarm.app.model;

import java.util.List;

public class PriceData {
    public String date;
    public List<PriceCategory> categories;
    public PriceData() {}
    public PriceData(String date, List<PriceCategory> categories) {
        this.date = date;
        this.categories = categories;
    }
}
