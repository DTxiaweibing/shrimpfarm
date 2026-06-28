package com.shrimpfarm.app.model;

import android.content.Context;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public class TokenEmbedder implements AutoCloseable {

    private static final int MAX_SEQ_LEN = 512;
    private static final String MODEL_FILE = "model_qint8.onnx";
    private static final String VOCAB_FILE = "vocab.txt";

    private final Map<String, Integer> vocab = new HashMap<>();
    private final OrtEnvironment env;
    private final OrtSession session;

    public TokenEmbedder(Context context) throws IOException {
        loadVocab(context);
        env = OrtEnvironment.getEnvironment();
        try {
            byte[] modelBytes = loadAssetBytes(context, MODEL_FILE);
            session = env.createSession(modelBytes, new OrtSession.SessionOptions());
        } catch (Exception e) {
            throw new IOException("Failed to create ONNX session", e);
        }
    }

    private int getVocabId(String token, int fallback) {
        Integer id = vocab.get(token);
        return id != null ? id : fallback;
    }

    private void loadVocab(Context context) throws IOException {
        try (InputStream is = context.getAssets().open(VOCAB_FILE);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            int idx = 0;
            while ((line = br.readLine()) != null) {
                String t = line.trim();
                if (!t.isEmpty()) vocab.put(t, idx++);
            }
        }
    }

    private byte[] loadAssetBytes(Context context, String name) throws IOException {
        try (InputStream is = context.getAssets().open(name);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return baos.toByteArray();
        }
    }

    private List<Integer> tokenize(String text) {
        text = text.replace('\u3000', ' ').replace('\u00a0', ' ');
        text = text.replaceAll("\\s+", " ").trim();
        List<Integer> ids = new ArrayList<>();
        int[] codePoints = text.codePoints().toArray();
        int unkId = getVocabId("[UNK]", 100);
        for (int cp : codePoints) {
            String ch = new String(Character.toChars(cp));
            Integer id = vocab.get(ch);
            if (id != null) {
                ids.add(id);
            } else if (!Character.isWhitespace(cp)) {
                ids.add(unkId);
            }
        }
        return ids;
    }

    public float[] embed(String text) throws Exception {
        List<Integer> tokens = tokenize(text);
        if (tokens.size() > MAX_SEQ_LEN - 2) {
            tokens = tokens.subList(0, MAX_SEQ_LEN - 2);
        }
        int size = tokens.size() + 2;
        long[] inputIds = new long[MAX_SEQ_LEN];
        long[] attnMask = new long[MAX_SEQ_LEN];
        long[] tokenType = new long[MAX_SEQ_LEN];
        int clsId = getVocabId("[CLS]", 101);
        int sepId = getVocabId("[SEP]", 102);
        inputIds[0] = clsId;
        attnMask[0] = 1;
        for (int i = 0; i < tokens.size(); i++) {
            inputIds[i + 1] = tokens.get(i);
            attnMask[i + 1] = 1;
        }
        inputIds[size - 1] = sepId;
        attnMask[size - 1] = 1;

        long[] shape = new long[]{1, MAX_SEQ_LEN};
        try (OnnxTensor idsT = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape);
             OnnxTensor maskT = OnnxTensor.createTensor(env, LongBuffer.wrap(attnMask), shape);
             OnnxTensor typeT = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenType), shape)) {

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", idsT);
            inputs.put("attention_mask", maskT);
            inputs.put("token_type_ids", typeT);

            try (OrtSession.Result outputs = session.run(inputs)) {
                float[][][] lastHidden = (float[][][]) outputs.get(0).getValue();
                float[] clsEmb = lastHidden[0][0];
                float norm = 0f;
                for (float v : clsEmb) norm += v * v;
                norm = (float) Math.sqrt(norm);
                if (norm > 0) {
                    for (int i = 0; i < clsEmb.length; i++) clsEmb[i] /= norm;
                }
                float[] result = new float[clsEmb.length];
                System.arraycopy(clsEmb, 0, result, 0, clsEmb.length);
                return result;
            }
        }
    }

    @Override
    public void close() {
        if (session != null) try { session.close(); } catch (Exception ignored) {}
    }
}
