package com.shrimpfarm.app;

import android.content.Context;
import android.content.Intent;
import android.os.Process;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String CRASH_DIR = "crash_logs";
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public CrashHandler(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            saveCrashLog(throwable);
        } catch (Exception ignored) {
        }

        Intent intent = new Intent(context, CrashReportActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);

        Process.killProcess(Process.myPid());
    }

    private void saveCrashLog(Throwable throwable) {
        File dir = new File(context.getFilesDir(), CRASH_DIR);
        if (!dir.exists()) dir.mkdirs();

        String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File file = new File(dir, "crash_" + time + ".log");

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("=== 小棚养虾 崩溃日志 ===");
            pw.println("时间: " + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            pw.println("Android API: " + android.os.Build.VERSION.SDK_INT);
            pw.println("设备: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            pw.println();
            throwable.printStackTrace(pw);
            pw.flush();
        } catch (Exception ignored) {
        }
    }

    public static boolean hasCrashLog(Context context) {
        File dir = new File(context.getFilesDir(), CRASH_DIR);
        if (!dir.exists()) return false;
        File[] files = dir.listFiles((d, name) -> name.startsWith("crash_") && name.endsWith(".log"));
        return files != null && files.length > 0;
    }

    public static String getLatestCrashLog(Context context) {
        File dir = new File(context.getFilesDir(), CRASH_DIR);
        if (!dir.exists()) return null;
        File[] files = dir.listFiles((d, name) -> name.startsWith("crash_") && name.endsWith(".log"));
        if (files == null || files.length == 0) return null;
        File latest = files[0];
        for (File f : files) {
            if (f.lastModified() > latest.lastModified()) latest = f;
        }
        try {
            return new String(java.nio.file.Files.readAllBytes(latest.toPath()));
        } catch (Exception e) {
            return null;
        }
    }

    public static void clearCrashLogs(Context context) {
        File dir = new File(context.getFilesDir(), CRASH_DIR);
        if (!dir.exists()) return;
        File[] files = dir.listFiles((d, name) -> name.startsWith("crash_") && name.endsWith(".log"));
        if (files != null) {
            for (File f : files) f.delete();
        }
    }
}
