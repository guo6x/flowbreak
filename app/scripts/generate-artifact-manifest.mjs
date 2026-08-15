#!/usr/bin/env node
// Builds artifact-manifest.json + SHA256SUMS.txt for the release candidates.
// manifest.artifacts[] holds the release binaries (channel/type/filename/size/sha256);
// SHA256SUMS.txt additionally covers mapping files and the manifest itself.
// The manifest does NOT contain its own hash (no self-recursion).
//
// Usage: node scripts/generate-artifact-manifest.mjs
//          --out <dir> --provenance <build-provenance.json>
//          --binaries '<json: [{channel,type,path,filename}...]>'
//          --extra-sums '<json: [{channel,type,path,filename}...]>'
import { mkdir, readFile, writeFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { sha256File, isValidArtifactFilename } from './provenance-lib.mjs';

function fail(msg) {
  console.error('ARTIFACT_MANIFEST=FAIL: ' + msg);
  process.exit(1);
}

function parseArgs(argv) {
  const out = { binaries: [], extraSums: [] };
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i];
    if (key === '--out') { out.out = argv[++i]; }
    else if (key === '--provenance') { out.provenance = argv[++i]; }
    else if (key === '--binaries') { out.binaries = JSON.parse(argv[++i]); }
    else if (key === '--extra-sums') { out.extraSums = JSON.parse(argv[++i]); }
    else if (key === '--binaries-file') { out.binariesFile = argv[++i]; }
    else if (key === '--extra-sums-file') { out.extraSumsFile = argv[++i]; }
    else fail('unknown argument: ' + key);
  }
  if (!out.out || !out.provenance) fail('--out and --provenance are required');
  return out;
}

function stripBom(text) {
  return text.replace(/^\uFEFF/, '');
}

async function loadArgLists(args) {
  if (args.binariesFile) {
    try {
      args.binaries = JSON.parse(stripBom(await readFile(args.binariesFile, 'utf8')));
    } catch {
      fail('cannot read binaries file: ' + args.binariesFile);
    }
  }
  if (args.extraSumsFile) {
    try {
      args.extraSums = JSON.parse(stripBom(await readFile(args.extraSumsFile, 'utf8')));
    } catch {
      fail('cannot read extra sums file: ' + args.extraSumsFile);
    }
  }
  if (!Array.isArray(args.binaries) || args.binaries.length === 0) fail('--binaries must be a non-empty array');
  if (!Array.isArray(args.extraSums)) fail('--extra-sums must be an array');
  return args;
}

async function entryToRecord(entry) {
  const { channel, type, path: filePath, filename } = entry;
  if (!channel || !type) fail('each artifact entry needs channel and type');
  if (!isValidArtifactFilename(filename)) fail('filename must be a plain basename: ' + filename);
  let size;
  try { size = (await stat(filePath)).size; } catch { fail('artifact file not found: ' + filePath); }
  const sha256 = await sha256File(filePath);
  return { channel, type, filename, size, sha256 };
}

const args = await loadArgLists(parseArgs(process.argv.slice(2)));
const outDir = path.resolve(args.out);
await mkdir(outDir, { recursive: true });

let provenance;
try {
  provenance = JSON.parse(await readFile(args.provenance, 'utf8'));
} catch {
  fail('cannot read provenance file: ' + args.provenance);
}
for (const key of ['schemaVersion', 'sourceGitSha', 'versionCode', 'versionName']) {
  if (!provenance[key]) fail('provenance missing field ' + key);
}

const manifest = {
  schemaVersion: 1,
  sourceGitSha: provenance.sourceGitSha,
  workflowSha: provenance.workflowSha ?? '',
  gitRef: provenance.gitRef ?? '',
  runId: provenance.runId ?? '',
  runNumber: provenance.runNumber ?? '',
  workflow: provenance.workflow ?? 'Android',
  buildUtc: provenance.buildUtc ?? '',
  versionCode: provenance.versionCode,
  versionName: provenance.versionName,
  artifacts: [],
};
if (provenance.prHeadSha) manifest.prHeadSha = provenance.prHeadSha;

for (const entry of args.binaries) {
  manifest.artifacts.push(await entryToRecord(entry));
}

const manifestPath = path.join(outDir, 'artifact-manifest.json');
await writeFile(manifestPath, JSON.stringify(manifest, null, 2) + '\n', 'utf8');

// SHA256SUMS.txt: binaries + extra sums (mappings) + the manifest itself.
const sumsLines = [];
for (const artifact of manifest.artifacts) {
  sumsLines.push(artifact.sha256 + '  ' + artifact.filename);
}
for (const entry of args.extraSums) {
  const record = await entryToRecord(entry);
  sumsLines.push(record.sha256 + '  ' + record.filename);
}
const manifestHash = await sha256File(manifestPath);
sumsLines.push(manifestHash + '  artifact-manifest.json');
await writeFile(path.join(outDir, 'SHA256SUMS.txt'), sumsLines.join('\n') + '\n', 'utf8');

console.log('ARTIFACT_MANIFEST=PASS sourceGitSha=' + manifest.sourceGitSha + ' artifacts=' + manifest.artifacts.length);
for (const a of manifest.artifacts) {
  console.log('  ' + a.filename + ' size=' + a.size + ' sha256=' + a.sha256);
}
console.log('  artifact-manifest.json sha256=' + manifestHash);
