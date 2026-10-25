// Browser layout/interaction fixtures only. No Android bridge, permissions,
// OEM services or physical devices are exercised by this module.
import assert from 'node:assert/strict';
import path from 'node:path';

export async function checkOemGuidance(browser, url, output, observePage, record) {
  const scenarios = [
    { brand: 'Xiaomi', label: '小米 / 红米 / 黑鲨', hint: '后台自启动' },
    { brand: 'OPPO', label: 'OPPO / 一加 / realme', hint: '耗电管理', domestic: true },
    { brand: 'HUAWEI', label: '华为', hint: '应用启动管理', domestic: true },
    { brand: 'HONOR', label: '荣耀', hint: '应用启动管理' },
    { brand: 'Samsung', label: '三星', hint: '从不休眠', restricted: true },
    { brand: 'Google', label: '系统', hint: '受限制' },
    { brand: 'vivo', unsupported: true, domestic: true },
  ];
  for (const width of [320, 393]) {
    for (const scenario of scenarios) {
      const context = await browser.newContext({
        viewport: { width, height: 852 }, reducedMotion: 'reduce', locale: 'zh-CN',
      });
      const label = `synthetic-oem-${scenario.brand.toLowerCase()}-${width}`;
      try {
        await context.addInitScript(({ brand, domestic, unsupported, restricted }) => {
          localStorage.setItem('fb_monitoring_enabled', 'false');
          localStorage.setItem('fb_profile', JSON.stringify({ onboardingDone: true, targetApps: [] }));
          const permissions = {
            hasUsageStats: true, hasOverlay: true, hasNotification: false,
            hasAccessibility: false, isIgnoringBattery: false,
            isDomestic: !!domestic, channel: domestic ? 'domestic' : 'play',
            manufacturer: brand, unsupportedDevice: !!unsupported,
            protectionRuntimeAvailable: !unsupported,
            backgroundRestriction: restricted ? 'restricted' : 'unknown',
          };
          window.__settingsCalls = 0;
          window.androidBridge = {}; // Deliberately synthetic; never forwards to a device.
          window.Capacitor = {
            PluginHeaders: [{ name: 'NativeFlow', methods: [
              ...['checkPermissions', 'loadSettings', 'getProtectionStatus', 'getBlockState',
                'getDashboardSummary', 'consumePendingNavigation', 'stopService', 'startService',
                'openAutoStartSettings'].map(name => ({ name, rtype: 'promise' })),
              { name: 'addListener', rtype: 'callback' }, { name: 'removeListener', rtype: 'callback' },
            ] }],
            nativeCallback: () => Promise.resolve('fixture-listener'),
            nativePromise: async (_plugin, method) => {
              if (method === 'checkPermissions') return permissions;
              if (method === 'loadSettings') return { limitMinutes: 25, restDuration: 180,
                allowEmergencyUnlock: true, strongBlockingEnabled: false,
                monitoringEnabled: false, targetApps: [], channel: permissions.channel };
              if (method === 'getProtectionStatus') return { status: 'PAUSED', reason: 'MONITORING_DISABLED',
                coreProtectionOperational: false, strongBlockingOperational: false, permissions };
              if (method === 'getBlockState') return { state: 'IDLE', sessionSeconds: 0, graceUntil: 0 };
              if (method === 'getDashboardSummary') return {};
              if (method === 'consumePendingNavigation') return { path: '' };
              if (method === 'openAutoStartSettings') { window.__settingsCalls++; return; }
              if (method === 'stopService' || method === 'startService') return;
              throw new Error(`Unimplemented synthetic method: ${method}`);
            },
          };
        }, scenario);
        const page = await context.newPage();
        observePage(page);
        await page.goto(`${url}/permissions`);
        await page.waitForLoadState('networkidle');
        if (scenario.unsupported) {
          await page.getByRole('button', { name: '当前版本暂不支持在此设备上开启保护' }).waitFor();
          assert.equal(await page.getByText('提升后台稳定性（可稍后设置）').count(), 0);
        } else {
          const expand = page.getByRole('button', { name: '提升后台稳定性（可稍后设置）' });
          await expand.click();
          await page.getByText(`${scenario.label} 后台设置`, { exact: true }).waitFor();
          await page.getByText(scenario.hint, { exact: false }).first().waitFor();
          if (scenario.restricted) await page.getByRole('alert').filter({ hasText: '系统当前限制后台运行' }).waitFor();
          const open = page.getByRole('button', { name: `设置${scenario.label} 后台设置` });
          await open.click();
          await page.getByText('请在系统中确认', { exact: true }).waitFor();
          assert.equal(await page.evaluate(() => window.__settingsCalls), 1);
          assert.equal(await page.getByRole('button', { name: '设置无障碍强阻断' }).count(), scenario.domestic ? 1 : 0);
          await page.getByRole('button', { name: '继续设置保护', exact: true }).scrollIntoViewIfNeeded();
        }
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'Guidance overflows viewport');
        await page.screenshot({ path: path.join(output, `${label}.png`), fullPage: true });
        record({ viewport: width, route: label, kind: 'SYNTHETIC_NATIVE_BRIDGE_ONLY', status: 'PASS' });
      } catch (error) {
        record({ viewport: width, route: label, kind: 'SYNTHETIC_NATIVE_BRIDGE_ONLY', status: 'FAIL', error: error.message });
      } finally {
        await context.close();
      }
    }
  }
}
