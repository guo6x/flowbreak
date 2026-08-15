#!/usr/bin/env node
// Generates <dist>/build-provenance.json from an allowlisted set of CI metadata.
// The file is generated AFTER 'npm run build' and BEFORE 'npx cap sync android',
// so it travels into the Android web assets together with the bundle it describes.
//
// Allowlisted inputs (env only — never dumped):
//   SOURCE_COMMIT_SHA  the commit actually checked out/built (PR: head sha, push: pushed sha)
//   GITHUB_SHA         the workflow event sha (PR: merge sha, push: = SOURCE_COMMIT_SHA)
//   GITHUB_REF         the ref that triggered the run
//   GITHUB_RUN_ID      run id
//   GITHUB_RUN_NUMBER  run number
//   GITHUB_WORKFLOW    workflow name
//   PR_HEAD_SHA        optional; PR head sha when the event is pull_request
//   VERSION_CODE       numeric version code used for this build
//   VERSION_NAME       version name used for this build
//
// Usage: node scripts/generate-build-provenance.mjs [distDir]
import { writeFile, access } from 'node:fs/promises';
import path from 'node:path';
import { isValidGitSha } from './provenance-lib.mjs';

function fail(msg) {
  console.error('PROVENANCE_GENERATE=FAIL: ' + msg);
  process.exit(1);
}

function requiredEnv(name) {
  const value = (process.env[name] ?? '').trim();
  if (value === '') fail(name + ' is required but not set or empty');
  return value;
}

const distDir = path.resolve(process.argv[2] ?? 'dist');

// The dist directory must already contain the real bundle (proof that npm run build ran).
try {
  await access(path.join(distDir, 'index.html'));
} catch {
  fail('dist/index.html not found — run npm run build before generating provenance');
}

const sourceGitSha = requiredEnv('SOURCE_COMMIT_SHA');
if (!isValidGitSha(sourceGitSha)) {
  fail('SOURCE_COMMIT_SHA is not a valid 40-char git sha: ' + sourceGitSha);
}
const workflowSha = requiredEnv('GITHUB_SHA');
if (!isValidGitSha(workflowSha)) {
  fail('GITHUB_SHA is not a valid 40-char git sha: ' + workflowSha);
}
const versionCode = requiredEnv('VERSION_CODE');
if (!/^[0-9]+$/.test(versionCode)) {
  fail('VERSION_CODE must be a positive integer: ' + versionCode);
}
const versionName = requiredEnv('VERSION_NAME');

// buildUtc is frozen INTO the file before packaging; the packaged copy is
// compared byte-for-byte afterwards, never regenerated.
const provenance = {
  schemaVersion: 1,
  sourceGitSha,
  workflowSha,
  gitRef: (process.env.GITHUB_REF ?? '').trim(),
  runId: (process.env.GITHUB_RUN_ID ?? '').trim(),
  runNumber: (process.env.GITHUB_RUN_NUMBER ?? '').trim(),
  workflow: (process.env.GITHUB_WORKFLOW ?? 'Android').trim() || 'Android',
  buildUtc: new Date().toISOString(),
  versionCode,
  versionName,
};

const prHeadSha = (process.env.PR_HEAD_SHA ?? '').trim();
if (prHeadSha !== '') {
  if (!isValidGitSha(prHeadSha)) {
    fail('PR_HEAD_SHA is not a valid 40-char git sha: ' + prHeadSha);
  }
  provenance.prHeadSha = prHeadSha;
}

const outFile = path.join(distDir, 'build-provenance.json');
await writeFile(outFile, JSON.stringify(provenance, null, 2) + '\n', 'utf8');

console.log(
  'PROVENANCE_GENERATED=PASS sourceGitSha=' + sourceGitSha +
  ' workflowSha=' + workflowSha +
  ' versionCode=' + versionCode +
  ' versionName=' + versionName +
  (provenance.prHeadSha ? ' prHeadSha=' + provenance.prHeadSha : '')
);
