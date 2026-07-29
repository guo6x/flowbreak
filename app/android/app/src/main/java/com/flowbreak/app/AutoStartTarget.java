package com.flowbreak.app;

import android.content.Intent;

/**
 * 厂商自启动设置页入口解析。
 *
 * 将原 NativeFlowPlugin 内部 AutoStartTarget 提取为顶层类，便于 JVM 单元测试。
 * 不依赖 Context、PackageManager 或 Build，是纯逻辑解析器。
 *
 * 候选顺序、action/component 类型、包名/类名必须与原实现完全一致，
 * 不得调整厂商优先级或删除 fallback 入口。
 */
public final class AutoStartTarget {
    public final String action;
    public final String packageName;
    public final String className;

    private AutoStartTarget(String action, String packageName, String className) {
        this.action = action;
        this.packageName = packageName;
        this.className = className;
    }

    /** 构建 action 类型的候选入口（无具体包名/类名）。 */
    public static AutoStartTarget action(String action) {
        return new AutoStartTarget(action, null, null);
    }

    /** 构建 component 类型的候选入口（无 action）。 */
    public static AutoStartTarget component(String packageName, String className) {
        return new AutoStartTarget(null, packageName, className);
    }

    /**
     * 根据厂商名称（小写）返回自启动设置页候选入口列表。
     * 未知厂商返回空数组。调用方需逐个 try startActivity，失败后回退到应用详情页。
     *
     * 顺序与原 NativeFlowPlugin.autoStartTargets() 完全一致。
     */
    public static AutoStartTarget[] forManufacturer(String manufacturer) {
        if (manufacturer == null) return new AutoStartTarget[0];

        // 小米 / 红米 / 黑鲨（JoyUI 已并入 MIUI 体系，共享 Action 入口）
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")
                || manufacturer.contains("blackshark")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.action("miui.intent.action.OP_AUTO_START"),
                    AutoStartTarget.action("miui.intent.action.POWER_HIDE_MODE_APP_LIST_MANAGER")
            };
        }
        // 华为 EMUI
        if (manufacturer.contains("huawei")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.action("huawei.intent.action.HSM_BOOTAPP_MANAGER"),
                    AutoStartTarget.action("huawei.intent.action.PROTECTED_APPS")
            };
        }
        // 荣耀 MagicOS（独立后入口与 EMUI 不同，先试 hihonor 私有 Action，回退到 EMUI 入口）
        if (manufacturer.contains("honor") || manufacturer.contains("hihonor")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.action("com.hihonor.manager.intent.action.APP_BOOTUP_MANAGER"),
                    AutoStartTarget.action("huawei.intent.action.HSM_BOOTAPP_MANAGER"),
                    AutoStartTarget.action("huawei.intent.action.PROTECTED_APPS")
            };
        }
        // OPPO / 一加 / realme（ColorOS 12+ 宿主包名改为 oplus，老版为 coloros / oppo）
        if (manufacturer.contains("oppo") || manufacturer.contains("realme")
                || manufacturer.contains("oneplus") || manufacturer.contains("oplus")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.component("com.oplus.safecenter",
                            "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
                    AutoStartTarget.component("com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                    AutoStartTarget.component("com.coloros.safecenter",
                            "com.coloros.safecenter.permission.PermissionTopActivity"),
                    AutoStartTarget.component("com.oppo.safe",
                            "com.oppo.safe.permission.permission.TopActivity")
            };
        }
        // vivo / iQOO（OriginOS / FuntouchOS）
        if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.component("com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    AutoStartTarget.component("com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                    AutoStartTarget.component("com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.BgStartUpManagerActivity")
            };
        }
        // 魅族 Flyme
        if (manufacturer.contains("meizu")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.component("com.meizu.safe",
                            "com.meizu.safe.permission.SmartBGActivity"),
                    AutoStartTarget.component("com.meizu.safe",
                            "com.meizu.safe.security.SHOW_APPSEC")
            };
        }
        // 三星 One UI（无"自启动"概念，引导到 Smart Manager / 设备维护，多版本兜底）
        if (manufacturer.contains("samsung")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.component("com.samsung.android.lool",
                            "com.samsung.android.lool.SmartManagerDetail"),
                    AutoStartTarget.component("com.samsung.android.sm.battery",
                            "com.samsung.android.sm.battery.ui.BatteryActivity"),
                    AutoStartTarget.component("com.samsung.android.sm",
                            "com.samsung.android.sm.ui.battery.BatteryActivity")
            };
        }
        // 乐视 EUI（含 leeco 别名）
        if (manufacturer.contains("letv") || manufacturer.contains("leeco")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.component("com.letv.android.letvsafe",
                            "com.letv.android.letvsafe.permission.PermissionTopActivity")
            };
        }
        // 华硕 ZenUI
        if (manufacturer.contains("asus")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.component("com.asus.mobilemanager",
                            "com.asus.mobilemanager.entry.FunctionActivity"),
                    AutoStartTarget.component("com.asus.mobilemanager",
                            "com.asus.mobilemanager.autostart.AutoStartActivity")
            };
        }
        // 中兴 / 努比亚（MyOS / nubia UI）
        if (manufacturer.contains("zte") || manufacturer.contains("nubia")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.component("com.zte.heartyservices",
                            "com.zte.heartyservices.startupmanager.BootUpMgrActivity"),
                    AutoStartTarget.action("com.zte.heartyservices.intent.action.STARTUP_MANAGER")
            };
        }
        // 联想 / 摩托罗拉（ZUI；摩托罗拉接近原生，主要靠电池优化白名单）
        if (manufacturer.contains("lenovo") || manufacturer.contains("motorola")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.component("com.lenovo.guardhouse",
                            "com.lenovo.guardhouse.autoboot.AutoBootActivity")
            };
        }
        return new AutoStartTarget[0];
    }

    /** 把候选入口转换为可 startActivity 的 Intent。 */
    public Intent buildIntent() {
        Intent intent;
        if (action != null) {
            intent = new Intent(action);
        } else {
            intent = new Intent();
            intent.setClassName(packageName, className);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
