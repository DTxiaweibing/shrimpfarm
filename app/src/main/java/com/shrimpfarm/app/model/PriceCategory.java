package com.shrimpfarm.app.model;

import java.util.List;

public class PriceCategory {
    public String title;
    public List<PriceItem> items;
    public PriceCategory() {}
    public PriceCategory(String title, List<PriceItem> items) {
        this.title = title;
        this.items = items;
    }
}
