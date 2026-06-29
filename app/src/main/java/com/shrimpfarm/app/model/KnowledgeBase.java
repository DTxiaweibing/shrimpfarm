package com.shrimpfarm.app.model;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class KnowledgeBase {

    private static final String TAG = "KnowledgeBase";
    private static final String DB_NAME = "knowledge_base.db";

    public static class ChunkEntry {
        public final int id;
        public final String docId;
        public final String content;
        public final float[] embedding;

        ChunkEntry(int id, String docId, String content, float[] embedding) {
            this.id = id;
            this.docId = docId;
            this.content = content;
            this.embedding = embedding;
        }
    }

    private final List<ChunkEntry> chunks = new ArrayList<>();

    public KnowledgeBase(Context context) {
        File dbFile = new File(context.getFilesDir(), DB_NAME);
        if (!dbFile.exists()) {
            try {
                copyAssetToFile(context, DB_NAME, dbFile);
            } catch (Exception e) {
                Log.e(TAG, "Failed to copy database", e);
                return;
            }
        }
        loadDatabase(dbFile.getAbsolutePath());
    }

    private void copyAssetToFile(Context context, String assetName, File outFile) throws Exception {
        try (InputStream is = context.getAssets().open(assetName);
             FileOutputStream os = new FileOutputStream(outFile)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                os.write(buf, 0, len);
            }
        }
    }

    private void loadDatabase(String dbPath) {
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);
             Cursor c = db.rawQuery("SELECT id, doc_id, content, embedding FROM chunks ORDER BY id", null)) {
            while (c.moveToNext()) {
                int id = c.getInt(0);
                String docId = c.getString(1);
                String content = c.getString(2);
                byte[] blob = c.getBlob(3);
                ByteBuffer bb = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
                float[] emb = new float[blob.length / 4];
                bb.asFloatBuffer().get(emb);
                chunks.add(new ChunkEntry(id, docId, content, emb));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load database", e);
        }
        Log.i(TAG, "Loaded " + chunks.size() + " chunks, first vec[0]=" + (chunks.isEmpty() ? "N/A" : chunks.get(0).embedding[0]));
    }

    public List<String> search(float[] queryEmb, int topK) {
        List<ScoredIdx> ranked = searchRaw(queryEmb, topK);
        List<String> results = new ArrayList<>();
        for (ScoredIdx si : ranked) {
            results.add(chunks.get(si.idx).content);
        }
        return results;
    }

    public static class ScoredIdx {
        public final int idx;
        public final float score;
        ScoredIdx(int idx, float score) {
            this.idx = idx;
            this.score = score;
        }
    }

    private static final float MIN_SCORE = 0.0f;

    public List<ScoredIdx> searchRaw(float[] queryEmb, int topK) {
        return searchRaw(queryEmb, topK, null);
    }

    public List<ScoredIdx> searchRaw(float[] queryEmb, int topK, String docIdPrefix) {
        if (topK <= 0) topK = Math.min(20, chunks.size());
        topK = Math.min(topK, chunks.size());

        float[] scores = new float[chunks.size()];
        int[] indices = new int[chunks.size()];
        int validCount = 0;
        for (int i = 0; i < chunks.size(); i++) {
            if (docIdPrefix != null && !chunks.get(i).docId.startsWith(docIdPrefix)) continue;
            scores[validCount] = cosineSimilarity(queryEmb, chunks.get(i).embedding);
            indices[validCount] = i;
            validCount++;
        }
        topK = Math.min(topK, validCount);
        for (int i = 0; i < topK; i++) {
            for (int j = i + 1; j < chunks.size(); j++) {
                if (scores[j] > scores[i]) {
                    float tmpS = scores[i]; scores[i] = scores[j]; scores[j] = tmpS;
                    int tmpI = indices[i]; indices[i] = indices[j]; indices[j] = tmpI;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, scores.length); i++) {
            sb.append(String.format(java.util.Locale.ROOT, " %.3f", scores[i]));
        }
        Log.i(TAG, "Top scores:" + sb + " (query[0]=" + queryEmb[0] + ")");
        List<ScoredIdx> results = new ArrayList<>();
        for (int i = 0; i < topK; i++) {
            if (scores[i] < MIN_SCORE) break;
            results.add(new ScoredIdx(indices[i], scores[i]));
        }
        return results;
    }

    public ChunkEntry getChunk(int idx) {
        return chunks.get(idx);
    }

    private float cosineSimilarity(float[] a, float[] b) {
        float dot = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    public int size() {
        return chunks.size();
    }
}
