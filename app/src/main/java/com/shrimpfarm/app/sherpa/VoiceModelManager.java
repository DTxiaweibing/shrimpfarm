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

    /** TTS 模型（tar 名去掉 .tar.bz2 即可作为目录名）。 */
    public static final String TTS_MODEL_XIAO_YA = "vits-piper-zh_CN-xiao_ya-medium";

    /** 内置 TTS 模型（仅小雅；melo 已下线）。 */
    public static final String[] TTS_MODEL_KEYS = {TTS_MODEL_XIAO_YA};

    /** 已下线的旧模型目录名，用于清理设备上的残留。 */
    private static final String LEGACY_TTS_MELO = "vits-melo-tts-zh_en";

    private static final String DEFAULT_TTS_MODEL = TTS_MODEL_XIAO_YA;
    private static final String PREFS_NAME = "voice_model_prefs";
    private static final String KEY_ACTIVE_TTS = "active_tts_model";

    /** 内置 melo 的 model.onnx 字节数，用于识别"误拷到其他模型目录"的假就绪。 */
    private static final long BUNDLED_MELO_ONNX_BYTES = 170_429_550L;

    /** 已知各 TTS 模型的音色数量（加载前即可用来渲染音色列表；加载后以模型实际值为准）。 */
    public static int knownSpeakers(String modelKey) {
        if (TTS_MODEL_XIAO_YA.equals(modelKey)) return 1;
        return 5;
    }

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
    private volatile String activeTtsModel;

    private final Object installLock = new Object();

    private VoiceModelManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.activeTtsModel = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ACTIVE_TTS, DEFAULT_TTS_MODEL);
        if (!isKnownModel(this.activeTtsModel)) this.activeTtsModel = DEFAULT_TTS_MODEL;
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

    public String getActiveTtsModel() {
        return activeTtsModel;
    }

    private boolean isKnownModel(String modelKey) {
        for (String k : TTS_MODEL_KEYS) {
            if (k.equals(modelKey)) return true;
        }
        return false;
    }

    /** 切换当前 TTS 模型并持久化；不影响其它模型目录，可随时切回。 */
    public void setActiveTtsModel(String modelKey) {
        if (modelKey == null) return;
        if (!isKnownModel(modelKey)) return;
        if (modelKey.equals(activeTtsModel)) return;
        activeTtsModel = modelKey;
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_ACTIVE_TTS, modelKey).apply();
        refresh();
    }

    /** 指定模型是否已就绪（与当前激活模型无关）。 */
    public boolean isTtsModelReady(String modelKey) {
        return dirUsable(ttsDirFor(modelKey), new String[]{
                "model.onnx", "tokens.txt", "lexicon.txt", "phone.fst", "date.fst", "number.fst"});
    }

    private File ttsDirFor(String modelKey) {
        return new File(modelsDir(), TYPE_TTS + File.separator + modelKey);
    }

    private File modelsDir() {
        return new File(appContext.getFilesDir(), "sherpa_models");
    }

    public File asrDir() {
        return new File(modelsDir(), TYPE_ASR);
    }

    public File ttsDir() {
        return ttsDirFor(activeTtsModel);
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

    /** 老版本把模型平铺在 tts/ 根目录，迁移进按模型命名的子目录，避免误判未就绪。 */
    private void migrateLegacyTtsLayout() {
        File flat = new File(modelsDir(), TYPE_TTS);
        if (!new File(flat, "model.onnx").exists()) return;
        String[] need = {"model.onnx", "tokens.txt", "lexicon.txt", "phone.fst", "date.fst", "number.fst"};
        File xiaoYaSub = new File(modelsDir(), TYPE_TTS + File.separator + TTS_MODEL_XIAO_YA);
        if (dirUsable(xiaoYaSub, need)) return;
        for (String f : need) {
            File src = new File(flat, f);
            if (src.exists()) {
                File dst = new File(xiaoYaSub, f);
                if (dst.getParentFile() != null) dst.getParentFile().mkdirs();
                src.renameTo(dst);
            }
        }
    }

    private void refresh() {
        migrateLegacyTtsLayout();
        repairMiscopiedTts();
        purgeLegacyMelo();
        String[] asrFiles = {"model.int8.onnx", "tokens.txt"};
        String[] ttsFiles = {"model.onnx", "tokens.txt", "lexicon.txt", "phone.fst", "date.fst", "number.fst"};
        asrDone = dirUsable(asrDir(), asrFiles);
        ttsDone = dirUsable(ttsDir(), ttsFiles);
    }

    /** 自愈：老版本 bug 会把内置 melo 的 model.onnx 拷进"当前激活模型"目录（默认小雅）。
     * 若小雅目录里 model.onnx 体积与内置 melo 完全一致，说明是误拷，删掉待真实下载。 */
    private void repairMiscopiedTts() {
        for (String key : TTS_MODEL_KEYS) {
            File dir = ttsDirFor(key);
            File onnx = new File(dir, "model.onnx");
            if (!onnx.exists() || !onnx.isFile()) continue;
            if (onnx.length() == BUNDLED_MELO_ONNX_BYTES) {
                deleteRecursive(dir);
                Log.i(TAG, "repair: " + key + " dir held bundled melo weights, cleaned for re-download");
            }
        }
    }

    /** 移除已下线的 melo 模型残留（目录与缓存 tar），释放空间。 */
    private void purgeLegacyMelo() {
        deleteRecursive(ttsDirFor(LEGACY_TTS_MELO));
        File cache = new File(appContext.getCacheDir(),
                "sherpa_download" + File.separator + LEGACY_TTS_MELO + ".tar.bz2");
        if (cache.exists()) cache.delete();
    }

    public boolean installFromAssets() {
        synchronized (installLock) {
            refresh();
            Log.i(TAG, "installFromAssets start: asrReady=" + asrDone
                    + " activeTts=" + activeTtsModel
                    + " xiaoYaReady=" + isTtsModelReady(TTS_MODEL_XIAO_YA));
            boolean ok = true;
            if (!asrDone) ok &= copyAssetDir(TYPE_ASR, null);
            // 为每个内置 TTS 模型拷贝到自己的目录（与激活模型无关）。
            for (String key : TTS_MODEL_KEYS) {
                boolean before = isTtsModelReady(key);
                if (!before) {
                    ok &= copyAssetDir(TYPE_TTS, key);
                }
                Log.i(TAG, "installFromAssets model=" + key
                        + " readyBefore=" + before
                        + " readyAfter=" + isTtsModelReady(key));
            }
            refresh();
            Log.i(TAG, "installFromAssets end: ok=" + ok + " asrDone=" + asrDone
                    + " ttsDone=" + ttsDone + " activeTts=" + activeTtsModel);
            return ok;
        }
    }

    private boolean copyAssetDir(String type, String modelKey) {
        String[] need;
        if (TYPE_ASR.equals(type)) {
            need = new String[]{"model.int8.onnx", "tokens.txt"};
        } else {
            need = new String[]{"model.onnx", "tokens.txt", "lexicon.txt", "phone.fst", "date.fst", "number.fst"};
        }
        // 每个内置 TTS 模型从 assets 的 tts/<modelKey>/ 子目录拷进自身目录，
        // 不能拷到当前激活模型的目录（否则激活其他模型时会把权重放错位置且 ttsDone 误判就绪）。
        File target = TYPE_ASR.equals(type) ? asrDir() : ttsDirFor(modelKey);
        try {
            if (!target.exists() && !target.mkdirs()) return false;
            AssetManager am = appContext.getAssets();
            for (String f : need) {
                copyAssetFile(am, type, modelKey, f, new File(target, f));
            }
            return true;
        } catch (IOException e) {
            Log.w(TAG, "assets copy failed for " + type + (modelKey != null ? "/" + modelKey : "") + ": " + e.getMessage());
            return false;
        }
    }

    private void copyAssetFile(AssetManager am, String type, String modelKey, String name, File out) throws IOException {
        if (out.exists() && out.length() > 0) return;
        String assetPath = ASSETS_ROOT + "/" + type
                + (modelKey == null ? "" : "/" + modelKey) + "/" + name;
        try (InputStream in = am.open(assetPath);
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

    /** 为指定 TTS 模型下载并解压（与当前激活模型无关，未就绪的候选模型也能下载）。 */
    public void downloadTtsModel(final String modelKey, final ProgressListener listener) {
        executor.execute(() -> {
            String[] need = {"model.onnx", "tokens.txt", "lexicon.txt", "phone.fst", "date.fst", "number.fst"};
            File target = ttsDirFor(modelKey);
            if (dirUsable(target, need)) {
                if (listener != null) listener.onFinished("该模型已就绪");
                return;
            }
            File cacheFile = null;
            try {
                cacheFile = ensureCached(modelKey + ".tar.bz2", TYPE_TTS, listener);
                if (!isCancelled() && extract(TYPE_TTS, cacheFile, target)) {
                    if (modelKey.equals(activeTtsModel)) ttsDone = true;
                    if (listener != null) listener.onFinished("下载完成");
                    return;
                }
                if (cacheFile != null) cacheFile.delete();
                if (listener != null) listener.onFinished("下载失败: 解压/校验失败");
            } catch (IOException e) {
                if (cacheFile != null) cacheFile.delete();
                if (listener != null) listener.onFinished("下载失败: " + e.getMessage());
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
        String name = TYPE_ASR.equals(type) ? ASR_TAR : activeTtsModel + ".tar.bz2";
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
        if (TYPE_TTS.equals(type)) {
            File onnx = new File(tmp, "model.onnx");
            if (!onnx.exists()) {
                File[] candidates = tmp.listFiles((d, n) -> n.endsWith(".onnx"));
                if (candidates != null && candidates.length > 0) {
                    // piper 系模型 onnx 名带语音名（如 zh_CN-xiao_ya-medium.onnx），统一为 model.onnx
                    candidates[0].renameTo(onnx);
                }
            }
        }
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