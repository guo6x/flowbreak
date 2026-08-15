import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, writeFile, readFile, cp } from 'node:fs/promises';
import path from 'node:path';
import { makeTmpDir, runScript, writeDistWithIndex, cleanup } from './helpers.mjs';

const SHA = 'd'.repeat(40);

async function buildProvisionedPair() {
  const root = await makeTmpDir('sync-');
  const dist = path.join(root, 'dist');
  const assets = path.join(root, 'assets');
  await writeDistWithIndex(dist, {
    'assets/app.js': 'console.log(1);',
    'assets/style.css': 'body{}',
  });
  // generate provenance into dist via the real generator
  const gen = runScript('generate-build-provenance.mjs', [dist], { SOURCE_COMMIT_SHA: SHA });
  assert.equal(gen.status, 0, gen.stderr);
  await mkdir(assets, { recursive: true });
  await cp(dist, assets, { recursive: true });
  return { root, dist, assets };
}

test('PASS when every dist file matches in android assets', async () => {
  const { root, dist, assets } = await buildProvisionedPair();
  const r = runScript('verify-web-asset-sync.mjs', [dist, assets], { SOURCE_COMMIT_SHA: SHA });
  assert.equal(r.status, 0, r.stderr);
  assert.match(r.stdout, /WEB_ASSET_SYNC=PASS/);
  assert.match(r.stdout, /distFileCount=4/);
  assert.match(r.stdout, /matchedFileCount=4/);
  await cleanup(root);
});

test('FAIL when an android asset file was tampered', async () => {
  const { root, dist, assets } = await buildProvisionedPair();
  await writeFile(path.join(assets, 'assets', 'app.js'), 'console.log(OLD);', 'utf8');
  const r = runScript('verify-web-asset-sync.mjs', [dist, assets], { SOURCE_COMMIT_SHA: SHA });
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /WEB_ASSET_SYNC=FAIL/);
  assert.match(r.stderr, /HASH_MISMATCH/);
  await cleanup(root);
});

test('FAIL when a dist file is missing from android assets', async () => {
  const { root, dist, assets } = await buildProvisionedPair();
  const { rm } = await import('node:fs/promises');
  await rm(path.join(assets, 'assets', 'style.css'));
  const r = runScript('verify-web-asset-sync.mjs', [dist, assets], { SOURCE_COMMIT_SHA: SHA });
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /MISSING in assets/);
  await cleanup(root);
});

test('FAIL when provenance sourceGitSha differs from SOURCE_COMMIT_SHA', async () => {
  const { root, dist, assets } = await buildProvisionedPair();
  const r = runScript('verify-web-asset-sync.mjs', [dist, assets], { SOURCE_COMMIT_SHA: 'e'.repeat(40) });
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /sourceGitSha/);
  await cleanup(root);
});

test('allows extra Capacitor runtime files in android assets', async () => {
  const { root, dist, assets } = await buildProvisionedPair();
  await writeFile(path.join(assets, 'native-bridge.js'), 'capacitor-runtime', 'utf8');
  const r = runScript('verify-web-asset-sync.mjs', [dist, assets], { SOURCE_COMMIT_SHA: SHA });
  assert.equal(r.status, 0, r.stderr);
  assert.match(r.stdout, /extraAssetFiles=1/);
  await cleanup(root);
});
