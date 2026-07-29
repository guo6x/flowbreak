package com.flowbreak.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 负责诊断信息、数据导出、迁移、分享、清除、构建信息、待处理导航、崩溃日志。
 *
 * 保持原有：
 * - diagnostics formatVersion=1
 * - full export formatVersion=2
 * - migration version=1
 * - crash log 单文件最大读取 64KB
 * - 分享 MIME type=application/json
 * - 文件命名 flowbreak-export-yyyy-MM-dd.json / flowbreak-diagnostics-yyyy-MM-dd.json
 * - 清除数据范围：DB + exports/ + crashes/ + SharedPreferences.clear()
 * - 不清除应用运行所必需的文件
 *
 * 不持有 Activity。分享 Intent 由调用方在 UI 线程启动。
 */
public final class NativeFlowDataManager {
    private final Context context;

    public NativeFlowDataManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 诊断 JSON 对象，包含 formatVersion=1 和所有运行状态字段。 */
    public JSObject diagnostics(SharedPreferences prefs) {
        long now = System.currentTimeMillis();
        FlowDao dao = FlowDatabase.get(context).flowDao();
        long heartbeat = Math.max(
                FlowForegroundService.getLastTickAt(),
                prefs.getLong("serviceHeartbeatAt", 0L)
        );
        JSObject result = new JSObject();
        result.put("versionName", BuildConfig.VERSION_NAME);
        result.put("versionCode", BuildConfig.VERSION_CODE);
        result.put("channel", BuildConfig.CHANNEL);
        result.put("packageName", context.getPackageName());
        result.put("databaseVersion", 3);
        result.put("serviceAlive", heartbeat > 0L && now - heartbeat < 45_000L);
        result.put("serviceHeartbeatAt", heartbeat);
        result.put("lastUsageEventAt", FlowForegroundService.getLastUsageEventAt());
        result.put("state", FlowForegroundService.getState().name());
        result.put("sessionSeconds", FlowForegroundService.getSessionSeconds());
        result.put("graceUntil", FlowForegroundService.getGraceUntil());
        result.put("monitoringEnabled", prefs.getBoolean("monitoringEnabled", true));
        result.put("targetCount", PreferenceUtils.getMigratedTargetApps(prefs).size());
        result.put("eventCount", dao.eventCount());
        result.put("usageRowCount", dao.usageRowCount());
        result.put("latestEventAt", dao.latestEventAt());
        return result;
    }

    /** 诊断 JSON 字符串（含 permissions 字段）。 */
    public String diagnosticsJson(NativeFlowPermissionManager permissions, SharedPreferences prefs) {
        JSObject root = diagnostics(prefs);
        root.put("formatVersion", 1);
        root.put("exportedAt", new Date().toString());
        root.put("permissions", permissions.permissionState());
        return root.toString();
    }

