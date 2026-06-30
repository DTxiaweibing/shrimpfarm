package com.shrimpfarm.app.model;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public class Reranker implements AutoCloseable {

    private static final String TAG = "Reranker";
    private static final String MODEL_FILE = "reranker.onnx";

    private final UnigramTokenizer tokenizer;
    private final OrtEnvironment env;
    private final OrtSession session;

    public Reranker(Context context) throws Exception {
        tokenizer = new UnigramTokenizer(context);
        env = OrtEnvironment.getEnvironment();
        byte[] modelBytes;
        try (InputStream is = context.getAssets().open(MODEL_FILE);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) baos.write(buf, 0, read);
            modelBytes = baos.toByteArray();
        }
        session = env.createSession(modelBytes, new OrtSession.SessionOptions());
        Log.i(TAG, "Reranker loaded, vocab=" + tokenizer.vocabSize());
    }

    public static class ScoredDoc {
        public final String content;
        public final float score;
        public ScoredDoc(String content, float score) {
            this.content = content;
            this.score = score;
        }
    }

    public List<ScoredDoc> rerank(String query, List<String> docs, int topK) throws Exception {
        if (docs.isEmpty()) return Collections.emptyList();

        List<ScoredDoc> scored = new ArrayList<>(docs.size());

        if (docs.size() == 1) {
            ScoredDoc sd = rerankSingle(query, docs.get(0));
            scored.add(sd);
        } else {
            int batchSize = docs.size();
            long[] flatIds = new long[batchSize * 512];
            long[] flatMasks = new long[batchSize * 512];

            for (int d = 0; d < batchSize; d++) {
                int[] inputIds = tokenizer.encodePair(query, docs.get(d));
                int offset = d * 512;
                for (int i = 0; i < 512; i++) {
                    flatIds[offset + i] = inputIds[i];
                    flatMasks[offset + i] = (inputIds[i] != 0) ? 1 : 0;
                }
            }

            long[] shape = new long[]{batchSize, 512};
            try (OnnxTensor idsT = OnnxTensor.createTensor(env, LongBuffer.wrap(flatIds), shape);
                 OnnxTensor maskT = OnnxTensor.createTensor(env, LongBuffer.wrap(flatMasks), shape)) {

                Map<String, OnnxTensor> inputs = new HashMap<>();
                inputs.put("input_ids", idsT);
                inputs.put("attention_mask", maskT);

                try (OrtSession.Result outputs = session.run(inputs)) {
                    float[][] logits = (float[][]) outputs.get(0).getValue();
                    for (int d = 0; d < batchSize; d++) {
                        scored.add(new ScoredDoc(docs.get(d), logits[d][0]));
                    }
                }
            }
        }

        Collections.sort(scored, (a, b) -> Float.compare(b.score, a.score));

        if (topK > 0 && topK < scored.size()) {
            scored = scored.subList(0, topK);
        }

        return scored;
    }

    private ScoredDoc rerankSingle(String query, String doc) throws Exception {
        int[] inputIds = tokenizer.encodePair(query, doc);
        long[] idsLong = new long[512];
        long[] maskLong = new long[512];
        for (int i = 0; i < 512; i++) {
            idsLong[i] = inputIds[i];
            maskLong[i] = (inputIds[i] != 0) ? 1 : 0;
        }
        long[] shape = new long[]{1, 512};
        try (OnnxTensor idsT = OnnxTensor.createTensor(env, LongBuffer.wrap(idsLong), shape);
             OnnxTensor maskT = OnnxTensor.createTensor(env, LongBuffer.wrap(maskLong), shape)) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", idsT);
            inputs.put("attention_mask", maskT);
            try (OrtSession.Result outputs = session.run(inputs)) {
                float[][] logits = (float[][]) outputs.get(0).getValue();
                return new ScoredDoc(doc, logits[0][0]);
            }
        }
    }

    @Override
    public void close() {
        if (session != null) try { session.close(); } catch (Exception ignored) {}
    }
}
