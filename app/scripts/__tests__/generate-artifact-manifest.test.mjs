import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, writeFile, readFile } from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { makeTmpDir, runScript, cleanup } from './helpers.mjs';

const sha256 = (data) => createHash('sha256').update(data).digest('hex');

async function buildFixture() {
  const root = await makeTmpDir('manifest-');
  const out = path.join(root, 'out');
  const binDir = path.join(root, 'bins');
  await mkdir(binDir, { recursive: true });
  const aabData = Buffer.from('AAB-BINARY-DATA');
  const apkData = Buffer.from('APK-BINARY-DATA');
  const mapPlay = Buffer.from('map play');
  const mapDom = Buffer.from('map dom');
  await writeFile(path.join(binDir, 'app-play-release.aab'), aabData);
  await writeFile(path.join(binDir, 'app-domestic-release-unsigned.apk'), apkData);
  await writeFile(path.join(binDir, 'mapping-playRelease.txt'), mapPlay);
  await writeFile(path.join(binDir, 'mapping-domesticRelease.txt'), mapDom);
  const prov = {
    schemaVersion: 1,
    sourceGitSha: 'f'.repeat(40),
    workflowSha: 'g'.repeat(40),
    gitRef: 'refs/heads/x',
    runId: '7',
    runNumber: '8',
    workflow: 'Android',
    buildUtc: '2026-08-15T00:00:00.000Z',
    versionCode: '1',
    versionName: 'ci-x',
  };
  await writeFile(path.join(root, 'build-provenance.json'), JSON.stringify(prov), 'utf8');
  return { root, out, binDir, aabData, apkData, mapPlay, mapDom, prov };
}

test('manifest and SHA256SUMS contain correct hashes and sizes', async () => {
  const { root, out, binDir, aabData, apkData, mapPlay, mapDom, prov } = await buildFixture();
  const binaries = JSON.stringify([
    { channel: 'play', type: 'aab', path: path.join(binDir, 'app-play-release.aab'), filename: 'app-play-release.aab' },
    { channel: 'domestic', type: 'apk', path: path.join(binDir, 'app-domestic-release-unsigned.apk'), filename: 'app-domestic-release-unsigned.apk' },
  ]);
  const extra = JSON.stringify([
    { channel: 'play', type: 'mapping', path: path.join(binDir, 'mapping-playRelease.txt'), filename: 'mapping-playRelease.txt' },
    { channel: 'domestic', type: 'mapping', path: path.join(binDir, 'mapping-domesticRelease.txt'), filename: 'mapping-domesticRelease.txt' },
  ]);
  const r = runScript('generate-artifact-manifest.mjs',
    ['--out', out, '--provenance', path.join(root, 'build-provenance.json'), '--binaries', binaries, '--extra-sums', extra], {});
  assert.equal(r.status, 0, r.stderr);
  assert.match(r.stdout, /ARTIFACT_MANIFEST=PASS/);

  const manifest = JSON.parse(await readFile(path.join(out, 'artifact-manifest.json'), 'utf8'));
  assert.equal(manifest.sourceGitSha, prov.sourceGitSha);
  assert.equal(manifest.workflowSha, prov.workflowSha);
  assert.equal(manifest.versionCode, '1');
  assert.equal(manifest.versionName, 'ci-x');
  assert.equal(manifest.artifacts.length, 2);
  const aab = manifest.artifacts.find((a) => a.type === 'aab');
  const apk = manifest.artifacts.find((a) => a.type === 'apk');
  assert.equal(aab.filename, 'app-play-release.aab');
  assert.equal(aab.size, aabData.length);
  assert.equal(aab.sha256, sha256(aabData));
  assert.equal(apk.filename, 'app-domestic-release-unsigned.apk');
  assert.equal(apk.size, apkData.length);
  assert.equal(apk.sha256, sha256(apkData));

  const sums = (await readFile(path.join(out, 'SHA256SUMS.txt'), 'utf8')).trim().split('\n');
  assert.equal(sums.length, 5);
  const expected = {
    'app-play-release.aab': sha256(aabData),
    'app-domestic-release-unsigned.apk': sha256(apkData),
    'mapping-playRelease.txt': sha256(mapPlay),
    'mapping-domesticRelease.txt': sha256(mapDom),
    'artifact-manifest.json': sha256(await readFile(path.join(out, 'artifact-manifest.json'))),
  };
  for (const line of sums) {
    const [hash, name] = line.split('  ');
    assert.ok(name in expected, 'unexpected sums entry: ' + name);
    assert.equal(hash, expected[name]);
  }
  // manifest must not contain its own hash (no recursion)
  assert.ok(!JSON.stringify(manifest).includes(aab.sha256 + '  artifact-manifest'));
  await cleanup(root);
});

test('fails on path-traversal filename', async () => {
  const { root, out, binDir } = await buildFixture();
  const binaries = JSON.stringify([
    { channel: 'play', type: 'aab', path: path.join(binDir, 'app-play-release.aab'), filename: '../evil.aab' },
  ]);
  const r = runScript('generate-artifact-manifest.mjs',
    ['--out', out, '--provenance', path.join(root, 'build-provenance.json'), '--binaries', binaries, '--extra-sums', '[]'], {});
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /plain basename/);
  await cleanup(root);
});

test('accepts --binaries-file / --extra-sums-file (BOM tolerant)', async () => {
  const { root, out, binDir } = await buildFixture();
  const { writeFile } = await import('node:fs/promises');
  const binsFile = path.join(root, 'bins.json');
  const extrasFile = path.join(root, 'extras.json');
  const bins = [{ channel: 'play', type: 'aab', path: path.join(binDir, 'app-play-release.aab'), filename: 'app-play-release.aab' }];
  const extras = [{ channel: 'play', type: 'mapping', path: path.join(binDir, 'mapping-playRelease.txt'), filename: 'mapping-playRelease.txt' }];
  // write with a BOM to simulate PowerShell 5 UTF8 output
  await writeFile(binsFile, '\uFEFF' + JSON.stringify(bins), 'utf8');
  await writeFile(extrasFile, JSON.stringify(extras), 'utf8');
  const r = runScript('generate-artifact-manifest.mjs',
    ['--out', out, '--provenance', path.join(root, 'build-provenance.json'), '--binaries-file', binsFile, '--extra-sums-file', extrasFile], {});
  assert.equal(r.status, 0, r.stderr);
  const manifest = JSON.parse(await readFile(path.join(out, 'artifact-manifest.json'), 'utf8'));
  assert.equal(manifest.artifacts.length, 1);
  assert.equal(manifest.artifacts[0].filename, 'app-play-release.aab');
  await cleanup(root);
});

test('fails on missing artifact file', async () => {
  const { root, out } = await buildFixture();
  const binaries = JSON.stringify([
    { channel: 'play', type: 'aab', path: path.join(root, 'nope.aab'), filename: 'nope.aab' },
  ]);
  const r = runScript('generate-artifact-manifest.mjs',
    ['--out', out, '--provenance', path.join(root, 'build-provenance.json'), '--binaries', binaries, '--extra-sums', '[]'], {});
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /artifact file not found/);
  await cleanup(root);
});
