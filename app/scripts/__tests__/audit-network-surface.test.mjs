import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { auditNetworkSurface } from '../audit-network-surface.mjs';

function scenario(indexHtml, appSource = '') {
  const root = mkdtempSync(join(tmpdir(), 'flowbreak-network-static-'));
  mkdirSync(join(root, 'src'), { recursive: true });
  mkdirSync(join(root, 'android/app/src/main'), { recursive: true });
  writeFileSync(join(root, 'index.html'), indexHtml);
  writeFileSync(join(root, 'src', 'main.ts'), appSource);
  writeFileSync(join(root, 'android/app/src/main/AndroidManifest.xml'),
    '<manifest><uses-permission android:name="android.permission.INTERNET"/></manifest>');
  return {
    result: auditNetworkSurface(root),
    cleanup: () => rmSync(root, { recursive: true, force: true }),
  };
}

test('accepts device-local page while reporting Android INTERNET permission as an unproven external risk', () => {
  const { result, cleanup } = scenario('<script type="module" src="/src/main.ts"></script>');
  try {
    assert.deepEqual(result.violations, []);
    assert.equal(result.androidInternetPermission, true);
    assert.equal(result.inference, 'NOT_RUNTIME_NETWORK_PROOF');
  } finally { cleanup(); }
});

test('rejects third-party Google Fonts in the app bootstrap', () => {
  const { result, cleanup } = scenario('<link href="https://fonts.googleapis.com/css?family=Roboto" rel="stylesheet">');
  try {
    assert.deepEqual(result.violations.map(x => x.type), ['external-html-asset']);
    assert.equal(result.violations[0].source, 'index.html');
  } finally { cleanup(); }
});

test('rejects hardcoded outgoing JavaScript network calls', () => {
  const { result, cleanup } = scenario('<html></html>', 'fetch("https://example.com/telemetry")');
  try {
    assert.ok(result.violations.some(x => x.type === 'browser-network-client'));
  } finally { cleanup(); }
});

test('rejects external stylesheet imports', () => {
  const { result, cleanup } = scenario('<html></html>', '@import url("https://fonts.example/style.css")');
  try {
    assert.ok(result.violations.some(x => x.type === 'external-css-import'));
  } finally { cleanup(); }
});


test('rejects newline-wrapped external bootstrap links', () => {
  const { result, cleanup } = scenario('<link\n rel="stylesheet"\n href="https://fonts.googleapis.com/css?family=Roboto"\n>');
  try {
    assert.equal(result.violations[0]?.type, 'external-html-asset');
  } finally { cleanup(); }
});

test('rejects indirect XMLHttpRequest and dynamic remote script source', () => {
  const { result, cleanup } = scenario('<html></html>', [
    'const xhr = new XMLHttpRequest();',
    'xhr.open("GET", "/endpoint");',
    "const script = document.createElement('script');",
    'script.src = "https://cdn.example.com/tracker.js";',
  ].join('\n'));
  try {
    assert.ok(result.violations.some(x => x.type === 'browser-network-client'));
    assert.ok(result.violations.some(x => x.type === 'external-dynamic-resource'));
  } finally { cleanup(); }
});

test('allows local new URL for workers or blobs without network', () => {
  const { result, cleanup } = scenario('<html></html>',
    "const workerUrl = new URL('./worker.js', import.meta.url);");
  try {
    assert.deepEqual(result.violations, []);
  } finally { cleanup(); }
});
