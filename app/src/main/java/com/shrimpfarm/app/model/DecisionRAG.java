package com.shrimpfarm.app.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DecisionRAG {

/** 无条件命中即返回 advice 并跳过知识库检索，因此每条规则都必须有本句中真实提取到的数据支撑。 */
    public static String getCombinedAdvice(String query) {
        StringBuilder sb = new StringBuilder();
        double temp = 25, ph = 8.2, tan = 0.5, sal = 15;
        int day = 30;

        Matcher tempMatcher = Pattern.compile("(\\d+(\\.\\d+)?)\\s*度").matcher(query);
        Matcher phMatcher = Pattern.compile("pH[\\s:]*(\\d+(\\.\\d+)?)", Pattern.CASE_INSENSITIVE).matcher(query);
        Matcher nh3Matcher = Pattern.compile("(氨氮|nh3)[\\s:]*(\\d+(\\.\\d+)?)", Pattern.CASE_INSENSITIVE).matcher(query);
        Matcher salMatcher = Pattern.compile("(盐度|含盐量|盐分|海水盐)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
        Matcher geMatcher = Pattern.compile("(\\d+(\\.\\d+)?)\\s*格").matcher(query);
        Matcher qfMatcher = Pattern.compile("千分之(\\d+(\\.\\d+)?)").matcher(query);

        boolean tempFound = tempMatcher.find();
        boolean phFound = phMatcher.find();
        boolean nh3Found = nh3Matcher.find();
        boolean salFound = salMatcher.find();
        boolean geFound = geMatcher.find();
        boolean qfFound = qfMatcher.find();

        if (tempFound) temp = Double.parseDouble(tempMatcher.group(1));
        if (phFound) ph = Double.parseDouble(phMatcher.group(1));
        if (nh3Found) tan = Double.parseDouble(nh3Matcher.group(2));
        if (salFound) sal = Double.parseDouble(salMatcher.group(2));
        else if (qfFound) sal = Double.parseDouble(qfMatcher.group(1));

        if (geFound && (query.contains("盐度") || query.contains("咸度") || query.contains("海水"))) {
            double ge = Double.parseDouble(geMatcher.group(1));
            sal = SeawaterHelper.geToSalinity(ge);
            sb.append("盐度").append((int)ge).append("格 = ").append(String.format(Locale.ROOT, "%.1f", sal)).append("PSU；");
        }

        boolean dayFound = false;
        if (query.contains("后期") || query.contains("60天") || query.contains("六十天")) day = 60;
        Matcher dayMatcher = Pattern.compile("(第|养殖)?(\\d+)\\s*天").matcher(query);
        if (dayMatcher.find()) {
            day = Integer.parseInt(dayMatcher.group(2));
            dayFound = true;
        }

        if (tempFound && (query.contains("温度") || query.contains("水温"))) {
            String a = ShrimpAdviceHelper.getTempAdvice(temp);
            if (!a.isEmpty()) sb.append(a).append("；");
        }
        if (phFound && (query.contains("pH") || query.contains("酸碱"))) {
            String a = ShrimpAdviceHelper.getPhAdvice(ph);
            if (!a.isEmpty()) sb.append(a).append("；");
        }
        if (nh3Found && (query.contains("氨氮") || query.contains("nh3"))) {
            String a = ShrimpAdviceHelper.getNh3Advice(ph, temp, tan, sal, day);
            if (!a.isEmpty()) sb.append(a).append(dayFound ? "；" : "（按养殖30天估算）；");
        }
        if (query.contains("亚盐") || query.contains("亚硝酸盐") || query.contains("NO2")) {
            Matcher m = Pattern.compile("(亚盐|亚硝酸盐|NO2)[\\s:]*(\\d+(\\.\\d+)?)", Pattern.CASE_INSENSITIVE).matcher(query);
            if (m.find()) {
                double nitrite = Double.parseDouble(m.group(2));
                String a = NitriteHelper.getAdvice(nitrite, day, sal);
                if (!a.isEmpty()) sb.append(a).append("；");
            } else if (query.contains("耐受") || query.contains("安全限") || query.contains("限值")) {
                double limit = NitriteHelper.getSafeLimit(day, sal);
                sb.append(String.format(Locale.ROOT,
                        "当前盐度%.0fPSU，养殖%.0f天，亚硝酸盐安全限值约%.2f mg/L", sal, (double)day, limit)).append("；");
            }
        }
        if (query.contains("溶氧") || query.contains("DO") || query.contains("溶解氧")) {
            Matcher m = Pattern.compile("(溶氧|DO|溶解氧)[\\s:]*(\\d+(\\.\\d+)?)", Pattern.CASE_INSENSITIVE).matcher(query);
            if (m.find()) {
                double doVal = Double.parseDouble(m.group(2));
                String a = DOHelper.getAdvice(doVal);
                if (!a.isEmpty()) sb.append(a).append("；");
            }
        }
        if (query.contains("ORP") || query.contains("氧化还原")) {
            Matcher m = Pattern.compile("ORP[\\s:]*(\\d+(\\.\\d+)?)", Pattern.CASE_INSENSITIVE).matcher(query);
            if (m.find()) {
                double orp = Double.parseDouble(m.group(1));
                String a = ORPHelper.getAdvice(orp);
                if (!a.isEmpty()) sb.append(a).append("；");
            }
        }
        if (query.contains("硫化氢") || query.contains("H2S")) {
            Matcher m = Pattern.compile("(硫化氢|H2S)[\\s:]*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE).matcher(query);
            if (m.find()) {
                double h2s = Double.parseDouble(m.group(2));
                String a = H2SHelper.getAdvice(h2s);
                if (!a.isEmpty()) sb.append(a).append("；");
            }
        }
        if (query.contains("余氯") || query.contains("总氯") || query.contains("游离氯")) {
            Matcher m = Pattern.compile("(余氯|总氯|游离氯)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
            if (m.find()) {
                double chlorine = Double.parseDouble(m.group(2));
                String a = ChlorineHelper.getAdvice(chlorine, day);
                if (!a.isEmpty()) sb.append(a).append("；");
            }
        }
        if (query.contains("弧菌")) {
            Matcher m = Pattern.compile("弧菌[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
            if (m.find()) {
                double vibrio = Double.parseDouble(m.group(1));
                String a = VibrioHelper.getAdvice(vibrio, day);
                if (!a.isEmpty()) sb.append(a).append("；");
            }
        }
        // 知识库中 93% 的「密度」指放苗/养殖密度，仅在明确海水语境且已给出温盐数据时才做换算
        if (geFound || tempFound || salFound || qfFound) {
            boolean seaContext = query.contains("海水") || query.contains("水体")
                    || query.contains("比重") || query.contains("盐度");
            if (query.contains("密度") && seaContext) {
                double d = SeawaterHelper.calcDensity(temp, sal);
                sb.append("当前海水密度为").append(String.format(Locale.ROOT, "%.2f", d)).append(" kg/m³；");
            }
        }
        return sb.toString();
    }

    /** 关键词型应急规则：不参与短路，仅作为附加提示叠加在知识库检索结果之后。 */
    public static String getEmergencyAdvice(String query) {
        StringBuilder sb = new StringBuilder();
        if (query.contains("偷死")) {
            sb.append("偷死通常由底质恶化、弧菌感染或虾苗体质弱引起。建议：1.停料观察2-3天，同时使用底改产品（如过硫酸氢钾）；2.检测水体弧菌；3.拌料投喂免疫增强剂（多糖、维生素C）；4.适当换水，控制投喂量在正常水平的70%；");
        }
        return sb.toString();
    }

    public static List<String> searchRulesFromKB(String query, TokenEmbedder embedder, KnowledgeBase kb) {
        List<String> results = new ArrayList<>();
        try {
            String rulesQuery = "养殖规则 " + query;
            float[] emb = embedder.embed(rulesQuery);
            List<KnowledgeBase.ScoredIdx> candidates = kb.searchRaw(emb, 5, "rules");
            for (KnowledgeBase.ScoredIdx si : candidates) {
                results.add(kb.getChunk(si.idx).content);
            }
        } catch (Exception e) {
            android.util.Log.w("DecisionRAG", "KB rule search failed: " + e.getMessage());
        }
        return results;
    }
}
