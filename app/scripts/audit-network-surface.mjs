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

const probes = [
  {
    id: 'external-html-asset',
    pattern: /<(?:script|link)\b[^>]*\b(?:src|href)\s*=\s*["']https?:\/\//i,
  },
  {
    id: 'external-css-import',
    pattern: /@import\s+(?:url\()?\s*["']?https?:\/\//i,
  },
  {
    id: 'external-css-url',
    pattern: /url\s*\(\s*["']?https?:\/\//i,
  },
  {
    id: 'literal-js-network-client',
    pattern: /(?:\bfetch|\bWebSocket|\bEventSource)\s*\(\s*["`]https?:\/\//i,
  },
  {
    id: 'literal-native-network-client',
    pattern: /(?:new\s+(?:URL|HttpURLConnection|OkHttpClient)|openConnection\s*\(|HttpClient\.newHttpClient\s*\()/,
  },
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
    source.split(/\r?\n/).forEach((line, lineNumber) => {
      for (const probe of probes) {
        if (probe.pattern.test(line)) violations.push({
          source: relative(appRoot, file).replaceAll('\\', '/'),
          line: lineNumber + 1,
          type: probe.id,
        });
      }
    });
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
