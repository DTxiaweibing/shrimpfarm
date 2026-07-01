package com.shrimpfarm.app.model;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class RagPipeline {

    private static final String TAG = "RagPipeline";

    private static final float MIN_SCORE = 0.5f;

    public static class Result {
        public final boolean isLocal;
        public final String text;
        public final String promptForApi;

        private Result(boolean isLocal, String text, String promptForApi) {
            this.isLocal = isLocal;
            this.text = text;
            this.promptForApi = promptForApi;
        }

        public static Result local(String text) {
            return new Result(true, text, null);
        }

        public static Result api(String prompt) {
            return new Result(false, null, prompt);
        }
    }

    public Result process(String rawQuery, TokenEmbedder embedder, KnowledgeBase kb,
                          boolean enableRouting, String systemPrompt) {
        long tGlobal = System.currentTimeMillis();

        long t = System.currentTimeMillis();
        String intent = IntentRouter.classify(rawQuery);
        Log.i(TAG, "TIMING intent=" + (System.currentTimeMillis() - t) + "ms");

        if (IntentRouter.INTENT_TIME.equals(intent)) {
            return Result.local("现在是" + new java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.CHINA)
                    .format(new java.util.Date()));
        }
        if (IntentRouter.INTENT_WEATHER.equals(intent)) {
            String city = extractCity(rawQuery);
            if (city == null) {
                city = WeatherHelper.getCityByIP();
                if (city != null) Log.i(TAG, "auto-located city=" + city);
            } else {
                Log.i(TAG, "weather city=" + city);
            }
            if (city != null) {
                String weatherJson = WeatherHelper.getWeatherJsonForAI(rawQuery, city);
                if (weatherJson != null) {
                    String prompt = "用户问题：" + rawQuery + "\n\n当前天气数据（JSON）："
                            + weatherJson + "\n\n请用口语化的方式描述当前天气，"
                            + "并结合小棚养虾场景给出建议（如需防暑降温、增氧、防雨等）。";
                    return Result.api(prompt);
                }
                String weather = WeatherHelper.getWeatherNow(city);
                if (weather != null) {
                    return Result.local(city + "，" + weather);
                }
                return Result.local("暂时查不到" + city + "的天气，检查城市名是否正确。");
            }
            return Result.local("查不到你所在城市的天气信息，请输入城市名。");
        }
        if (IntentRouter.INTENT_GENERAL.equals(intent)) {
            return Result.api(rawQuery);
        }

        t = System.currentTimeMillis();
        String query = SynonymExpander.expand(rawQuery);
        Log.i(TAG, "TIMING expand=" + (System.currentTimeMillis() - t) + "ms");

        t = System.currentTimeMillis();
        String numericResult = NumericCalculator.calculate(query);
        Log.i(TAG, "TIMING numeric=" + (System.currentTimeMillis() - t) + "ms");
        if (numericResult != null) {
            return Result.local(numericResult);
        }

        t = System.currentTimeMillis();
        String localAdvice = DecisionRAG.getCombinedAdvice(query);
        Log.i(TAG, "TIMING decisionRAG=" + (System.currentTimeMillis() - t) + "ms");
        String userPrompt;

        if (!localAdvice.isEmpty()) {
            userPrompt = "用户问题：" + query + "\n\n【首要执行规则】\n" + localAdvice
                    + "\n\n请根据上述规则用口语化方式给出具体操作步骤。";
            return Result.api(userPrompt);
        }

        try {
            t = System.currentTimeMillis();
            float[] queryEmb = embedder.embed(query);
            Log.i(TAG, "TIMING embed=" + (System.currentTimeMillis() - t) + "ms");

            String route = enableRouting ? routeQuery(query) : null;

            t = System.currentTimeMillis();
            List<KnowledgeBase.ScoredIdx> candidates = kb.searchRaw(queryEmb, 3, route);
            Log.i(TAG, "TIMING searchRaw=" + (System.currentTimeMillis() - t) + "ms, size=" + candidates.size());

            if (candidates.isEmpty()) {
                return Result.api(rawQuery);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("用户问题：").append(query).append("\n\n");
            int kept = 0;
            for (KnowledgeBase.ScoredIdx si : candidates) {
                if (si.score < MIN_SCORE) break;
                sb.append("【参考知识】\n").append(kb.getChunk(si.idx).content).append("\n\n");
                kept++;
            }
            if (kept == 0) {
                return Result.api(rawQuery);
            }
            Log.i(TAG, "KB scores: " + candidates.stream().limit(kept).map(s -> String.format("%.3f", s.score)).collect(java.util.stream.Collectors.joining(" ")));
            sb.append("请严格基于以上参考知识回答。如果参考知识有具体操作数值，必须原样引用。");
            userPrompt = sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "RAG pipeline error", e);
            userPrompt = "用户问题：" + query
                    + "\n\n请根据你自身的养殖知识回答，确保建议安全可行。";
        }
        Log.i(TAG, "TIMING total=" + (System.currentTimeMillis() - tGlobal) + "ms");
        return Result.api(userPrompt);
    }

    private static String routeQuery(String query) {
        if (query.contains("怎么") || query.contains("如何") || query.contains("步骤")
                || query.contains("处理") || query.contains("操作") || query.contains("调")
                || query.contains("多少") || query.contains("多久") || query.contains("用什么"))
            return "manual";
        if (query.contains("为什么") || query.contains("原理") || query.contains("原因")
                || query.contains("什么道理") || query.contains("机制") || query.contains("影响"))
            return "theory";
        if (query.contains("规则") || query.contains("规定") || query.contains("必须")
                || query.contains("禁止") || query.contains("允许") || query.contains("不能"))
            return "rules";
        return null;
    }

    private static String extractCity(String query) {
        String cleaned = query.replaceAll(
                "(今天|明天|后天|昨天|天气|预报|预告|播报" +
                "|下雨|下雪|阴天|晴天|多云|阵雨|雷雨|暴雨|大风|台风|刮风" +
                "|气温|温度|湿度|风向|风力|风速|气压|降水" +
                "|怎么样|怎么样|怎样|如何|什么|多少|几度|几级|几" +
                "|高|低|吗|呢|吧|啊|哈|呀|哦|嗯" +
                "|查一下|看看|帮我|我想|我要|请问|能不能|会不会" +
                "|℃|°C|°|度|％|%|点|分" +
                "|[？?！!，,。.、…：:；;【】（）()（）])"
                , "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    public static boolean isGeneralIntent(String intent) {
        return IntentRouter.INTENT_TIME.equals(intent)
                || IntentRouter.INTENT_WEATHER.equals(intent)
                || IntentRouter.INTENT_GENERAL.equals(intent);
    }
}
