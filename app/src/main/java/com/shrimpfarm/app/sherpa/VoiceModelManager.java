package com.shrimpfarm.app.sherpa;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class VoiceModelManager {

    public static final String TYPE_ASR = "asr";
    public static final String TYPE_TTS = "tts";

    private static final String TAG = "VoiceModelManager";

    private static final String[] ORDERS = {TYPE_TTS, TYPE_ASR};

    private static final String ASR_TAR = "sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2";
    private static final String TTS_TAR = "vits-melo-tts-zh_en.tar.bz2";

    private static final String BASE_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/%s-models/%s";

    private static final String[] MIRRORS = {
            "https://gh-proxy.com/",
            "https://ghfast.top/",
            ""};

    private static final String ASSETS_ROOT = "sherpa_models";

    private enum State {
        STARTED, DONE, SKIPPED
    }

    public interface ProgressListener {
        void onProgress(float asrPercent, float ttsPercent);

        void onFinished(String message);
    }

    private static volatile VoiceModelManager instance;

    private final Context appContext;
    private final OkHttpClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile boolean asrDone;
    private volatile boolean ttsDone;

    private final Object installLock = new Object();

    private VoiceModelManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        refresh();
    }

    public static VoiceModelManager getInstance(Context context) {
        if (instance == null) {
            synchronized (VoiceModelManager.class) {
                if (instance == null) {
                    instance = new VoiceModelManager(context);
                }
            }
        }
        return instance;
    }

    private File modelsDir() {
        return new File(appContext.getFilesDir(), "sherpa_models");
    }

    public File asrDir() {
        return new File(modelsDir(), TYPE_ASR);
    }

    public File ttsDir() {
        return new File(modelsDir(), TYPE_TTS);
    }

    public boolean isAsrReady() {
        return asrDone;
    }

    public boolean isTtsReady() {
        return ttsDone;
    }

    public boolean isReady(String type) {
        return TYPE_ASR.equals(type) ? asrDone : ttsDone;
    }

    private boolean dirUsable(File dir, String[] files) {
        for (String f : files) {
            if (!new File(dir, f).exists()) return false;
        }
        return true;
    }

    private void refresh() {
        String[] asrFiles = {"model.int8.onnx", "tokens.txt"};
        String[] ttsFiles = {"model.onnx", "tokens.txt", "lexicon.txt", "phone.fst", "date.fst", "number.fst"};
        asrDone = dirUsable(asrDir(), asrFiles);
        ttsDone = dirUsable(ttsDir(), ttsFiles);
    }

    public boolean installFromAssets() {
        synchronized (installLock) {
            refresh();
            boolean ok = true;
            if (!asrDone) ok &= copyAssetDir(TYPE_ASR);
            if (!ttsDone) ok &= copyAssetDir(TYPE_TTS);
            refresh();
            return ok;
        }
    }

    private boolean copyAssetDir(String type) {
        String[] need;
        if (TYPE_ASR.equals(type)) {
            need = new String[]{"model.int8.onnx", "tokens.txt"};
        } else {
            need = new String[]{"model.onnx", "tokens.txt", "lexicon.txt", "phone.fst", "date.fst", "number.fst"};
        }
        File target = TYPE_ASR.equals(type) ? asrDir() : ttsDir();
        try {
            if (!target.exists() && !target.mkdirs()) return false;
            AssetManager am = appContext.getAssets();
            for (String f : need) {
                copyAssetFile(am, TYPE_ASR.equals(type) ? "asr" : "tts", f, new File(target, f));
            }
            return true;
        } catch (IOException e) {
            Log.w(TAG, "assets copy failed for " + type + ": " + e.getMessage());
            return false;
        }
    }

    private void copyAssetFile(AssetManager am, String type, String name, File out) throws IOException {
        if (out.exists() && out.length() > 0) return;
        try (InputStream in = am.open(ASSETS_ROOT + "/" + type + "/" + name);
             OutputStream os = new BufferedOutputStream(new FileOutputStream(out), 64 * 1024)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (isCancelled()) throw new IOException("cancelled");
                os.write(buf, 0, n);
            }
        } catch (IOException e) {
            out.delete();
            throw e;
        }
    }

    public void downloadAll(final ProgressListener listener) {
        executor.execute(() -> {
            if (installFromAssets()) {
                if (listener != null) listener.onFinished("模型已就绪(内置)");
                return;
            }
            String result = checkAndDownload(listener);
            if (listener != null) {
                String msg;
                if (result != null) {
                    msg = "下载失败: " + result;
                } else {
                    msg = "下载完成";
                }
                listener.onFinished(msg);
            }
        });
    }

    private String checkAndDownload(ProgressListener listener) {
        refresh();
        for (String type : ORDERS) {
            if (isReady(type)) continue;
            State st = downloadOne(type, listener);
            if (st == State.STARTED) return "asr".equals(type) ? "ASR模型" : "TTS模型";
        }
        return null;
    }

    private boolean isCancelled() {
        return Thread.currentThread().isInterrupted();
    }

    private State downloadOne(String type, ProgressListener listener) {
        String name = TYPE_ASR.equals(type) ? ASR_TAR : TTS_TAR;
        File cacheFile = null;
        try {
            cacheFile = ensureCached(name, type, listener);
            if (isCancelled()) return State.STARTED;
            File target = TYPE_ASR.equals(type) ? asrDir() : ttsDir();
            boolean extracted = extract(type, cacheFile, target);
            if (isCancelled()) return State.STARTED;
            if (extracted) {
                if (TYPE_ASR.equals(type)) asrDone = true;
                else ttsDone = true;
                return State.DONE;
            }
            cacheFile.delete();
            return State.STARTED;
        } catch (IOException e) {
            if (cacheFile != null) cacheFile.delete();
            return State.STARTED;
        }
    }

    private File ensureCached(String name, String type, ProgressListener listener)
            throws IOException {
        File cacheDir = new File(appContext.getCacheDir(), "sherpa_download");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IOException("cannot create cache dir");
        }
        File target = new File(cacheDir, name);
        for (String mirror : MIRRORS) {
            if (cacheIsComplete(target)) {
                return target;
            }
            target.delete();
            IOException last = tryDownload(mirror, name, type, target, listener);
            if (isCancelled()) break;
            if (last == null && cacheIsComplete(target)) {
                return target;
            }
        }
        target.delete();
        throw new IOException("下载失败: " + name);
    }

    private boolean cacheIsComplete(File target) {
        if (!target.exists() || target.length() < 1024 * 1024) {
            return false;
        }
        try (BZip2CompressorInputStream bz =
                     new BZip2CompressorInputStream(new BufferedInputStream(new FileInputStream(target)))) {
            boolean readable = bz.read() >= 0;
            if (!readable) return false;
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    private IOException tryDownload(String mirror, String name, String type, File target,
                                    ProgressListener listener) {
        try {
            String url = mirror.length() == 0
                    ? String.format(BASE_URL, type, name)
                    : mirror + String.format(BASE_URL, type, name);
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return new IOException("HTTP " + response.code());
                }
                long total = response.body().contentLength();
                if (total <= 0) total = 0;
                float base = typeProgressBase(type);
                try (InputStream in = response.body().byteStream();
                     OutputStream out = new FileOutputStream(target)) {
                    copyWithProgress(in, out, total, base, listener, TYPE_ASR.equals(type));
                }
                return null;
            }
        } catch (IOException e) {
            target.delete();
            return e;
        }
    }

    private float typeProgressBase(String type) {
        return 0f;
    }

    private void copyWithProgress(InputStream in, OutputStream out, long total, float base,
                                  ProgressListener listener, boolean isAsr) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long written = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            if (isCancelled()) throw new IOException("cancelled");
            out.write(buf, 0, n);
            written += n;
            if (total > 0 && listener != null) {
                float pct = base + (100f - base) * written / total;
                listener.onProgress(isAsr ? pct : Float.NaN, isAsr ? Float.NaN : pct);
            }
        }
        out.flush();
    }

    private boolean extract(String type, File cacheFile, File targetDir) throws IOException {
        String[] need;
        if (type.equals(TYPE_ASR)) {
            need = new String[]{"model.int8.onnx", "tokens.txt"};
        } else {
            need = new String[]{"model.onnx", "tokens.txt", "lexicon.txt", "phone.fst", "date.fst", "number.fst"};
        }
        File tmp = new File(targetDir.getParentFile(), targetDir.getName() + ".tmp");
        deleteRecursive(tmp);
        if (!tmp.mkdirs()) throw new IOException("cannot mkdir tmp");
        extractTar(cacheFile, tmp, need);
        for (String f : need) {
            if (!new File(tmp, f).exists()) {
                throw new IOException("解压缺少文件: " + f);
            }
        }
        deleteRecursive(targetDir);
        if (!tmp.renameTo(targetDir)) {
            return false;
        }
        return dirUsable(targetDir, need);
    }

    private void extractTar(File tarFile, File outDir, String... need) throws IOException {
        try (FileInputStream fis = new FileInputStream(tarFile);
             BufferedInputStream bis = new BufferedInputStream(fis, 64 * 1024);
             InputStream bz = new BZip2CompressorInputStream(bis, true);
             TarArchiveInputStream tar = new TarArchiveInputStream(bz)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = stripTop(sanitize(entry.getName()));
                if (name == null || name.isEmpty()) continue;
                File out = new File(outDir, name);
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("cannot mkdir " + parent);
                }
                try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(out), 64 * 1024)) {
                    copyAll(tar, fos);
                }
            }
        } catch (Exception e) {
            throw new IOException("解压失败: " + e.getMessage(), e);
        }
    }

    private String sanitize(String name) {
        if (name.contains("..")) return null;
        if (name.contains("\\")) return null;
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        return name;
    }

    private String stripTop(String name) {
        if (name == null) return null;
        int idx = name.indexOf('/');
        return idx >= 0 ? name.substring(idx + 1) : "";
    }

    private void copyAll(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            if (isCancelled()) throw new IOException("cancelled");
            out.write(buf, 0, n);
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        file.delete();
    }
}