package com.shrimpfarm.app.model;

import java.util.ArrayList;
import java.util.List;

public class RagPipeline {

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
                          Reranker reranker, boolean enableRouting, String systemPrompt) {
        String intent = IntentRouter.classify(rawQuery);
        if (IntentRouter.INTENT_TIME.equals(intent)) {
            return Result.local("现在是" + new java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.CHINA)
                    .format(new java.util.Date()));
        }
        if (IntentRouter.INTENT_WEATHER.equals(intent)) {
            return Result.local("请告诉我你在哪个城市，我帮你查天气。");
        }
        if (IntentRouter.INTENT_GENERAL.equals(intent)) {
            return Result.api("");
        }

        String query = SynonymExpander.expand(rawQuery);

        String numericResult = NumericCalculator.calculate(query);
        if (numericResult != null) {
            return Result.local(numericResult);
        }

        String localAdvice = DecisionRAG.getCombinedAdvice(query);
        String userPrompt;

        if (!localAdvice.isEmpty()) {
            userPrompt = "用户问题：" + query + "\n\n【首要执行规则】\n" + localAdvice
                    + "\n\n请根据上述规则用口语化方式给出具体操作步骤。";
            return Result.api(userPrompt);
        }

        try {
            float[] queryEmb = embedder.embed(query);
            String route = enableRouting ? routeQuery(query) : null;
            List<KnowledgeBase.ScoredIdx> candidates = kb.searchRaw(queryEmb, 20, route);

            List<String> docContents = new ArrayList<>();
            for (KnowledgeBase.ScoredIdx si : candidates) {
                docContents.add(kb.getChunk(si.idx).content);
            }

            List<Reranker.ScoredDoc> reranked;
            if (reranker != null && !docContents.isEmpty()) {
                reranked = reranker.rerank(query, docContents, 3);
            } else {
                reranked = new ArrayList<>();
                for (String c : docContents) reranked.add(new Reranker.ScoredDoc(c, 0f));
            }

            StringBuilder sb = new StringBuilder();
            sb.append("用户问题：").append(query).append("\n\n");
            for (Reranker.ScoredDoc sd : reranked) {
                sb.append("【参考知识】\n").append(sd.content).append("\n\n");
            }
            sb.append("请严格基于以上参考知识回答。如果参考知识有具体操作数值，必须原样引用。");
            userPrompt = sb.toString();
        } catch (Exception e) {
            userPrompt = "用户问题：" + query
                    + "\n\n请根据你自身的养殖知识回答，确保建议安全可行。";
        }
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

    public static boolean isGeneralIntent(String intent) {
        return IntentRouter.INTENT_TIME.equals(intent)
                || IntentRouter.INTENT_WEATHER.equals(intent)
                || IntentRouter.INTENT_GENERAL.equals(intent);
    }
}
