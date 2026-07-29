package com.flowbreak.app;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 本地崩溃日志记录器：捕获未处理的 Java 异常，写入应用缓存目录的 crashes 目录，
 * 文件名格式 crash-yyyyMMdd-HHmmss.txt，最多保留最近 5 个文件。
 *
 * 不上传任何数据，仅本地保存，用户可在隐私与数据页查看或清除。
 */
public final class CrashLogger implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CrashLogger";
    private static final String DIR = "crashes";
    private static final int MAX_FILES = 5;

    private final Context context;
    private final Thread.UncaughtExceptionHandler previous;

    private CrashLogger(Context context) {
        this.context = context.getApplicationContext();
        this.previous = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void install(Context context) {
        try {
            Thread.setDefaultUncaughtExceptionHandler(new CrashLogger(context));
        } catch (Exception e) {
            Log.e(TAG, "Failed to install crash handler", e);
        }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            writeCrash(t, e);
        } catch (Exception ignored) { }
        if (previous != null) previous.uncaughtException(t, e);
    }

    private void writeCrash(Thread t, Throwable e) {
        File dir = new File(context.getCacheDir(), DIR);
        if (!dir.exists() && !dir.mkdirs()) return;
        pruneOldFiles(dir);

        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File file = new File(dir, "crash-" + timestamp + ".txt");
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            osw.write("=== FlowBreak Crash Report ===\n");
            osw.write("Time: " + new Date().toString() + "\n");
            osw.write("Thread: " + (t == null ? "null" : t.getName()) + "\n");
            osw.write("App: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n");
            osw.write("Channel: " + BuildConfig.CHANNEL + "\n");
            osw.write("Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n");
            osw.write("Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n");
            osw.write("\n=== Stack Trace ===\n");
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            osw.write(sw.toString());
        } catch (Exception ignored) { }
    }

    private void pruneOldFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= MAX_FILES) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        int toDelete = files.length - MAX_FILES;
        for (int i = 0; i < toDelete; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }
}
