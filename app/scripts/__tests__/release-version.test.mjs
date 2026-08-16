import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const scriptsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const script = path.join(scriptsDir, 'release-version.mjs');

async function makePkg(version) {
  const dir = await mkdtemp(path.join(tmpdir(), 'relver-'));
  await mkdir(dir, { recursive: true });
  await writeFile(path.join(dir, 'package.json'), JSON.stringify({ version }, null, 2) + '\n', 'utf8');
  return dir;
}

function run(args) {
  const r = spawnSync(process.execPath, [script, ...args], { encoding: 'utf8' });
  return { status: r.status, stdout: r.stdout ?? '', stderr: r.stderr ?? '' };
}

function envLines(stdout) {
  const out = {};
  for (const line of stdout.split('\n')) {
    const m = /^([A-Z_]+)=(.+)$/.exec(line.trim());
    if (m) out[m[1]] = m[2];
  }
  return out;
}

test('1.1.0 computes versionCode 1001000', async () => {
  const dir = await makePkg('1.1.0');
  const r = run(['--package', path.join(dir, 'package.json')]);
  assert.equal(r.status, 0, r.stderr);
  const env = envLines(r.stdout);
  assert.equal(env.VERSION_NAME, '1.1.0');
  assert.equal(env.VERSION_CODE, '1001000');
  assert.match(r.stderr, /RELEASE_VERSION=COMPUTED/);
  await rm(dir, { recursive: true, force: true });
});

test('tag v1.1.0 matches package 1.1.0 -> PASS', async () => {
  const dir = await makePkg('1.1.0');
  const r = run(['--package', path.join(dir, 'package.json'), '--tag', 'v1.1.0']);
  assert.equal(r.status, 0, r.stderr);
  const env = envLines(r.stdout);
  assert.equal(env.VERSION_NAME, '1.1.0');
  assert.equal(env.VERSION_CODE, '1001000');
  assert.match(r.stderr, /RELEASE_VERSION=PASS tag=v1\.1\.0/);
  await rm(dir, { recursive: true, force: true });
});

test('tag v1.1.1 against package 1.1.0 -> FAIL', async () => {
  const dir = await makePkg('1.1.0');
  const r = run(['--package', path.join(dir, 'package.json'), '--tag', 'v1.1.1']);
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /does not match package version/);
  await rm(dir, { recursive: true, force: true });
});

test('invalid tags fail: vfoo, v1, v1.1, v1.1.0-test', async () => {
  const dir = await makePkg('1.1.0');
  for (const tag of ['vfoo', 'v1', 'v1.1', 'v1.1.0-test', '1.1.0', 'v1.1.0+build1']) {
    const r = run(['--package', path.join(dir, 'package.json'), '--tag', tag]);
    assert.notEqual(r.status, 0, 'tag should fail: ' + tag);
    assert.match(r.stderr, /RELEASE_VERSION=FAIL/);
  }
  await rm(dir, { recursive: true, force: true });
});

test('versionCode overflow fails (MAJOR too large)', async () => {
  const dir = await makePkg('3000.0.0');
  const r = run(['--package', path.join(dir, 'package.json')]);
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /overflow/);
  await rm(dir, { recursive: true, force: true });
});

test('MINOR >= 1000 fails versionCode encoding', async () => {
  const dir = await makePkg('1.1000.0');
  const r = run(['--package', path.join(dir, 'package.json')]);
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /MINOR must be < 1000/);
  await rm(dir, { recursive: true, force: true });
});

test('non-integer / prerelease / leading-zero segments fail', async () => {
  for (const bad of ['1.1.x', '1.1', '1.1.0-beta', '01.1.0', '1.01.0', '1.1.00']) {
    const dir = await makePkg(bad);
    const r = run(['--package', path.join(dir, 'package.json')]);
    assert.notEqual(r.status, 0, 'version should fail: ' + bad);
    assert.match(r.stderr, /not a stable SemVer/);
    await rm(dir, { recursive: true, force: true });
  }
});

test('patch-level version encodes monotonically', async () => {
  const a = await makePkg('1.1.9');
  const b = await makePkg('1.1.10');
  const ra = run(['--package', path.join(a, 'package.json')]);
  const rb = run(['--package', path.join(b, 'package.json')]);
  assert.equal(ra.status, 0);
  assert.equal(rb.status, 0);
  const ca = Number(envLines(ra.stdout).VERSION_CODE);
  const cb = Number(envLines(rb.stdout).VERSION_CODE);
  assert.ok(cb > ca, 'versionCode must be monotonic');
  assert.equal(cb - ca, 1);
  await rm(a, { recursive: true, force: true });
  await rm(b, { recursive: true, force: true });
});
