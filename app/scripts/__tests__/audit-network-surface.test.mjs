import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { auditNetworkSurface } from '../audit-network-surface.mjs';

function makeApp({ files = {}, capacitorConfig = "export default { appId: 'com.test.app', webDir: 'dist' };", manifests = {} } = {}) {
  const root = mkdtempSync(join(tmpdir(), 'flowbreak-network-static-'));
  const defaults = {
    'index.html': '<script type="module" src="/src/main.ts"></script>',
    'android/app/src/main/AndroidManifest.xml': '<manifest><uses-permission android:name="android.permission.INTERNET"/></manifest>',
  };
  for (const [relativePath, contents] of Object.entries({ ...defaults, ...files, ...manifests })) {
    const path = join(root, relativePath);
    mkdirSync(join(path, '..'), { recursive: true });
    writeFileSync(path, contents, 'utf8');
  }
  if (capacitorConfig !== null) {
    const path = join(root, capacitorConfig.name ?? 'capacitor.config.ts');
    mkdirSync(join(path, '..'), { recursive: true });
    writeFileSync(path, capacitorConfig.contents ?? capacitorConfig, 'utf8');
  }
  return { root, cleanup: () => rmSync(root, { recursive: true, force: true }) };
}

function types(result) {
  return result.violations.map((violation) => violation.type);
}

test('accepts a bundled local page and reports Internet permission and static-only limits', () => {
  const app = makeApp();
  try {
    const result = auditNetworkSurface(app.root);
    assert.deepEqual(result.violations, []);
    assert.equal(result.scope, 'FIRST_PARTY_SOURCE_STATIC_ONLY');
    assert.equal(result.inference, 'NOT_RUNTIME_NETWORK_PROOF');
    assert.equal(result.androidInternetPermission, true);
    assert.deepEqual(result.androidInternetPermissionSources, ['android/app/src/main/AndroidManifest.xml']);
    assert.ok(result.scannedRoots.includes('public/'));
    assert.ok(result.scannedRoots.includes('android/app/src/play/java/'));
    assert.ok(result.limitations.some((item) => item.includes('NOT_RUNTIME_NETWORK_PROOF')));
    assert.ok(result.limitations.some((item) => item.includes('generated Android assets')));
  } finally {
    app.cleanup();
  }
});

test('excludes generated Android web bundles while scanning first-party native sources', () => {
  const generatedBundle = 'android/app/src/main/assets/public/assets/index-generated.js';
  const nativeSource = 'android/app/src/main/java/com/test/FirstParty.java';
  const app = makeApp({ files: {
    [generatedBundle]: 'fetch("https://modulepreload.example/chunk.js");',
    [nativeSource]: 'new URL("https://api.example.net/data");',
  } });
  try {
    const result = auditNetworkSurface(app.root);
    assert.deepEqual(result.violations.map((item) => item.source), [nativeSource]);
    assert.ok(result.scannedRoots.includes('android/app/src/main/java/'));
    assert.ok(!result.scannedRoots.some((root) => root.includes('/assets/')));
  } finally {
    app.cleanup();
  }
});

test('rejects remote fonts plus external img/source resources in multiline HTML tags', () => {
  const app = makeApp({ files: {
    'index.html': [
      '<link\n rel="stylesheet"\n href="https://fonts.googleapis.com/css?family=Roboto"\n>',
      '<img alt="remote"\n src=//cdn.example.net/hero.png>',
      '<video controls><source\n src="https://cdn.example.net/intro.mp4"\n type="video/mp4"></video>',
      '<script src=https://cdn.example.net/bootstrap.js></script>',
      '<link rel="preload" href="//cdn.example.net/font.woff2">',
    ].join('\n'),
  } });
  try {
    const result = auditNetworkSurface(app.root);
    assert.equal(result.violations.filter((item) => item.type === 'external-html-asset').length, 5);
    assert.ok(result.violations.every((item) => item.source === 'index.html'));
  } finally {
    app.cleanup();
  }
});

test('scans public assets and JSX/TSX markup, including absolute srcset candidates', () => {
  const app = makeApp({ files: {
    'public/theme.css': '@import url("//cdn.example.net/theme.css");',
    'public/remote.js': 'navigator.sendBeacon("/telemetry");',
    'src/components/RemoteImage.tsx': [
      'export function RemoteImage() {',
      '  return <img alt="remote" src="https://cdn.example.net/photo.png" srcSet="/small.png 1x, https://cdn.example.net/large.png 2x" />;',
      '}',
    ].join('\n'),
  } });
  try {
    const result = auditNetworkSurface(app.root);
    assert.ok(result.violations.some((item) => item.source === 'public/theme.css' && item.type === 'external-css-import'));
    assert.ok(result.violations.some((item) => item.source === 'public/remote.js' && item.type === 'browser-network-client'));
    assert.ok(result.violations.some((item) => item.source === 'src/components/RemoteImage.tsx' && item.type === 'external-html-asset'));
  } finally {
    app.cleanup();
  }
});

