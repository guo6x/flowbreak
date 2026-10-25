// Browser-only acceptance. This never represents Android device acceptance.
import assert from 'node:assert/strict';
import { mkdir, writeFile, access, readdir, stat } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { createServer } from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { stripVTControlCharacters } from 'node:util';
import { chromium } from 'playwright';

const root = fileURLToPath(new URL('..', import.meta.url));
const arg = name => { const i = process.argv.indexOf(name); return i >= 0 ? process.argv[i + 1] : null; };
const url = arg('--url') ?? 'http://127.0.0.1:4175';
const output = path.resolve(root, arg('--out') ?? '../.tmp/ui-smoke');
const routes = ['/onboarding', '/permissions', '/personalize', '/dashboard', '/stats', '/profile', '/profile-preferences', '/target-apps', '/blocking-settings', '/privacy', '/validation', '/help', '/rest'];
const expectedHeadings = {
  '/onboarding': '自然唤醒', '/permissions': '开始保护前需要两项权限',
  '/personalize': '设置你的保护规则', '/dashboard': '把时间，留给自己',
  '/stats': '使用统计', '/profile': '我的', '/profile-preferences': '偏好设置',
  '/target-apps': '受限应用', '/blocking-settings': '阻断设置',
  '/privacy': '隐私与本地数据', '/validation': '效果与运行诊断',
  '/help': '使用指南', '/rest': /眼部放松|身体拉伸|深呼吸/,
};
const sizes = [{ width: 320, height: 740 }, { width: 393, height: 852 }, { width: 1280, height: 900 }];
let server;
let browser;
const failures = [];
const results = [];
const unexpectedRequests = new Set();
const failedResources = new Set();
const pageErrors = [];
const consoleErrors = [];

async function collectBuildInputs(directory, files = []) {
  let entries;
  try { entries = await readdir(directory, { withFileTypes: true }); }
  catch { return files; }
  for (const entry of entries) {
    if (entry.isDirectory()) {
      if (!['node_modules', 'dist', 'coverage', '__tests__', 'test', 'tests'].includes(entry.name)) {
        await collectBuildInputs(path.join(directory, entry.name), files);
      }
    } else if (entry.isFile() && !/\.(?:test|spec)\.[^.]+$/.test(entry.name)) {
      files.push(path.join(directory, entry.name));
    }
  }
  return files;
}

async function assertFreshWebBuild() {
  const distPath = path.join(root, 'dist/index.html');
  const distStat = await stat(distPath);
  const sourceFiles = [
    ...await collectBuildInputs(path.join(root, 'src')),
    ...await collectBuildInputs(path.join(root, 'public')),
  ];
  const entries = await readdir(root, { withFileTypes: true });
  sourceFiles.push(...entries
    .filter(entry => entry.isFile() && /^(?:vite\.config\.|tailwind\.config\.|postcss\.config\.|tsconfig(?:\.|$)|package(?:-lock)?\.json$|index\.html$)/.test(entry.name))
    .map(entry => path.join(root, entry.name)));
  const newer = [];
  for (const file of sourceFiles) {
    const sourceStat = await stat(file);
    if (sourceStat.mtimeMs > distStat.mtimeMs) newer.push(path.relative(root, file));
  }
  if (newer.length) throw new Error(`STALE_WEB_BUILD: dist/index.html is older than build input(s): ${newer.join(', ')}. Run npm run build before npm run test:ui.`);
}