    /**
     * 完整数据导出 JSON 字符串。formatVersion=2。
     * 包含 settings、usage、dailySummaries、events、progress、webCache。
     */
    public String exportJson(JSObject uiData, SharedPreferences prefs) {
        FlowDao dao = FlowDatabase.get(context).flowDao();
        JSObject root = new JSObject();
        root.put("formatVersion", 2);
        root.put("exportedAt", new Date().toString());
        root.put("channel", BuildConfig.CHANNEL);
        JSObject settings = new JSObject();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Set) {
                JSArray values = new JSArray();
                for (Object item : (Set<?>) value) values.put(String.valueOf(item));
                settings.put(entry.getKey(), values);
            } else {
                settings.put(entry.getKey(), value);
            }
        }
        root.put("settings", settings);
        JSArray usage = new JSArray();
        for (DailyUsageEntity item : dao.allUsage()) {
            JSObject row = new JSObject();
            row.put("date", item.date);
            row.put("packageName", item.packageName);
            row.put("seconds", item.seconds);
            usage.put(row);
        }
        root.put("usage", usage);
        JSArray summaries = new JSArray();
        for (DailySummaryEntity item : dao.allSummaries()) {
            JSObject row = new JSObject();
            row.put("date", item.date);
            row.put("legacyScreenSeconds", item.legacyScreenSeconds);
            row.put("restCount", item.restCount);
            row.put("interventionCount", item.interventionCount);
            row.put("blockCount", item.blockCount);
            row.put("unlockSeconds", item.graceSeconds);
            row.put("pullbackOutcomeCount", item.pullbackOutcomeCount);
            row.put("successfulPullbackCount", item.successfulPullbackCount);
            row.put("postRestReturnCount", item.postRestReturnCount);
            row.put("postRestTargetSeconds", item.postRestTargetSeconds);
            row.put("reflectionValue", item.reflectionValue);
            row.put("reflectionUpdatedAt", item.reflectionUpdatedAt);
            summaries.put(row);
        }
        root.put("dailySummaries", summaries);
        JSArray events = new JSArray();
        for (FlowEventEntity item : dao.allEvents()) {
            JSObject row = new JSObject();
            row.put("id", item.id);
            row.put("timestamp", item.timestamp);
            row.put("type", item.type);
            row.put("packageName", item.packageName);
            row.put("activity", item.activity);
            row.put("durationSeconds", item.durationSeconds);
            row.put("metadata", item.metadata);
            events.put(row);
        }
        root.put("events", events);
        ProgressEntity progress = dao.getProgress();
        JSObject progressJson = new JSObject();
        progressJson.put("points", progress == null ? 0 : progress.points);
        progressJson.put("streak", progress == null ? 0 : progress.streak);
        progressJson.put("lastRestDay", progress == null ? "" : progress.lastRestDay);
        progressJson.put("achievements", progress == null ? "[]" : progress.achievementsJson);
        root.put("progress", progressJson);
        root.put("webCache", uiData == null ? new JSObject() : uiData);
        return root.toString();
    }

    /**
     * 把字符串写入 exports 目录下的指定文件名，返回 FileProvider URI。
     * 调用方使用返回的 URI 构建分享 Intent。
     */
    public Uri writeExportFile(String fileName, String content) {
        File directory = new File(context.getCacheDir(), "exports");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create export directory");
        }
        File file = new File(directory, fileName);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot write export file", e);
        }
        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file
        );
    }

    /** 构建分享 Intent。MIME type=application/json，带读权限。 */
    public Intent buildShareIntent(Uri uri, String chooserTitle) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/json");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(share, chooserTitle);
    }

    /**
     * 迁移 legacy 数据。version=1。
     * 使用 runInTransaction 保证原子性；commit 失败抛 IllegalStateException。
     * 已迁移过则返回 migrated=false。
     */
    public JSObject migrateLegacyData(JSObject payload, SharedPreferences prefs) {
        if (prefs.getInt("migrationVersion", 0) >= 1) {
            JSObject result = new JSObject();
            result.put("migrated", false);
            result.put("version", 1);
            return result;
        }
        FlowDatabase database = FlowDatabase.get(context);
        FlowDao dao = database.flowDao();
        database.runInTransaction(() -> {
            ProgressEntity existing = dao.getProgress();
            int legacyPoints = Math.max(0, payload.optInt("points", 0));
            int legacyStreak = Math.max(0, payload.optInt("streak", 0));
            JSONArray legacyAchievements = payload.optJSONArray("achievements");
            String achievementsJson = legacyAchievements == null ? "[]" : legacyAchievements.toString();
            if (existing != null) {
                legacyPoints = Math.max(legacyPoints, existing.points);
                legacyStreak = Math.max(legacyStreak, existing.streak);
                if ((legacyAchievements == null || legacyAchievements.length() == 0)
                        && existing.achievementsJson != null
                        && !existing.achievementsJson.isEmpty()) {
                    achievementsJson = existing.achievementsJson;
                }
            }
            dao.saveProgress(new ProgressEntity(legacyPoints, legacyStreak, "", achievementsJson));

            JSONObject legacyStats = payload.optJSONObject("stats");
            if (legacyStats != null) {
                java.util.Iterator<String> dates = legacyStats.keys();
                while (dates.hasNext()) {
                    String date = dates.next();
                    JSONObject stat = legacyStats.optJSONObject(date);
                    if (stat == null) continue;
                    dao.mergeLegacySummarySafely(
                            date,
                            Math.max(0L, stat.optLong("totalScreenTime", 0L)),
                            Math.max(0, stat.optInt("restCount", 0)),
                            Math.max(0, stat.optInt("interventionCount", 0))
                    );
                }
            }
            // The source payload is retained as migration metadata so
            // no legacy field is silently discarded, while current
            // screens use the normalized Room records above.
            dao.insertEvent(new FlowEventEntity(
                    System.currentTimeMillis(), "migration", "", "", 0, payload.toString()
            ));
        });
        if (!prefs.edit().putInt("migrationVersion", 1).commit()) {
            throw new IllegalStateException("无法保存迁移版本");
        }
        JSObject result = new JSObject();
        result.put("migrated", true);
        result.put("version", 1);
        return result;
    }

    /**
     * 清除本地数据。
     * 顺序：先标记 dataErasing+停止监控+停止服务 -> 清 DB -> 删 exports/crashes -> clear prefs。
     * 失败时回滚 dataErasing=false。
     */
    public void clearLocalData(SharedPreferences prefs, NativeFlowServiceController serviceController) throws Exception {
        if (!prefs.edit()
                .putBoolean("dataErasing", true)
                .putBoolean("monitoringEnabled", false)
                .putBoolean("serviceConfigured", false)
                .commit()) {
            throw new IllegalStateException("无法准备本地数据清除");
        }
        try {
            serviceController.stopService();
            FlowRepository.get(context).clearAllBlocking();
            deleteRecursively(new File(context.getCacheDir(), "exports"));
            deleteRecursively(new File(context.getCacheDir(), "crashes"));
            if (!prefs.edit().clear().commit()) {
                throw new IllegalStateException("无法完成本地数据清除");
            }
        } catch (Exception error) {
            prefs.edit().putBoolean("dataErasing", false).apply();
            throw error;
        }
    }

    /** 构建信息。 */
    public JSObject buildInfo() {
        JSObject result = new JSObject();
        result.put("versionName", BuildConfig.VERSION_NAME);
        result.put("versionCode", BuildConfig.VERSION_CODE);
        result.put("channel", BuildConfig.CHANNEL);
        result.put("packageName", context.getPackageName());
        return result;
    }

    /**
     * 读取并清除 pendingNavigation。
     * 返回 path 字段（空字符串表示无待处理导航）。
     */
    public JSObject consumePendingNavigation(SharedPreferences prefs) {
        String path = prefs.getString("pendingNavigation", "");
        prefs.edit().remove("pendingNavigation").apply();
        JSObject result = new JSObject();
        result.put("path", path == null ? "" : path);
        return result;
    }

    /**
     * 读取崩溃日志列表。单文件最大读取 64KB，按 lastModified 倒序。
     * 读取异常的文件被静默跳过（与原实现一致）。
     */
    public JSObject crashLogs() {
        File dir = new File(context.getCacheDir(), "crashes");
        JSArray logs = new JSArray();
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File file : files) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                    byte[] data = new byte[(int) Math.min(file.length(), 64 * 1024L)];
                    int read = fis.read(data);
                    JSObject entry = new JSObject();
                    entry.put("filename", file.getName());
                    entry.put("timestamp", file.lastModified());
                    entry.put("content", new String(data, 0, Math.max(0, read), StandardCharsets.UTF_8));
                    logs.put(entry);
                } catch (Exception ignored) { }
            }
        }
        JSObject result = new JSObject();
        result.put("logs", logs);
        return result;
    }

    /** 清除所有崩溃日志。 */
    public void clearCrashLogs() {
        File dir = new File(context.getCacheDir(), "crashes");
        File[] files = dir.listFiles();
        if (files != null) for (File file : files) file.delete();
    }

    /** 递归删除文件/目录。删除失败抛 IllegalStateException。 */
    private static void deleteRecursively(File target) {
        if (target == null || !target.exists()) return;
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        if (!target.delete() && target.exists()) {
            throw new IllegalStateException("无法删除本地缓存: " + target.getName());
        }
    }
}
