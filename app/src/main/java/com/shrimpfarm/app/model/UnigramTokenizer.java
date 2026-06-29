package com.shrimpfarm.app.model;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnigramTokenizer {

    private static final String VOCAB_FILE = "reranker_vocab.txt";
    private static final int MAX_LEN = 512;

    private final Map<String, Integer> pieceToId = new HashMap<>();
    private final int unkId;
    private final int clsId;
    private final int sepId;
    private final int padId;

    public UnigramTokenizer(Context context) throws Exception {
        int unk = 3, cls = 1, sep = 2, pad = 0;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(context.getAssets().open(VOCAB_FILE), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                int tab = line.lastIndexOf('\t');
                if (tab < 0) continue;
                String piece = line.substring(0, tab);
                int id = Integer.parseInt(line.substring(tab + 1));
                pieceToId.put(piece, id);
                if (piece.equals("[UNK]")) unk = id;
                else if (piece.equals("[CLS]")) cls = id;
                else if (piece.equals("[SEP]")) sep = id;
                else if (piece.equals("[PAD]")) pad = id;
            }
        }
        unkId = unk;
        clsId = cls;
        sepId = sep;
        padId = pad;
    }

    public List<Integer> encode(String text) {
        text = Normalizer.normalize(text, Normalizer.Form.NFC);
        List<Integer> ids = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int bestId = unkId;
            int bestLen = 0;
            int maxLookahead = Math.min(i + 20, text.length());
            for (int j = maxLookahead; j > i; j--) {
                String sub = text.substring(i, j);
                Integer id = pieceToId.get(sub);
                if (id != null) {
                    bestId = id;
                    bestLen = j - i;
                    break;
                }
            }
            if (bestLen > 0) {
                ids.add(bestId);
                i += bestLen;
            } else {
                String ch = text.substring(i, i + 1);
                Integer id = pieceToId.get(ch);
                ids.add(id != null ? id : unkId);
                i++;
            }
        }
        return ids;
    }

    public int[] encodePair(String query, String doc) {
        List<Integer> qIds = encode(query);
        List<Integer> dIds = encode(doc);
        int total = qIds.size() + dIds.size() + 3;
        if (total > MAX_LEN) {
            int overflow = total - MAX_LEN;
            if (overflow < dIds.size()) {
                dIds = dIds.subList(0, dIds.size() - overflow);
            } else {
                overflow -= dIds.size();
                dIds.clear();
                if (overflow < qIds.size()) {
                    qIds = qIds.subList(0, qIds.size() - overflow);
                } else {
                    qIds.clear();
                }
            }
        }
        int[] ids = new int[MAX_LEN];
        int pos = 0;
        ids[pos++] = clsId;
        for (int id : qIds) ids[pos++] = id;
        ids[pos++] = sepId;
        for (int id : dIds) ids[pos++] = id;
        ids[pos++] = sepId;
        while (pos < MAX_LEN) ids[pos++] = padId;
        return ids;
    }

    public int[] encodeSingle(String text) {
        List<Integer> tokens = encode(text);
        int[] ids = new int[MAX_LEN];
        int pos = 0;
        ids[pos++] = clsId;
        for (int id : tokens) {
            if (pos >= MAX_LEN - 1) break;
            ids[pos++] = id;
        }
        ids[pos] = sepId;
        return ids;
    }

    public int vocabSize() {
        return pieceToId.size();
    }
}
