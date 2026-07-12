package com.shrimpfarm.app.utils;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;

public class SensitiveWordFilter {
    private static volatile SensitiveWordBs instance;

    public static boolean contains(String text) {
        if (text == null || text.isEmpty()) return false;
        if (instance == null) {
            synchronized (SensitiveWordFilter.class) {
                if (instance == null) {
                    instance = SensitiveWordBs.newInstance().init();
                }
            }
        }
        return instance.contains(text);
    }
}
