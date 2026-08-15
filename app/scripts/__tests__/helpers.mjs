import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

export const scriptsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

export async function makeTmpDir(prefix) {
  return await mkdtemp(path.join(tmpdir(), prefix));
}

export function runScript(scriptName, args, envOverrides) {
  const env = {
    ...process.env,
    SOURCE_COMMIT_SHA: 'a'.repeat(40),
    GITHUB_SHA: 'a'.repeat(40),
    GITHUB_REF: 'refs/heads/test',
    GITHUB_RUN_ID: '123',
    GITHUB_RUN_NUMBER: '45',
    GITHUB_WORKFLOW: 'Android',
    VERSION_CODE: '1',
    VERSION_NAME: 'ci-test',
    ...envOverrides,
  };
  const result = spawnSync(process.execPath, [path.join(scriptsDir, scriptName), ...args], {
    env,
    encoding: 'utf8',
  });
  return { status: result.status, stdout: result.stdout ?? '', stderr: result.stderr ?? '' };
}

export async function writeDistWithIndex(distDir, files) {
  await mkdir(path.join(distDir, 'assets'), { recursive: true });
  await writeFile(path.join(distDir, 'index.html'), '<html></html>', 'utf8');
  for (const [rel, data] of Object.entries(files)) {
    const full = path.join(distDir, rel);
    await mkdir(path.dirname(full), { recursive: true });
    await writeFile(full, data, 'utf8');
  }
}

export async function cleanup(dir) {
  await rm(dir, { recursive: true, force: true });
}
