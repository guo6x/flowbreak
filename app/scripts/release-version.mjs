#!/usr/bin/env node
// Deterministic release version policy for FlowBreak.
//
// versionName Source of Truth: app/package.json "version" (stable SemVer X.Y.Z).
// versionCode formula: MAJOR * 1_000_000 + MINOR * 1_000 + PATCH
//   (deterministic, monotonic, independent of CI run numbers).
// Release tag must be exactly "v<versionName>" (e.g. v1.1.0).
//
// Modes:
//   node scripts/release-version.mjs              compute + print values
//   node scripts/release-version.mjs --tag v1.1.0 verify tag against package
//
// Stdout carries only KEY=VALUE lines (safe to append to $GITHUB_ENV);
// human-readable summary goes to stderr.
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const MAX_VERSION_CODE = 2100000000; // Android max int for versionCode
const MAX_MINOR = 999;
const MAX_PATCH = 999;

function fail(msg) {
  console.error('RELEASE_VERSION=FAIL: ' + msg);
  process.exit(1);
}

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i];
    if (key === '--tag') { out.tag = argv[++i]; }
    else if (key === '--package') { out.packagePath = argv[++i]; }
    else fail('unknown argument: ' + key);
  }
  return out;
}

// Strict stable SemVer: X.Y.Z, non-negative integer segments,
// no leading zeros (except the segment "0"), no prerelease/build metadata.
const SEMVER_RE = /^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/;

function parseSemVer(version) {
  const m = SEMVER_RE.exec(version);
  if (!m) {
    fail('package.json version is not a stable SemVer (X.Y.Z): ' + version);
  }
  return { major: Number(m[1]), minor: Number(m[2]), patch: Number(m[3]) };
}

function computeVersionCode(parts) {
  if (parts.minor > MAX_MINOR) {
    fail('MINOR must be < 1000 for versionCode encoding: ' + parts.minor);
  }
  if (parts.patch > MAX_PATCH) {
    fail('PATCH must be < 1000 for versionCode encoding: ' + parts.patch);
  }
  const code = parts.major * 1000000 + parts.minor * 1000 + parts.patch;
  if (code > MAX_VERSION_CODE || !Number.isSafeInteger(code)) {
    fail('versionCode overflow (computed ' + code + ' > ' + MAX_VERSION_CODE + '): ' + JSON.stringify(parts));
  }
  return code;
}

const args = parseArgs(process.argv.slice(2));
const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const pkgPath = path.resolve(args.packagePath ?? path.join(scriptDir, '..', 'package.json'));

let pkg;
try {
  pkg = JSON.parse(await readFile(pkgPath, 'utf8'));
} catch {
  fail('cannot read package.json: ' + pkgPath);
}
const versionName = typeof pkg.version === 'string' ? pkg.version.trim() : '';
if (versionName === '') fail('package.json has no version field');
const parts = parseSemVer(versionName);
const versionCode = computeVersionCode(parts);

if (args.tag !== undefined) {
  if (typeof args.tag !== 'string' || args.tag === '') {
    fail('release tag is empty');
  }
  const expectedTag = 'v' + versionName;
  if (args.tag !== expectedTag) {
    fail('tag "' + args.tag + '" does not match package version "' + versionName + '" (expected tag: ' + expectedTag + ')');
  }
  console.error('RELEASE_VERSION=PASS tag=' + args.tag + ' versionName=' + versionName + ' versionCode=' + versionCode);
} else {
  console.error('RELEASE_VERSION=COMPUTED versionName=' + versionName + ' versionCode=' + versionCode);
}

// KEY=VALUE only on stdout — safe for $GITHUB_ENV
process.stdout.write('VERSION_NAME=' + versionName + '\n');
process.stdout.write('VERSION_CODE=' + versionCode + '\n');
