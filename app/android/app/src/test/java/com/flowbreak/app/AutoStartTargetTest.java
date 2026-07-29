package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * 验证厂商自启动候选入口解析。
 * 不依赖真实设备，只验证 forManufacturer 的纯逻辑。
 *
 * 必须保持原有候选顺序、action/component 类型和构建信息正确。
 */
public class AutoStartTargetTest {
    @Test public void xiaomiRedmiBlacksharkShareMiuiActionEntries() {
        for (String brand : new String[]{"xiaomi", "redmi", "blackshark"}) {
            AutoStartTarget[] targets = AutoStartTarget.forManufacturer(brand);
            assertEquals(brand + " should have 2 candidates", 2, targets.length);
            assertEquals("miui.intent.action.OP_AUTO_START", targets[0].action);
            assertEquals("miui.intent.action.POWER_HIDE_MODE_APP_LIST_MANAGER", targets[1].action);
            assertNullComponent(targets[0]);
            assertNullComponent(targets[1]);
        }
    }

    @Test public void huaweiEmuiUsesActionEntries() {
        AutoStartTarget[] targets = AutoStartTarget.forManufacturer("huawei");
        assertEquals(2, targets.length);
        assertEquals("huawei.intent.action.HSM_BOOTAPP_MANAGER", targets[0].action);
        assertEquals("huawei.intent.action.PROTECTED_APPS", targets[1].action);
    }

    @Test public void honorTriesHihonorActionBeforeFallingBackToEmui() {
        for (String brand : new String[]{"honor", "hihonor"}) {
            AutoStartTarget[] targets = AutoStartTarget.forManufacturer(brand);
            assertEquals(brand + " should have 3 candidates", 3, targets.length);
            assertEquals("com.hihonor.manager.intent.action.APP_BOOTUP_MANAGER", targets[0].action);
            assertEquals("huawei.intent.action.HSM_BOOTAPP_MANAGER", targets[1].action);
            assertEquals("huawei.intent.action.PROTECTED_APPS", targets[2].action);
        }
    }

    @Test public void oppoFamilyUsesComponentEntriesWithOplusFirst() {
        for (String brand : new String[]{"oppo", "realme", "oneplus", "oplus"}) {
            AutoStartTarget[] targets = AutoStartTarget.forManufacturer(brand);
            assertEquals(brand + " should have 4 candidates", 4, targets.length);
            // oplus safecenter must come first (ColorOS 12+)
            assertEquals("com.oplus.safecenter", targets[0].packageName);
            assertEquals(
                    "com.oplus.safecenter.permission.startup.StartupAppListActivity",
                    targets[0].className
            );
            assertNullAction(targets[0]);
            // Then coloros safecenter
            assertEquals("com.coloros.safecenter", targets[1].packageName);
            // Then old oppo safe
            assertEquals("com.oppo.safe", targets[3].packageName);
        }
    }

    @Test public void vivoAndIqooUseComponentEntries() {
        for (String brand : new String[]{"vivo", "iqoo"}) {
            AutoStartTarget[] targets = AutoStartTarget.forManufacturer(brand);
            assertEquals(brand + " should have 3 candidates", 3, targets.length);
            assertEquals("com.vivo.permissionmanager", targets[0].packageName);
            assertEquals("com.iqoo.secure", targets[1].packageName);
            assertEquals("com.iqoo.secure", targets[2].packageName);
        }
    }

    @Test public void samsungUsesComponentEntriesWithLoolFirst() {
        AutoStartTarget[] targets = AutoStartTarget.forManufacturer("samsung");
        assertEquals(3, targets.length);
        assertEquals("com.samsung.android.lool", targets[0].packageName);
        assertEquals("com.samsung.android.sm.battery", targets[1].packageName);
        assertEquals("com.samsung.android.sm", targets[2].packageName);
    }

