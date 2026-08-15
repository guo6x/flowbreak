// Shared helpers for the release-artifact provenance toolchain.
// Node built-ins only — no external dependencies.
import { createHash } from 'node:crypto';
import { readFile, readdir } from 'node:fs/promises';
import path from 'node:path';

export const GIT_SHA_RE = /^[0-9a-f]{40}$/i;

export function isValidGitSha(value) {
  return typeof value === 'string' && GIT_SHA_RE.test(value);
}

export async function sha256File(filePath) {
  const data = await readFile(filePath);
  return createHash('sha256').update(data).digest('hex');
}

/** Recursively list all files under dir, sorted by full path. */
export async function walkFiles(dir) {
  const out = [];
  const stack = [dir];
  while (stack.length > 0) {
    const current = stack.pop();
    const entries = await readdir(current, { withFileTypes: true });
    for (const entry of entries) {
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(full);
      } else if (entry.isFile()) {
        out.push(full);
      }
    }
  }
  return out.sort();
}

/** A filename usable inside an artifact: basename only, no separators. */
export function isValidArtifactFilename(name) {
  const sep = String.fromCharCode(92);
  return (
    typeof name === 'string' &&
    name.length > 0 &&
    !name.includes('/') &&
    !name.includes(sep) &&
    name !== '.' &&
    name !== '..'
  );
}
