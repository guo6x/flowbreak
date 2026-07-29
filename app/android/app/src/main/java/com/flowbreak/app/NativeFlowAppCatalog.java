package com.flowbreak.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 负责桌面可启动应用列表、目标应用过滤策略、应用图标转 data URI、应用使用时长列表。
 *
 * 保持原有 Bitmap recycle 行为、PNG 质量、图标尺寸、排序方式、protected packages 规则、
 * Play 渠道排除整个微信包规则。
 */
public final class NativeFlowAppCatalog {
    private final Context context;
    private final NativeFlowPermissionManager permissions;

    public NativeFlowAppCatalog(Context context, NativeFlowPermissionManager permissions) {
        this.context = context.getApplicationContext();
        this.permissions = permissions;
    }

    /**
     * 获取桌面可启动应用列表，过滤受保护包和不支持的目标包。
     * 返回包含 apps JSArray 的 JSObject。
     */
    public JSObject launchableApps() {
        PackageManager pm = context.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(launcher, 0);
        Set<String> protectedPackages = permissions.protectedPackages(pm);
        Map<String, JSObject> unique = new HashMap<>();
        for (ResolveInfo info : infos) {
            String packageName = info.activityInfo.packageName;
            if (protectedPackages.contains(packageName) || !isSupportedTargetPackage(packageName)) continue;
            JSObject app = new JSObject();
            app.put("packageName", packageName);
            app.put("label", info.loadLabel(pm).toString());
            app.put("iconDataUri", drawableDataUri(info.loadIcon(pm)));
            unique.put(packageName, app);
        }
        List<JSObject> sorted = new ArrayList<>(unique.values());
        Collections.sort(sorted, Comparator.comparing(
                value -> value.optString("label", ""), String.CASE_INSENSITIVE_ORDER
        ));
        JSArray result = new JSArray();
        for (JSObject app : sorted) result.put(app);
        JSObject response = new JSObject();
        response.put("apps", result);
        return response;
    }

    /**
     * 过滤目标应用：剔除受保护包和不支持的目标包，去重空字符串。
     * 不改变原有 Play 渠道排除 com.tencent.mm 的规则。
     */
    public Set<String> filterTargetApps(Set<String> values) {
        Set<String> protectedValues = permissions.protectedPackages(context.getPackageManager());
        boolean isPlay = "play".equals(BuildConfig.CHANNEL);
        return filterTargetApps(values, protectedValues, isPlay);
    }

    /**
     * Play 渠道不支持 com.tencent.mm 作为目标应用（无法区分聊天与视频号）。
     * 其他包名默认支持。
     */
    public boolean isSupportedTargetPackage(String packageName) {
        return isSupportedTargetPackage(packageName, "play".equals(BuildConfig.CHANNEL));
    }

    /**
     * 纯函数版本：根据是否为 Play 渠道判断包名是否可作为目标应用。
     * 便于 JVM 单元测试，不依赖 BuildConfig。
     */
    public static boolean isSupportedTargetPackage(String packageName, boolean isPlayChannel) {
        return !(isPlayChannel && "com.tencent.mm".equals(packageName));
    }

    /**
     * 纯函数版本：根据受保护包集合和渠道标志过滤目标应用。
     * 剔除受保护包和不支持的目标包，忽略空字符串。
     * 便于 JVM 单元测试，不依赖 PackageManager 或 BuildConfig。
     */
    public static Set<String> filterTargetApps(
            Set<String> values, Set<String> protectedValues, boolean isPlayChannel) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (value == null) continue;
            String trimmed = value.trim();
            if (trimmed.isEmpty()) continue;
            if (!protectedValues.contains(trimmed)
                    && isSupportedTargetPackage(trimmed, isPlayChannel)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** Drawable 转 data:image/png;base64,... 字符串。Bitmap 必须 recycle。 */
    public String drawableDataUri(Drawable drawable) {
        int width = Math.max(1, Math.min(96, drawable.getIntrinsicWidth()));
        int height = Math.max(1, Math.min(96, drawable.getIntrinsicHeight()));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, output);
        bitmap.recycle();
        return "data:image/png;base64," + Base64.encodeToString(
                output.toByteArray(), Base64.NO_WRAP
        );
    }

    /**
     * 把 JSArray 转为去重、去空白的字符串集合。
     * 与原 NativeFlowPlugin.toStringSet() 行为一致：trim 后忽略空字符串。
     */
    public static Set<String> toStringSet(JSArray array) {
        Set<String> result = new HashSet<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }
}
