import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { sha256File, isValidGitSha, isValidArtifactFilename } from '../provenance-lib.mjs';

test('sha256File matches a known vector', async () => {
  const dir = await mkdtemp(path.join(tmpdir(), 'lib-'));
  const file = path.join(dir, 'v.txt');
  await writeFile(file, 'abc', 'utf8');
  assert.equal(await sha256File(file), 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
  await rm(dir, { recursive: true, force: true });
});

test('isValidGitSha accepts exactly 40 hex chars', () => {
  assert.ok(isValidGitSha('a'.repeat(40)));
  assert.ok(isValidGitSha('A'.repeat(40)));
  assert.ok(!isValidGitSha('a'.repeat(39)));
  assert.ok(!isValidGitSha('g'.repeat(40)));
  assert.ok(!isValidGitSha(''));
  assert.ok(!isValidGitSha(null));
});

test('isValidArtifactFilename rejects separators and traversal', () => {
  assert.ok(isValidArtifactFilename('app-play-release.aab'));
  assert.ok(isValidArtifactFilename('mapping-playRelease.txt'));
  assert.ok(!isValidArtifactFilename('../evil.aab'));
  assert.ok(!isValidArtifactFilename('sub/app.aab'));
  assert.ok(!isValidArtifactFilename('app\\..\\x.aab'));
  assert.ok(!isValidArtifactFilename('..'));
  assert.ok(!isValidArtifactFilename(''));
});