test('rejects remote CSS imports and background URLs, including scheme-relative URLs', () => {
  const app = makeApp({ files: {
    'src/App.css': [
      '@import "https://fonts.example/style.css";',
      '.hero { background-image: url(//cdn.example.net/hero.png); }',
    ].join('\n'),
  } });
  try {
    const result = auditNetworkSurface(app.root);
    assert.ok(result.violations.some((item) => item.type === 'external-css-import'));
    assert.ok(result.violations.some((item) => item.type === 'external-css-url'));
  } finally {
    app.cleanup();
  }
});

test('rejects browser clients and remote dynamic resources while allowing local worker URLs', () => {
  const app = makeApp({ files: {
    'src/main.ts': [
      'fetch("/api/local");',
      'const xhr = new XMLHttpRequest();',
      'xhr.open("GET", "/local");',
      'const script = document.createElement("script");',
      'script.src = `https://cdn.example.net/client.js`;',
      'script . href = "//cdn.example.net/client.css";',
      'import("//cdn.example.net/module.js");',
      "const workerUrl = new URL('./worker.js', import.meta.url);",
    ].join('\n'),
  } });
  try {
    const result = auditNetworkSurface(app.root);
    assert.ok(result.violations.some((item) => item.type === 'browser-network-client'));
    assert.ok(result.violations.some((item) => item.type === 'external-dynamic-resource'));
    assert.ok(result.violations.some((item) => item.type === 'remote-dynamic-import'));
    assert.ok(!result.violations.some((item) => item.line === 8));
  } finally {
    app.cleanup();
  }
});

test('rejects uppercase remote URL schemes in script and native literals', () => {
  const app = makeApp({ files: {
    'src/remote.ts': [
      'const script = document.createElement("script");',
      'script.src = "HTTPS://cdn.example.net/remote.js";',
      'void import("HTTPS://cdn.example.net/module.js");',
    ].join('\n'),
    'android/app/src/main/java/com/test/Remote.java': [
      'new URL("HTTPS://api.example.net/data");',
      'webView.loadUrl("HTTPS://example.net/page");',
    ].join('\n'),
  } });
  try {
    const result = auditNetworkSurface(app.root);
    assert.equal(
      result.violations.filter((item) => item.source === 'src/remote.ts').length,
      2,
      JSON.stringify(result.violations.filter((item) => item.source === 'src/remote.ts')),
    );
    assert.ok(result.violations.some((item) => item.type === 'external-dynamic-resource'));
    assert.ok(result.violations.some((item) => item.type === 'remote-dynamic-import'));
    assert.equal(result.violations.filter((item) => item.source.endsWith('/Remote.java')).length, 2);
    assert.ok(result.violations.some((item) => item.type === 'native-remote-url'));
    assert.ok(result.violations.some((item) => item.type === 'native-remote-webview'));
  } finally {
    app.cleanup();
  }
});

test('inspects Java and Kotlin across main, Domestic, and Play production source sets', () => {
  const app = makeApp({ files: {
    'android/app/src/main/java/com/test/MainNetwork.java': 'new OkHttpClient();',
    'android/app/src/domestic/kotlin/com/test/DomesticNetwork.kt': 'val socket = Socket("host", 443)',
    'android/app/src/play/java/com/test/PlayNetwork.java': 'new URL("https://cdn.example.net");',
    'android/app/src/play/kotlin/com/test/PlayWeb.kt': 'webView.loadUrl("//cdn.example.net/app");',
    'android/app/src/test/kotlin/com/test/ShouldBeIgnored.kt': 'fetch("https://test.example");',
  } });
  try {
    const result = auditNetworkSurface(app.root);
    const native = result.violations.filter((item) => item.source.includes('android/app/src/'));
    assert.ok(native.some((item) => item.source.startsWith('android/app/src/main/') && item.type === 'native-network-client'));
    assert.ok(native.some((item) => item.source.startsWith('android/app/src/domestic/') && item.type === 'native-network-client'));
    assert.ok(native.some((item) => item.source.startsWith('android/app/src/play/java/') && item.type === 'native-remote-url'));
    assert.ok(native.some((item) => item.source.startsWith('android/app/src/play/kotlin/') && item.type === 'native-remote-webview'));
    assert.ok(!native.some((item) => item.source.includes('/src/test/')));
  } finally {
    app.cleanup();
  }
});

