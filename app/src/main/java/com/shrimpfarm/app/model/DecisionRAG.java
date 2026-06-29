package com.shrimpfarm.app.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DecisionRAG {

    public static String getCombinedAdvice(String query) {
        StringBuilder sb = new StringBuilder();
        double temp = 25, ph = 8.2, tan = 0.5, sal = 15;
        int day = 30;

        Matcher tempMatcher = Pattern.compile("(\\d+(\\.\\d+)?)\\s*度").matcher(query);
        Matcher phMatcher = Pattern.compile("pH[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
        Matcher nh3Matcher = Pattern.compile("(氨氮|nh3|NH3)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
        Matcher salMatcher = Pattern.compile("(盐度|盐)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
        Matcher geMatcher = Pattern.compile("(\\d+)\\s*格").matcher(query);

        if (tempMatcher.find()) temp = Double.parseDouble(tempMatcher.group(1));
        if (phMatcher.find()) ph = Double.parseDouble(phMatcher.group(1));
        if (nh3Matcher.find()) tan = Double.parseDouble(nh3Matcher.group(2));
        if (salMatcher.find()) sal = Double.parseDouble(salMatcher.group(2));
        if (geMatcher.find() && (query.contains("盐") || query.contains("咸"))) {
            double ge = Double.parseDouble(geMatcher.group(1));
            sal = SeawaterHelper.geToSalinity(ge);
            sb.append("盐度").append((int)ge).append("格 = ").append(String.format(Locale.ROOT, "%.1f", sal)).append("PSU；");
        }
        Matcher dayMatcher = Pattern.compile("(第|养殖)?(\\d+)\\s*天").matcher(query);
        if (dayMatcher.find()) day = Integer.parseInt(dayMatcher.group(2));

        if (query.contains("温度") || query.contains("水温")) {
            String a = ShrimpAdviceHelper.getTempAdvice(temp);
            if (!a.isEmpty()) sb.append(a).append("；");
        }
        if (query.contains("pH") || query.contains("酸碱")) {
            String a = ShrimpAdviceHelper.getPhAdvice(ph);
            if (!a.isEmpty()) sb.append(a).append("；");
        }
        if (query.contains("氨氮") || query.contains("nh3")) {
            String a = ShrimpAdviceHelper.getNh3Advice(ph, temp, tan, sal, day);
            if (!a.isEmpty()) sb.append(a).append("；");
        }
        if (query.contains("亚盐") || query.contains("亚硝酸盐") || query.contains("NO2")) {
            Matcher m = Pattern.compile("(亚盐|亚硝酸盐|NO2)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
            if (m.find()) {
                double nitrite = Double.parseDouble(m.group(2));
                String a = NitriteHelper.getAdvice(nitrite, day, sal);
                if (!a.isEmpty()) sb.append(a).append("；");
            }
        }
        if (query.contains("溶氧") || query.contains("DO") || query.contains("溶解氧")) {
            Matcher m = Pattern.compile("(溶氧|DO|溶解氧)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
            if (m.find()) {
                double doVal = Double.parseDouble(m.group(2));
                String a = DOHelper.getAdvice(doVal);
                if (!a.isEmpty()) sb.append(a).append("；");
            }
        }
        if (query.contains("ORP") || query.contains("氧化还原")) {
            Matcher m = Pattern.compile("ORP[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
            if (m.find()) {
                double orp = Double.parseDouble(m.group(1));
                String a = ORPHelper.getAdvice(orp);
                if (!a.isEmpty()) sb.append(a).append("；");
            }
        }
        if (query.contains("硫化氢") || query.contains("H2S") || query.contains("h2s")) {
            Matcher m = Pattern.compile("(硫化氢|H2S|h2s)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
            if (m.find()) {
                double h2s = Double.parseDouble(m.group(2));
                String a = H2SHelper.getAdvice(h2s);
                if (!a.isEmpty()) sb.append(a).append("；");
            }
        }
        if (query.contains("余氯") || query.contains("氯")) {
            Matcher m = Pattern.compile("(余氯|氯)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
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
        if (query.contains("密度") || query.contains("比重")) {
            double d = SeawaterHelper.calcDensity(temp, sal);
            sb.append("当前海水密度为").append(String.format(Locale.ROOT, "%.2f", d)).append(" kg/m³；");
        }
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
