package com.shrimpfarm.app.model;

public class IntentRouter {

    public static final String INTENT_TIME = "INTENT_TIME";
    public static final String INTENT_WEATHER = "INTENT_WEATHER";
    public static final String INTENT_GENERAL = "INTENT_GENERAL";
    public static final String INTENT_SHRIMP = "INTENT_SHRIMP";

    private static final String[] SHRIMP_KEYWORDS = {
        "对虾", "虾塘", "小棚", "放苗", "投苗", "下苗",
        "料台", "料盘", "食台", "喂料", "投喂", "吃料", "摄食",
        "亚硝酸盐", "亚盐", "NO2", "pH", "酸碱",
        "氨氮", "NH3", "水色", "底质", "底改",
        "气盘", "气头", "纳米管", "增氧盘", "微孔管",
        "盐度", "咸度", "格", "密度", "比重",
        "掉苗", "偷死", "游塘", "浮头", "红体", "白便",
        "弧菌", "溶氧", "DO", "溶解氧",
        "ORP", "氧化还原",
        "硫化氢", "H2S",
        "余氯", "氯",
        "温度", "水温", "加温", "加热",
        "增氧", "风机", "鼓风机", "锅炉",
        "做水", "肥水", "培藻", "培水", "调水",
        "改底", "解毒", "消杀",
        "拌料", "拌药", "饵料", "饲料",
        "苗", "虾苗", "虾",
    };

    public static String classify(String query) {
        if (query.matches(".*(几点|时间|日期|今天|星期|现在是|几月|几号).*"))
            return INTENT_TIME;
        if (query.matches(".*(天气|下雨|阴天|晴天|气温|刮风|台风|℃|°C).*")
                && !query.contains("水温") && !query.contains("水温"))
            return INTENT_WEATHER;
        for (String kw : SHRIMP_KEYWORDS) {
            if (query.contains(kw)) return INTENT_SHRIMP;
        }
        if (query.matches(".*(你是谁|你叫什么|你是什么|谁创造|谁做的|你好).*"))
            return INTENT_GENERAL;
        return INTENT_GENERAL;
    }
}
