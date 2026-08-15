#!/usr/bin/env node
// Verifies that EVERY file produced by this round's web build (dist/) is present
// in the Capacitor-synced Android web assets with an identical SHA-256.
// Extra files inside the Android assets are allowed (Capacitor runtime files),
// but no dist file may be missing or differ — otherwise CI must FAIL.
//
// Usage: node scripts/verify-web-asset-sync.mjs [distDir] [androidWebAssetsDir]
import { access, readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { sha256File, walkFiles, GIT_SHA_RE } from './provenance-lib.mjs';

function fail(msg) {
  console.error('WEB_ASSET_SYNC=FAIL: ' + msg);
  process.exit(1);
}

function requiredEnv(name) {
  const value = (process.env[name] ?? '').trim();
  if (value === '') fail(name + ' is required but not set or empty');
  return value;
}

const distDir = path.resolve(process.argv[2] ?? 'dist');
const assetsDir = path.resolve(process.argv[3] ?? 'android/app/src/main/assets/public');

const sourceCommitSha = requiredEnv('SOURCE_COMMIT_SHA');
if (!GIT_SHA_RE.test(sourceCommitSha)) fail('SOURCE_COMMIT_SHA is not a valid git sha');
const versionCode = requiredEnv('VERSION_CODE');
const versionName = requiredEnv('VERSION_NAME');

// 1. provenance file must exist on both sides and be byte-identical
const provRel = 'build-provenance.json';
const distProv = path.join(distDir, provRel);
const assetProv = path.join(assetsDir, provRel);
try { await access(distProv); } catch { fail('missing ' + distProv); }
try { await access(assetProv); } catch { fail('missing ' + assetProv + ' — cap sync did not carry the provenance file'); }

const distProvHash = await sha256File(distProv);
const assetProvHash = await sha256File(assetProv);
if (distProvHash !== assetProvHash) {
  fail('build-provenance.json differs between dist and android assets');
}

// 2. provenance contents must match this build's declared identity
const provenance = JSON.parse(await readFile(distProv, 'utf8'));
if (provenance.sourceGitSha !== sourceCommitSha) {
  fail('provenance sourceGitSha ' + provenance.sourceGitSha + ' != SOURCE_COMMIT_SHA ' + sourceCommitSha);
}
if (provenance.versionCode !== versionCode) {
  fail('provenance versionCode ' + provenance.versionCode + ' != VERSION_CODE ' + versionCode);
}
if (provenance.versionName !== versionName) {
  fail('provenance versionName ' + provenance.versionName + ' != VERSION_NAME ' + versionName);
}

// 3. every dist file must exist in android assets with identical SHA-256
const distFiles = await walkFiles(distDir);
const mismatches = [];
let matched = 0;
for (const file of distFiles) {
  const rel = path.relative(distDir, file).split(path.sep).join('/');
  const assetFile = path.join(assetsDir, rel);
  try { await access(assetFile); } catch {
    mismatches.push('MISSING in assets: ' + rel);
    continue;
  }
  const [distHash, assetHash] = await Promise.all([sha256File(file), sha256File(assetFile)]);
  if (distHash !== assetHash) {
    mismatches.push('HASH_MISMATCH: ' + rel);
    continue;
  }
  matched += 1;
}

if (mismatches.length > 0) {
  fail('web asset sync mismatch (' + mismatches.length + ' of ' + distFiles.length + ' files). First mismatches: ' + mismatches.slice(0, 10).join('; '));
}

// informational: count extra files in assets (allowed — Capacitor runtime files)
const assetFiles = await walkFiles(assetsDir);
const distRelSet = new Set(distFiles.map((f) => path.relative(distDir, f).split(path.sep).join('/')));
const extraFiles = assetFiles.filter((f) => {
  const rel = path.relative(assetsDir, f).split(path.sep).join('/');
  return !distRelSet.has(rel);
});

console.log('WEB_ASSET_SYNC=PASS distFileCount=' + distFiles.length + ' matchedFileCount=' + matched + ' extraAssetFiles=' + extraFiles.length);
console.log('PROVENANCE_SOURCE_GIT_SHA=' + provenance.sourceGitSha);