    @Test public void meizuUsesComponentEntries() {
        AutoStartTarget[] targets = AutoStartTarget.forManufacturer("meizu");
        assertEquals(2, targets.length);
        assertEquals("com.meizu.safe", targets[0].packageName);
        assertEquals("com.meizu.safe.permission.SmartBGActivity", targets[0].className);
    }

    @Test public void asusUsesComponentEntries() {
        AutoStartTarget[] targets = AutoStartTarget.forManufacturer("asus");
        assertEquals(2, targets.length);
        assertEquals("com.asus.mobilemanager", targets[0].packageName);
        assertEquals("com.asus.mobilemanager.entry.FunctionActivity", targets[0].className);
        assertEquals("com.asus.mobilemanager.autostart.AutoStartActivity", targets[1].className);
    }

    @Test public void zteAndNubiaMixComponentAndAction() {
        for (String brand : new String[]{"zte", "nubia"}) {
            AutoStartTarget[] targets = AutoStartTarget.forManufacturer(brand);
            assertEquals(brand + " should have 2 candidates", 2, targets.length);
            // First: component
            assertEquals("com.zte.heartyservices", targets[0].packageName);
            assertNullAction(targets[0]);
            // Second: action
            assertEquals("com.zte.heartyservices.intent.action.STARTUP_MANAGER", targets[1].action);
            assertNullComponent(targets[1]);
        }
    }

    @Test public void lenovoAndMotorolaUseSingleComponentEntry() {
        for (String brand : new String[]{"lenovo", "motorola"}) {
            AutoStartTarget[] targets = AutoStartTarget.forManufacturer(brand);
            assertEquals(brand + " should have 1 candidate", 1, targets.length);
            assertEquals("com.lenovo.guardhouse", targets[0].packageName);
            assertEquals("com.lenovo.guardhouse.autoboot.AutoBootActivity", targets[0].className);
        }
    }

    @Test public void letvAndLeecoUseSingleComponentEntry() {
        for (String brand : new String[]{"letv", "leeco"}) {
            AutoStartTarget[] targets = AutoStartTarget.forManufacturer(brand);
            assertEquals(brand + " should have 1 candidate", 1, targets.length);
            assertEquals("com.letv.android.letvsafe", targets[0].packageName);
        }
    }

    @Test public void unknownManufacturerReturnsEmptyArray() {
        AutoStartTarget[] targets = AutoStartTarget.forManufacturer("unknownbrand");
        assertNotNull(targets);
        assertEquals(0, targets.length);
    }

    @Test public void nullManufacturerReturnsEmptyArray() {
        AutoStartTarget[] targets = AutoStartTarget.forManufacturer(null);
        assertNotNull(targets);
        assertEquals(0, targets.length);
    }

    @Test public void manufacturerMatchingIsCaseInsensitive() {
        AutoStartTarget[] targets = AutoStartTarget.forManufacturer("XIAOMI");
        assertEquals(2, targets.length);
        assertEquals("miui.intent.action.OP_AUTO_START", targets[0].action);

        AutoStartTarget[] huawei = AutoStartTarget.forManufacturer("HUAWEI");
        assertEquals(2, huawei.length);
    }

    @Test public void actionFactoryCreatesActionOnlyTarget() {
        AutoStartTarget target = AutoStartTarget.action("test.action");
        assertEquals("test.action", target.action);
        assertNull(target.packageName);
        assertNull(target.className);
    }

    @Test public void componentFactoryCreatesComponentOnlyTarget() {
        AutoStartTarget target = AutoStartTarget.component("com.test", "com.test.Activity");
        assertNull(target.action);
        assertEquals("com.test", target.packageName);
        assertEquals("com.test.Activity", target.className);
    }

    private static void assertNullAction(AutoStartTarget target) {
        assertTrue("expected action to be null for " + target, target.action == null);
    }

    private static void assertNullComponent(AutoStartTarget target) {
        assertTrue("expected packageName to be null for " + target, target.packageName == null);
        assertTrue("expected className to be null for " + target, target.className == null);
    }
}