test('reports Internet permission from any production channel manifest', () => {
  const app = makeApp({
    files: { 'android/app/src/main/AndroidManifest.xml': '<manifest />' },
    manifests: { 'android/app/src/domestic/AndroidManifest.xml': '<manifest><uses-permission android:name="android.permission.INTERNET"/></manifest>' },
  });
  try {
    const result = auditNetworkSurface(app.root);
    assert.equal(result.androidInternetPermission, true);
    assert.deepEqual(result.androidInternetPermissionSources, ['android/app/src/domestic/AndroidManifest.xml']);
  } finally {
    app.cleanup();
  }
});

test('rejects direct, computed, aliased, shorthand, and nested Capacitor server URLs', () => {
  const cases = [
    "export default { server: { url: process.env.REMOTE_ORIGIN } };",
    "export default { server: { nested: { enabled: true }, 'url': 'https://remote.example' } };",
    "export default { server: { ...remoteConfig } };",
    "export default { server: remoteConfig };",
    "export default { server: { url } };",
    "const server = { url: process.env.REMOTE_ORIGIN }; export default { server };",
    "const server = makeServer(); export default { server };",
    "export default { ['server']: { url: 'https://remote.example' } };",
    "export default { server: { [dynamicKey]: 'x' } };",
  ];
  for (const capacitorConfig of cases) {
    const app = makeApp({ capacitorConfig });
    try {
      const result = auditNetworkSurface(app.root);
      assert.ok(types(result).includes('capacitor-remote-server'), capacitorConfig);
    } finally {
      app.cleanup();
    }
  }
});

test('rejects root config spreads that could introduce server.url, including imported bases', () => {
  const cases = [
    "import remoteConfig from './remote-config'; export default { ...remoteConfig, webDir: 'dist' };",
    "export default withLocalConfig({ ...remoteConfig });",
    "const base = loadBaseConfig(); export default { ...base };",
  ];
  for (const capacitorConfig of cases) {
    const app = makeApp({ capacitorConfig });
    try {
      const result = auditNetworkSurface(app.root);
      assert.ok(types(result).includes('capacitor-remote-server'), capacitorConfig);
    } finally {
      app.cleanup();
    }
  }
});

test('rejects unknown config factories that can inject a remote server URL', () => {
  const app = makeApp({ capacitorConfig: [
    'function wrap(config) {',
    "  return { ...config, server: { url: 'https://remote.example' } };",
    '}',
    "export default wrap({ appId: 'com.test.app', webDir: 'dist' });",
  ].join('\n') });
  try {
    const result = auditNetworkSurface(app.root);
    assert.ok(types(result).includes('capacitor-remote-server'));
  } finally {
    app.cleanup();
  }
});

test('accepts the repository CapacitorConfig type plus statically inspectable config binding', () => {
  const app = makeApp({ capacitorConfig: [
    "import { CapacitorConfig } from '@capacitor/cli';",
    'const config: CapacitorConfig = {',
    "  appId: 'com.test.app',",
    "  appName: 'FlowBreak',",
    "  webDir: 'dist',",
    '  bundledWebRuntime: false,',
    '};',
    'export default config;',
  ].join('\n') });
  try {
    const result = auditNetworkSurface(app.root);
    assert.deepEqual(result.violations, []);
  } finally {
    app.cleanup();
  }
});

test('allows statically inspectable local Capacitor server options and local root spreads', () => {
  const app = makeApp({ capacitorConfig: [
    "const localServer = { cleartext: false, androidScheme: 'https' };",
    "const shared = { appId: 'com.test.app', webDir: 'dist' };",
    'export default { ...shared, server: localServer };',
  ].join('\n') });
  try {
    const result = auditNetworkSurface(app.root);
    assert.deepEqual(result.violations, []);
  } finally {
    app.cleanup();
  }
});

test('rejects remote server URL in JSON Capacitor config', () => {
  const app = makeApp({ capacitorConfig: {
    name: 'capacitor.config.json',
    contents: JSON.stringify({ appId: 'com.test.app', server: { url: 'https://remote.example' } }),
  } });
  try {
    const result = auditNetworkSurface(app.root);
    assert.ok(types(result).includes('capacitor-remote-server'));
  } finally {
    app.cleanup();
  }
});
