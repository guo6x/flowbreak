#!/usr/bin/env node
/**
 * Prevent reintroduction of externally fetched bootstrap resources and obvious
 * hardcoded outbound clients in the first-party source.
 *
 * STATIC ONLY: not a certificate of absence of network traffic in APK/AAB,
 * transitively linked native SDKs, Android/OS components, or user shares.
 */
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { dirname, extname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const APP_ROOT = resolve(HERE, '..');

// Check whole source / complete tags: newline-wrapped attributes are ordinary HTML.
const probes = [
  { id: 'external-html-asset', kind: 'html-tag',
    pattern: /<(?:script|link|iframe)\b[\s\S]*?>/gi,
    attribute: /\b(?:src|href)\s*=\s*["']?(?:https?:\/\/|\/\/)/i },
  { id: 'external-css-import', kind: 'html-css',
    pattern: /@import\s+(?:url\()?[\s"'\\]*https?:\/\//gi },
  { id: 'external-css-url', kind: 'html-css',
    pattern: /url\s*\(\s*["']?https?:\/\//gi },
  { id: 'browser-network-client', kind: 'script',
    pattern: /\b(?:fetch|WebSocket|EventSource|XMLHttpRequest|sendBeacon)\s*\(/g },
  { id: 'browser-http-library', kind: 'script',
    pattern: /\baxios\s*\.\s*(?:get|post|put|patch|delete|request)\s*\(/g },
  { id: 'external-dynamic-resource', kind: 'script',
    pattern: /\.(?:src|href)\s*=\s*["']https?:\/\//g },
  { id: 'remote-dynamic-import', kind: 'script',
    pattern: /\bimport\s*\(\s*["']https?:\/\//g },
  { id: 'native-network-client', kind: 'java',
    pattern: /\b(?:HttpURLConnection|OkHttpClient|Socket|openConnection)\b/g },
  { id: 'native-remote-url', kind: 'java',
    pattern: /new\s+URL\s*\(\s*["']https?:\/\//g },
];

const allowedExt = new Set(['.ts', '.tsx', '.js', '.jsx', '.css', '.html', '.java']);
function visit(dir, files) {
  if (!existsSync(dir)) return;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === '__tests__' || entry.name === 'test' || entry.name === 'androidTest' || entry.name === 'testDomestic' || entry.name === 'testPlay') continue;
    const path = join(dir, entry.name);
    if (entry.isDirectory()) visit(path, files);
    else if (entry.isFile() && allowedExt.has(extname(path))) files.push(path);
  }
}

export function auditNetworkSurface(appRoot = APP_ROOT) {
  const files = [join(appRoot, 'index.html')];
  visit(join(appRoot, 'src'), files);
  visit(join(appRoot, 'android/app/src/main/java'), files);
  visit(join(appRoot, 'android/app/src/domestic/java'), files);
  const violations = [];
  for (const file of files) {
    if (!existsSync(file)) continue;
    const source = readFileSync(file, 'utf8');
    const ext = extname(file);
    for (const probe of probes) {
      const compatible = probe.kind === 'html-tag' ? ext === '.html'
        : probe.kind === 'html-css' ? (ext === '.html' || ext === '.css')
        : probe.kind === 'java' ? ext === '.java'
        : (ext === '.ts' || ext === '.tsx' || ext === '.js' || ext === '.jsx');
      if (!compatible) continue;
      for (const match of source.matchAll(probe.pattern)) {
        if (probe.attribute && !probe.attribute.test(match[0])) continue;
        const line = source.slice(0, match.index).split(/\r?\n/).length;
        violations.push({
          source: relative(appRoot, file).replaceAll('\\', '/'),
          line,
          type: probe.id,
        });
      }
    }
  }
  const manifest = join(appRoot, 'android/app/src/main/AndroidManifest.xml');
  const androidInternetPermission = existsSync(manifest) &&
    readFileSync(manifest, 'utf8').includes('android.permission.INTERNET');
  return {
    scope: 'FIRST_PARTY_SOURCE_STATIC_ONLY',
    inference: 'NOT_RUNTIME_NETWORK_PROOF',
    filesInspected: files.filter(existsSync).length,
    androidInternetPermission,
    violations,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = auditNetworkSurface();
  process.stdout.write(JSON.stringify(result, null, 2) + '\n');
  if (result.violations.length) process.exitCode = 1;
}
