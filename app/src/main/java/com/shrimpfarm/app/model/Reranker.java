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
    private static final int BATCH_SIZE = 1;

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

        long[] shape = new long[]{1, 512};

        List<ScoredDoc> scored = new ArrayList<>(docs.size());

        for (String doc : docs) {
            int[] inputIds = tokenizer.encodePair(query, doc);
            int[] attnMask = new int[512];
            for (int i = 0; i < 512; i++) {
                attnMask[i] = (inputIds[i] != 0) ? 1 : 0;
            }

            long[] idsLong = new long[512];
            long[] maskLong = new long[512];
            for (int i = 0; i < 512; i++) {
                idsLong[i] = inputIds[i];
                maskLong[i] = attnMask[i];
            }

            try (OnnxTensor idsT = OnnxTensor.createTensor(env, LongBuffer.wrap(idsLong), shape);
                 OnnxTensor maskT = OnnxTensor.createTensor(env, LongBuffer.wrap(maskLong), shape)) {

                Map<String, OnnxTensor> inputs = new HashMap<>();
                inputs.put("input_ids", idsT);
                inputs.put("attention_mask", maskT);

                try (OrtSession.Result outputs = session.run(inputs)) {
                    float[][] logits = (float[][]) outputs.get(0).getValue();
                    scored.add(new ScoredDoc(doc, logits[0][0]));
                }
            }
        }

        Collections.sort(scored, (a, b) -> Float.compare(b.score, a.score));

        if (topK > 0 && topK < scored.size()) {
            scored = scored.subList(0, topK);
        }

        return scored;
    }

    @Override
    public void close() {
        if (session != null) try { session.close(); } catch (Exception ignored) {}
    }
}
