import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { makeTmpDir, runScript, writeDistWithIndex, cleanup } from './helpers.mjs';

test('generates allowlisted provenance with valid metadata', async () => {
  const dist = await makeTmpDir('prov-dist-');
  await writeDistWithIndex(dist, {});
  const r = runScript('generate-build-provenance.mjs', [dist], {
    SOURCE_COMMIT_SHA: 'b'.repeat(40),
    GITHUB_SHA: 'c'.repeat(40),
    PR_HEAD_SHA: 'b'.repeat(40),
    SECRET_FAKE_TOKEN: 'should-never-leak',
  });
  assert.equal(r.status, 0, r.stderr);
  assert.match(r.stdout, /PROVENANCE_GENERATED=PASS/);
  const prov = JSON.parse(await readFile(path.join(dist, 'build-provenance.json'), 'utf8'));
  assert.equal(prov.schemaVersion, 1);
  assert.equal(prov.sourceGitSha, 'b'.repeat(40));
  assert.equal(prov.workflowSha, 'c'.repeat(40));
  assert.equal(prov.prHeadSha, 'b'.repeat(40));
  assert.equal(prov.versionCode, '1');
  assert.equal(prov.versionName, 'ci-test');
  assert.equal(prov.runId, '123');
  assert.equal(prov.runNumber, '45');
  // allowlist only: no extra fields, no env dump, no secrets
  const allowed = new Set(['schemaVersion','sourceGitSha','workflowSha','gitRef','runId','runNumber','workflow','buildUtc','versionCode','versionName','prHeadSha']);
  for (const key of Object.keys(prov)) assert.ok(allowed.has(key), 'unexpected field: ' + key);
  assert.ok(!JSON.stringify(prov).includes('should-never-leak'));
  await cleanup(dist);
});

test('omits prHeadSha when not a PR', async () => {
  const dist = await makeTmpDir('prov-dist-');
  await writeDistWithIndex(dist, {});
  const r = runScript('generate-build-provenance.mjs', [dist], { PR_HEAD_SHA: '' });
  assert.equal(r.status, 0, r.stderr);
  const prov = JSON.parse(await readFile(path.join(dist, 'build-provenance.json'), 'utf8'));
  assert.ok(!('prHeadSha' in prov));
  await cleanup(dist);
});

test('fails when SOURCE_COMMIT_SHA is missing', async () => {
  const dist = await makeTmpDir('prov-dist-');
  await writeDistWithIndex(dist, {});
  const r = runScript('generate-build-provenance.mjs', [dist], { SOURCE_COMMIT_SHA: '' });
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /PROVENANCE_GENERATE=FAIL/);
  await cleanup(dist);
});

test('fails on invalid git sha', async () => {
  const dist = await makeTmpDir('prov-dist-');
  await writeDistWithIndex(dist, {});
  const r = runScript('generate-build-provenance.mjs', [dist], { SOURCE_COMMIT_SHA: 'not-a-sha' });
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /not a valid 40-char git sha/);
  await cleanup(dist);
});

test('fails on invalid VERSION_CODE', async () => {
  const dist = await makeTmpDir('prov-dist-');
  await writeDistWithIndex(dist, {});
  const r = runScript('generate-build-provenance.mjs', [dist], { VERSION_CODE: '1.2' });
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /VERSION_CODE must be a positive integer/);
  await cleanup(dist);
});

test('fails when dist has no index.html (build not run)', async () => {
  const dist = await makeTmpDir('prov-dist-');
  const r = runScript('generate-build-provenance.mjs', [dist], {});
  assert.notEqual(r.status, 0);
  assert.match(r.stderr, /index\.html not found/);
  await cleanup(dist);
});