try {
  await mkdir(output, { recursive: true });
  if (!arg('--url')) {
    await access(path.join(root, 'dist/index.html')).catch(() => { throw new Error('Run npm run build before npm run test:ui.'); });
    await assertFreshWebBuild();
    // Never certify an old/unrelated process that happens to own our port.
    const probe = createServer();
    await new Promise((resolve, reject) => {
      probe.once('error', () => reject(new Error('Port 4175 is occupied. Stop its owner or explicitly pass --url.')));
      probe.listen(4175, '127.0.0.1', () => probe.close(resolve));
    });
    server = spawn(process.execPath, ['node_modules/vite/bin/vite.js', 'preview', '--host', '127.0.0.1', '--port', '4175', '--strictPort'], { cwd: root, stdio: 'pipe', windowsHide: true });
    let serverOutput = '';
    server.stdout.on('data', data => { serverOutput += data; });
    server.stderr.on('data', data => { serverOutput += data; });
    let startError;
    server.on('error', error => { startError = error; });
    const deadline = Date.now() + 20_000;
    while (true) {
      if (startError) throw startError;
      if (server.exitCode !== null) throw new Error(`Preview failed: ${serverOutput}`);
      // Vite styles the port separately in CI; ANSI must not hide a ready URL.
      if (stripVTControlCharacters(serverOutput).includes(url) && await fetch(url).then(r => r.ok).catch(() => false)) break;
      if (Date.now() > deadline) throw new Error(`Preview did not start within 20s. Output: ${stripVTControlCharacters(serverOutput).slice(-800)}`);
      await new Promise(resolve => setTimeout(resolve, 150));
    }
  }
  browser = await chromium.launch({ headless: true });
  for (const viewport of sizes) {
    const context = await browser.newContext({ viewport, reducedMotion: 'reduce', locale: 'zh-CN', timezoneId: 'Asia/Shanghai' });
    await context.addInitScript(() => {
      const profile = { name: '慢慢来', type: 'worker', dailyGoal: 120, sessionLimit: 25, restDuration: 120, selectedBackground: 0, onboardingDone: true, targetApps: ['com.ss.android.ugc.aweme', 'tv.danmaku.bili'], allowEmergencyUnlock: true, strongBlockingEnabled: false };
      localStorage.setItem('fb_profile', JSON.stringify(profile));
      localStorage.setItem('fb_monitoring_enabled', 'false');
    });
    const page = await context.newPage();
    page.on('pageerror', error => { pageErrors.push(String(error)); });
    page.on('console', message => {
      if (message.type() === 'error') consoleErrors.push(`${page.url()}: ${message.text()}`);
    });
    page.on('request', request => {
      const reqUrl = request.url();
      if (/^https?:/.test(reqUrl) && new URL(reqUrl).origin !== new URL(url).origin) unexpectedRequests.add(reqUrl);
    });
    page.on('response', response => {
      if (response.status() >= 400) failedResources.add(`${response.status()} ${response.url()}`);
    });
    page.on('requestfailed', request => {
      failedResources.add(`${request.failure()?.errorText ?? 'Request failed'} ${request.url()}`);
    });
    for (const route of routes) {
      const label = `${viewport.width}-${route.slice(1)}`;
      try {
        await page.goto(`${url}${route}`);
        await page.waitForLoadState('networkidle');
        assert.equal(new URL(page.url()).pathname, route, 'Unexpected route redirect');
        await page.getByRole('heading', { name: expectedHeadings[route], exact: true }).waitFor({ state: 'visible', timeout: 10_000 });
        await page.evaluate(() => document.fonts.ready);
        const metrics = await page.evaluate(() => ({ width: innerWidth, scrollWidth: document.documentElement.scrollWidth, headings: [...document.querySelectorAll('h1,h2')].map(n => n.textContent), images: [...document.images].filter(i => !i.complete || i.naturalWidth === 0).map(i => i.src) }));
        assert.ok(metrics.scrollWidth <= metrics.width + 1, `Horizontal overflow ${metrics.scrollWidth} > ${metrics.width}`);
        assert.deepEqual(metrics.images, [], 'Broken image');
        if (route === '/help' || route === '/privacy') {
          const mailLinks = page.locator('a[href^="mailto:"]');
          assert.equal(await mailLinks.count(), 1, 'Expected one support email link');
          assert.equal(await mailLinks.getAttribute('href'), 'mailto:15015091629@163.com', 'Support email must not include query or attached data');
        }
        await page.screenshot({ path: path.join(output, `${label}.png`), fullPage: true });
        if (route === '/rest' && viewport.width === 320) {
          const restRoot = page.getByTestId('rest-activity-view');
          const earlyExit = page.getByRole('button', { name: /提前结束/ });
          await earlyExit.waitFor({ state: 'visible' });
          const before = await restRoot.evaluate(element => ({
            scrollHeight: element.scrollHeight,
            clientHeight: element.clientHeight,
            scrollTop: element.scrollTop,
          }));
          const initiallyClipped = await earlyExit.evaluate(element => {
            const rect = element.getBoundingClientRect();
            return rect.top < 0 || rect.bottom > innerHeight;
          });
          await earlyExit.evaluate(element => element.scrollIntoView({ block: 'end', inline: 'nearest' }));
          const after = await earlyExit.evaluate(element => {
            const root = element.closest('[data-testid="rest-activity-view"]');
            const rect = element.getBoundingClientRect();
            return {
              scrollHeight: root.scrollHeight,
              clientHeight: root.clientHeight,
              scrollTop: root.scrollTop,
              rect: { top: rect.top, bottom: rect.bottom, left: rect.left, right: rect.right },
              viewport: { width: innerWidth, height: innerHeight },
            };
          });
          // Font metrics vary across hosts; a button that already fits needs
          // no overflow. When clipped, its own container must reveal it.
          if (initiallyClipped) {
            assert.ok(before.scrollHeight > before.clientHeight, 'REST view must expose its own scrollable overflow');
            assert.ok(after.scrollTop !== before.scrollTop, 'REST view must scroll to reveal the early-exit button');
          }
          assert.ok(after.rect.top >= 0 && after.rect.bottom <= after.viewport.height
            && after.rect.left >= 0 && after.rect.right <= after.viewport.width,
          `REST early-exit button must be fully reachable after scrolling: ${JSON.stringify(after)}`);
          results.push({ viewport: viewport.width, route: 'rest-early-exit-scroll-accessibility', status: 'PASS', before, after });
        }
        results.push({ viewport: viewport.width, route, status: 'PASS', headings: metrics.headings });
      } catch (error) {
        failures.push(`${label}: ${error.message}`);
        results.push({ viewport: viewport.width, route, status: 'FAIL', error: error.message });
      }
    }
    // Real user interactions in the rendered browser, separate from screenshots.
    try {
      await page.goto(`${url}/onboarding`); await page.waitForLoadState('networkidle');
      for (let slide = 0; slide < 3; slide++) {
        await page.screenshot({ path: path.join(output, `${viewport.width}-onboarding-${slide + 1}.png`), fullPage: true });
        await page.getByRole('button', { name: slide < 2 ? '下一步' : /开始|设置/ }).click();
      }
      await page.waitForURL('**/permissions');
      await page.goto(`${url}/profile`); await page.waitForLoadState('networkidle');
      await page.getByRole('button', { name: /使用指南/ }).click();
      await page.waitForURL('**/help');
      await page.locator('summary').first().click();
      assert.equal(await page.locator('details').first().getAttribute('open'), '');
      await page.getByRole('button', { name: '返回我的' }).click();
      await page.waitForURL('**/profile');
      await page.getByRole('button', { name: '统计', exact: true }).click();
      await page.waitForURL('**/stats');
      await page.getByRole('button', { name: '今日', exact: true }).click();
      assert.equal(await page.getByRole('button', { name: '今日', exact: true }).getAttribute('aria-pressed'), 'true');
      await page.goto(`${url}/login`); await page.waitForURL('**/permissions');
      results.push({ viewport: viewport.width, route: 'navigation-and-controls', status: 'PASS' });
    } catch (error) {
      failures.push(`${viewport.width}-interactions: ${error.message}`);
      results.push({ viewport: viewport.width, route: 'navigation-and-controls', status: 'FAIL', error: error.message });
    }
    if (viewport.width === 393) {
      try {
        // Synthetic records live only in this disposable web context.
        await page.evaluate(() => {
          const key = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
          const records = {};
          [28, 45, 17, 8, 55, 20, 15].forEach((minutes, i) => {
            const day = new Date(); day.setDate(day.getDate() - i);
            records[key(day)] = { date: key(day), totalScreenTime: minutes * 60, videoTime: minutes * 60, restCount: i % 3, interventionCount: i % 2, focusMinutes: 0 };
          });
          localStorage.setItem('fb_stats', JSON.stringify(records));
          localStorage.setItem('fb_activity', JSON.stringify({ [key(new Date())]: [
            { id: 'sample-a', at: new Date().toISOString(), type: 'screen', app: '抖音', durationSec: 900 },
            { id: 'sample-b', at: new Date().toISOString(), type: 'screen', app: 'B站', durationSec: 600 },
            { id: 'sample-c', at: new Date().toISOString(), type: 'screen', app: '小红书', durationSec: 180 },
          ] }));
        });
        for (const route of ['dashboard', 'stats']) {
          await page.goto(`${url}/${route}`); await page.waitForLoadState('networkidle');
          await page.screenshot({ path: path.join(output, `393-${route}-sample.png`), fullPage: true });
        }
        await page.getByRole('button', { name: '今日', exact: true }).click();
        assert.match(await page.getByTestId('statistics-total-time').textContent(), /28分钟/);
        await page.screenshot({ path: path.join(output, '393-stats-today-sample.png'), fullPage: true });
        results.push({ viewport: 393, route: 'synthetic-populated-statistics', status: 'PASS' });
      } catch (error) {
        failures.push(`sample-statistics: ${error.message}`);
        results.push({ viewport: 393, route: 'synthetic-populated-statistics', status: 'FAIL', error: error.message });
      }
      try {
        await page.clock.install();
        await page.goto(`${url}/rest`); await page.waitForLoadState('networkidle');
        await page.getByRole('button', { name: '暂停休息计时' }).click();
        await page.getByRole('button', { name: '继续休息计时' }).click();
        await page.getByRole('button', { name: '退出休息模式' }).click();
        await page.getByRole('group', { name: '提前结束不会解锁' }).waitFor();
        await page.screenshot({ path: path.join(output, '393-rest-exit.png'), fullPage: true });
        await page.getByRole('button', { name: '继续休息', exact: true }).click();
        // Advance this browser's clock only; never shorten an Android rest.
        await page.clock.runFor(121_000);
        await page.getByRole('button', { name: '完成休息', exact: true }).click();
        await page.clock.runFor(2_000);
        await page.getByRole('heading', { name: '休息完成！' }).waitFor();
        await page.screenshot({ path: path.join(output, '393-rest-complete.png'), fullPage: true });
        results.push({ viewport: 393, route: 'web-rest-controls-and-completion', status: 'PASS' });
      } catch (error) {
        failures.push(`web-rest-controls: ${error.message}`);
        results.push({ viewport: 393, route: 'web-rest-controls-and-completion', status: 'FAIL', error: error.message });
      }
    }
    await context.close();
  }
  if (pageErrors.length) failures.push(...pageErrors.map(error => `Unhandled browser error: ${error}`));
  if (consoleErrors.length) failures.push(...consoleErrors.map(error => `Browser console error: ${error}`));
  if (unexpectedRequests.size) failures.push(`Unexpected external requests: ${[...unexpectedRequests].join(', ')}`);
  if (failedResources.size) failures.push(`Failed browser resources: ${[...failedResources].join(', ')}`);
} catch (error) {
  failures.push(`Infrastructure: ${error.message}`);
} finally {
  await browser?.close();
  server?.kill();
  await mkdir(output, { recursive: true });
  await writeFile(path.join(output, 'results.json'), JSON.stringify({ kind: 'WEB_PREVIEW_ONLY', status: failures.length ? 'FAIL' : 'PASS', results, failures, pageErrors, consoleErrors, unexpectedRequests: [...unexpectedRequests], failedResources: [...failedResources] }, null, 2));
}
for (const failure of failures) console.error(failure);
console.log(`UI_SMOKE=${failures.length ? 'FAIL' : 'PASS'} (${results.filter(r => r.status === 'PASS').length} checks, ${failures.length} failures)`);
console.log(`Evidence: ${output}`);
if (failures.length) process.exitCode = 1;
