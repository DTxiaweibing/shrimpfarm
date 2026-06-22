package com.shrimpfarm.app.model;

public class AlertItem {
    public String message;
    public String type;
    public int id;

    public AlertItem(String message, String type) {
        this.message = message;
        this.type = type;
        this.id = (message + "_" + type).hashCode();
    }
}
