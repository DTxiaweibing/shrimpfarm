package com.shrimpfarm.app.model;

import java.util.HashMap;
import java.util.Map;

public class SynonymExpander {
    private static final Map<String, String> SYNONYM_MAP = new HashMap<>();

    static {
        put("气头", "气盘");
        put("纳米管", "气盘");
        put("增氧盘", "气盘");
        put("增氧环", "气盘");
        put("曝气盘", "气盘");
        put("微孔管", "气盘");
        put("风机", "增氧机");
        put("鼓风机", "增氧机");
        put("罗茨风机", "增氧机");
        put("增氧泵", "增氧机");
        put("叶轮增氧机", "增氧机");
        put("水车增氧机", "增氧机");

        put("咸度", "盐度");
        put("含盐量", "盐度");
        put("盐分", "盐度");
        put("亚硝酸盐", "亚盐");
        put("亚硝态氮", "亚盐");
        put("游离氨", "氨氮毒性");
        put("非离子氨", "氨氮毒性");
        put("总硬度", "硬度");
        put("总碱度", "碱度");

        put("喂料", "投喂");
        put("吃料", "摄食");
        put("料台", "食台");
        put("料盘", "食台");
        put("食盘", "食台");
        put("拌药", "拌料配比");
        put("料比", "饵料系数");
        put("饲料系数", "饵料系数");

        put("加温棒", "加热棒");
        put("加热管", "加热棒");
        put("烧锅炉", "锅炉加温");
        put("冬棚", "小棚");
        put("保温棚", "小棚");

        put("做水", "调水");
        put("肥水", "培藻");
        put("培水", "培藻");
        put("投苗", "放苗");
        put("下苗", "放苗");
        put("加水", "换水");
        put("吸底", "底排污");

        put("游塘", "应激游塘");
        put("浮头", "缺氧浮头");
        put("掉苗", "损耗");
        put("红体", "红体病");
        put("白便", "肠炎白便");
        put("空肠空胃", "肠炎");
    }

    private static void put(String key, String value) {
        SYNONYM_MAP.put(key, value);
    }

    public static String expand(String query) {
        String expanded = query;
        for (Map.Entry<String, String> entry : SYNONYM_MAP.entrySet()) {
            if (expanded.contains(entry.getKey())) {
                expanded = expanded.replace(entry.getKey(),
                        entry.getKey() + "(" + entry.getValue() + ")");
            }
        }
        if (!expanded.equals(query)) {
            android.util.Log.i("SynonymExpander", "Expanded: " + query + " -> " + expanded);
        }
        return expanded;
    }
}
