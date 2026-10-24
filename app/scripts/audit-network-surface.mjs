#!/usr/bin/env node
/**
 * Heuristically guard first-party source against common remote bootstrap
 * resources, outbound clients, and non-bundled Capacitor WebView servers.
 *
 * STATIC SOURCE CHECK ONLY. A clean result is not proof of zero network
 * traffic, and does not review transitive SDKs, build-time injection, merged
 * release manifests, packaged APK/AAB contents, Android system traffic, or
 * user-directed sharing. Dynamic/obfuscated code and unrecognized network
 * APIs can evade these source patterns.
 */
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, extname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const HERE = dirname(fileURLToPath(import.meta.url));
const APP_ROOT = resolve(HERE, '..');
const SKIP_DIRS = new Set(['__tests__', 'test', 'tests', 'androidTest', 'testDomestic', 'testPlay']);
const SOURCE_EXTENSIONS = new Set([
  '.html', '.css', '.svg', '.js', '.jsx', '.mjs', '.cjs', '.ts', '.tsx', '.java', '.kt',
]);
const ANDROID_SOURCE_SETS = ['main', 'domestic', 'play'];
const ANDROID_SOURCE_ROOTS = ANDROID_SOURCE_SETS.flatMap((sourceSet) =>
  ['java', 'kotlin'].map((language) => `android/app/src/${sourceSet}/${language}`));
const SCRIPT_EXTENSIONS = new Set(['.js', '.jsx', '.mjs', '.cjs', '.ts', '.tsx']);
const HTML_LIKE_EXTENSIONS = new Set(['.html', '.jsx', '.tsx', '.svg']);
const CSS_LIKE_EXTENSIONS = new Set(['.css', '.html', '.jsx', '.tsx', '.svg']);
const NATIVE_EXTENSIONS = new Set(['.java', '.kt']);
const CAPACITOR_CONFIG_PATTERN = /^capacitor\.config\.(?:ts|js|mjs|cjs|json)$/i;

const LIMITATIONS = [
  'Static first-party source inspection only; a clean result is NOT_RUNTIME_NETWORK_PROOF.',
  'Authored web inputs are scanned from public/ and src/; generated Android assets under android/app/src/*/assets/ are excluded.',
  'Does not inspect transitive npm/Gradle SDK behavior, build-time injection, merged release manifests, or final APK/AAB contents.',
  'Does not capture Android/OS traffic or evaluate user-directed exports and shares.',
  'Regex probes are heuristic: dynamically assembled/obfuscated URLs and unrecognized network APIs may be missed; comments/examples may cause false positives.',
];

const probes = [
  {
    id: 'external-html-asset', kind: 'html-resource',
    pattern: /<(?:script|link|iframe|img|source|video|audio|track|embed|object|image|use|feImage)\b[\s\S]*?>/gi,
    attribute: /\b(?:src|href|poster|data)\s*=\s*["']?(?:https?:\/\/|\/\/)/i,
    srcset: /\b(?:srcset|imagesrcset)\s*=\s*(?:"[^"]*(?:https?:\/\/|\/\/)[^"]*"|'[^']*(?:https?:\/\/|\/\/)[^']*'|[^\s>]*(?:https?:\/\/|\/\/))/i,
  },
  {
    id: 'external-css-import', kind: 'css-resource',
    pattern: /@import\s+(?:url\()?\s*["'\\]*(?:https?:\/\/|\/\/)/gi,
  },
  {
    id: 'external-css-url', kind: 'css-resource',
    pattern: /url\s*\(\s*["']?(?:https?:\/\/|\/\/)/gi,
  },
  {
    id: 'browser-network-client', kind: 'script',
    pattern: /\b(?:fetch|WebSocket|EventSource|XMLHttpRequest|sendBeacon)\s*\(/g,
  },
  {
    id: 'browser-http-library', kind: 'script',
    pattern: /\baxios\s*\.\s*(?:get|post|put|patch|delete|request)\s*\(/g,
  },
  {
    id: 'external-dynamic-resource', kind: 'script',
    pattern: /\.\s*(?:src|href)\s*=\s*["'`]?(?:(?:[Hh][Tt][Tt][Pp][Ss]?:\/\/)|\/\/)/g,
  },
  {
    id: 'remote-dynamic-import', kind: 'script',
    pattern: /\bimport\s*\(\s*["'`](?:(?:[Hh][Tt][Tt][Pp][Ss]?:\/\/)|\/\/)/g,
  },
  {
    id: 'native-network-client', kind: 'native',
    pattern: /\b(?:HttpURLConnection|OkHttpClient|Socket|openConnection)\b/g,
  },
  {
    id: 'native-remote-url', kind: 'native',
    pattern: /(?:new\s+)?(?:java\.net\.)?(?:URL|URI)\s*\(\s*["'`](?:(?:[Hh][Tt][Tt][Pp][Ss]?:\/\/)|\/\/)/g,
  },
  {
    id: 'native-remote-webview', kind: 'native',
    pattern: /\b(?:loadUrl|loadDataWithBaseURL)\s*\(\s*["'`](?:(?:[Hh][Tt][Tt][Pp][Ss]?:\/\/)|\/\/)/g,
  },
];

function toRepoPath(appRoot, file) {
  return relative(appRoot, file).replaceAll('\\', '/');
}

function addDirectoryFiles(dir, files, excludeTestChildren = false, allowedExtensions = SOURCE_EXTENSIONS) {
  if (!existsSync(dir)) return;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    // Exclude conventional test roots only at app/src itself. A package or
    // feature folder named "test" under a production Android source set is
    // still production source and must not disappear from the audit.
    if (excludeTestChildren && entry.isDirectory() && SKIP_DIRS.has(entry.name)) continue;
    const path = join(dir, entry.name);
    if (entry.isDirectory()) addDirectoryFiles(path, files);
    else if (entry.isFile() && allowedExtensions.has(extname(path).toLowerCase())) files.add(path);
  }
}

function listCapacitorConfigs(appRoot) {
  return readdirSync(appRoot, { withFileTypes: true })
    .filter((entry) => entry.isFile() && CAPACITOR_CONFIG_PATTERN.test(entry.name))
    .map((entry) => join(appRoot, entry.name));
}

function collectSourceFiles(appRoot) {
  const files = new Set();
  const entryHtml = join(appRoot, 'index.html');
  if (existsSync(entryHtml)) files.add(entryHtml);
  for (const config of listCapacitorConfigs(appRoot)) files.add(config);

  addDirectoryFiles(join(appRoot, 'public'), files);
  addDirectoryFiles(join(appRoot, 'src'), files, true);

  // Scan authored Java/Kotlin production sources for every shipped channel.
  // Capacitor-generated assets under each source set's assets/ directory are
  // deliberately excluded; their authored inputs are covered by public/ and src/.
  for (const sourceRoot of ANDROID_SOURCE_ROOTS) {
    addDirectoryFiles(join(appRoot, sourceRoot), files, false, NATIVE_EXTENSIONS);
  }
  return [...files].sort((a, b) => toRepoPath(appRoot, a).localeCompare(toRepoPath(appRoot, b)));
}

function lineAt(source, offset) {
  return source.slice(0, offset).split(/\r?\n/).length;
}

function scanSourceFile(appRoot, file, violations) {
  const source = readFileSync(file, 'utf8');
  const ext = extname(file).toLowerCase();
  const sourcePath = toRepoPath(appRoot, file);

  for (const probe of probes) {
    const compatible = probe.kind === 'html-resource'
      ? HTML_LIKE_EXTENSIONS.has(ext)
      : probe.kind === 'css-resource'
        ? CSS_LIKE_EXTENSIONS.has(ext)
        : probe.kind === 'native'
          ? NATIVE_EXTENSIONS.has(ext)
          : SCRIPT_EXTENSIONS.has(ext);
    if (!compatible) continue;

    probe.pattern.lastIndex = 0;
    for (const match of source.matchAll(probe.pattern)) {
      if (probe.attribute && !probe.attribute.test(match[0]) && !probe.srcset?.test(match[0])) continue;
      violations.push({ source: sourcePath, line: lineAt(source, match.index), type: probe.id });
    }
  }
}

function unwrapExpression(node) {
  let current = node;
  while (current && (
    ts.isParenthesizedExpression(current)
    || ts.isAsExpression(current)
    || ts.isTypeAssertionExpression(current)
    || ts.isNonNullExpression(current)
    || ts.isSatisfiesExpression(current)
  )) current = current.expression;
  return current;
}

function staticPropertyName(name) {
  if (!name) return undefined;
  if (ts.isIdentifier(name) || ts.isStringLiteral(name) || ts.isNoSubstitutionTemplateLiteral(name)) {
    return name.text;
  }
  if (ts.isComputedPropertyName(name)) {
    const expression = unwrapExpression(name.expression);
    if (ts.isStringLiteral(expression) || ts.isNoSubstitutionTemplateLiteral(expression)) return expression.text;
  }
  return undefined;
}

function isDynamicComputedName(name) {
  return ts.isComputedPropertyName(name) && staticPropertyName(name) === undefined;
}

function collectVariableInitializers(sourceFile) {
  const declarations = new Map();
  const visit = (node) => {
    if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.initializer) {
      declarations.set(node.name.text, node.initializer);
    }
    ts.forEachChild(node, visit);
  };
  visit(sourceFile);
  return declarations;
}

function findExportedConfigExpressions(sourceFile) {
  const expressions = [];
  const visit = (node) => {
    if (ts.isExportAssignment(node)) expressions.push(node.expression);
    if (ts.isBinaryExpression(node) && node.operatorToken.kind === ts.SyntaxKind.EqualsToken) {
      const left = node.left;
      const moduleExports = ts.isPropertyAccessExpression(left)
        && ts.isIdentifier(left.expression) && left.expression.text === 'module' && left.name.text === 'exports';
      const exportsDefault = ts.isPropertyAccessExpression(left)
        && ts.isIdentifier(left.expression) && left.expression.text === 'exports' && left.name.text === 'default';
      if (moduleExports || exportsDefault) expressions.push(node.right);
    }
    ts.forEachChild(node, visit);
  };
  visit(sourceFile);
  return expressions;
}

function auditCapacitorSource(appRoot, configPath, violations) {
  const source = readFileSync(configPath, 'utf8');
  const sourcePath = toRepoPath(appRoot, configPath);
  const record = (node, type = 'capacitor-remote-server') => {
    const offset = node?.getStart?.() ?? 0;
    violations.push({ source: sourcePath, line: lineAt(source, offset), type });
  };

  if (extname(configPath).toLowerCase() === '.json') {
    let config;
    try {
      config = JSON.parse(source);
    } catch {
      record(undefined, 'capacitor-config-unparseable');
      return;
    }
    if (Object.hasOwn(config ?? {}, 'server')) {
      const server = config.server;
      if (!server || typeof server !== 'object' || Array.isArray(server)) {
        record(undefined);
      } else if (Object.hasOwn(server, 'url') || Object.keys(server).some((key) => key.startsWith('...'))) {
        record(undefined);
      }
    }
    return;
  }

  const scriptKind = extname(configPath).toLowerCase() === '.ts' ? ts.ScriptKind.TS : ts.ScriptKind.JS;
  const ast = ts.createSourceFile(configPath, source, ts.ScriptTarget.Latest, true, scriptKind);
  const initializers = collectVariableInitializers(ast);
  const uncertainType = 'capacitor-remote-server';

  const inspectServerValue = (rawValue, node, seen = new Set()) => {
    const value = unwrapExpression(rawValue);
    if (ts.isObjectLiteralExpression(value)) {
      for (const member of value.properties) {
        if (ts.isSpreadAssignment(member)) {
          record(member, uncertainType);
        } else if (ts.isPropertyAssignment(member)) {
          if (staticPropertyName(member.name) === 'url' || isDynamicComputedName(member.name)) {
            record(member, uncertainType);
          }
        } else if (ts.isShorthandPropertyAssignment(member)) {
          if (member.name.text === 'url') record(member, uncertainType);
        } else if (
          ts.isMethodDeclaration(member)
          || ts.isGetAccessorDeclaration(member)
          || ts.isSetAccessorDeclaration(member)
        ) {
          record(member, uncertainType);
        }
      }
      return;
    }
    if (ts.isIdentifier(value) && initializers.has(value.text) && !seen.has(value.text)) {
      const nextSeen = new Set(seen);
      nextSeen.add(value.text);
      inspectServerValue(initializers.get(value.text), node, nextSeen);
      return;
    }
    // A non-literal or unresolved server value cannot establish that no URL is
    // configured (e.g. an imported server object or a function result).
    record(node, uncertainType);
  };

  const inspectRootObject = (rawExpression, seen = new Set()) => {
    const expression = unwrapExpression(rawExpression);
    if (ts.isObjectLiteralExpression(expression)) {
      for (const member of expression.properties) {
        if (ts.isSpreadAssignment(member)) {
          const spreadValue = unwrapExpression(member.expression);
          if (ts.isObjectLiteralExpression(spreadValue)) {
            inspectRootObject(spreadValue, seen);
          } else if (ts.isIdentifier(spreadValue) && initializers.has(spreadValue.text) && !seen.has(spreadValue.text)) {
            const nextSeen = new Set(seen);
            nextSeen.add(spreadValue.text);
            inspectRootObject(initializers.get(spreadValue.text), nextSeen);
          } else {
            // Unknown root spreads could replace/introduce `server`.
            record(member, uncertainType);
          }
        } else if (ts.isPropertyAssignment(member)) {
          const name = staticPropertyName(member.name);
          if (name === 'server' || isDynamicComputedName(member.name)) inspectServerValue(member.initializer, member);
        } else if (ts.isShorthandPropertyAssignment(member)) {
          if (member.name.text === 'server') inspectServerValue(member.name, member);
        } else if (
          ts.isMethodDeclaration(member)
          || ts.isGetAccessorDeclaration(member)
          || ts.isSetAccessorDeclaration(member)
        ) {
          if (staticPropertyName(member.name) === 'server' || isDynamicComputedName(member.name)) record(member, uncertainType);
        }
      }
      return true;
    }
    if (ts.isIdentifier(expression) && initializers.has(expression.text) && !seen.has(expression.text)) {
      const nextSeen = new Set(seen);
      nextSeen.add(expression.text);
      return inspectRootObject(initializers.get(expression.text), nextSeen);
    }
    if (ts.isCallExpression(expression)) {
      // A readable argument does not prove that an unknown factory returns it
      // unchanged: it may inject (or replace) `server.url` in the result.
      // Capacitor's project config is represented as a literal or a statically
      // resolvable local binding above; arbitrary calls stay fail-closed.
      record(expression, uncertainType);
      return false;
    }
    // An unrecognized exported expression (conditional/function/import/etc.)
    // is deliberately fail-closed for the local-only WebView assumption.
    record(expression, uncertainType);
    return false;
  };

  const exportedConfigs = findExportedConfigExpressions(ast);
  if (exportedConfigs.length === 0) {
    record(undefined, 'capacitor-config-export-unrecognized');
    return;
  }
  for (const expression of exportedConfigs) inspectRootObject(expression);
}

function auditCapacitorJson(appRoot, configPath, violations) {
  // Retained as a named wrapper to make the JSON-vs-source boundary explicit.
  auditCapacitorSource(appRoot, configPath, violations);
}

function inspectAndroidInternetPermissions(appRoot) {
  const manifests = ANDROID_SOURCE_SETS.map((sourceSet) =>
    join(appRoot, 'android', 'app', 'src', sourceSet, 'AndroidManifest.xml'));
  const sources = manifests
    .filter(existsSync)
    .filter((manifest) => readFileSync(manifest, 'utf8').includes('android.permission.INTERNET'))
    .map((manifest) => toRepoPath(appRoot, manifest));
  return { present: sources.length > 0, sources };
}

export function auditNetworkSurface(appRoot = APP_ROOT) {
  const files = collectSourceFiles(appRoot);
  const violations = [];
  for (const file of files) scanSourceFile(appRoot, file, violations);

  for (const configPath of listCapacitorConfigs(appRoot)) {
    if (extname(configPath).toLowerCase() === '.json') auditCapacitorJson(appRoot, configPath, violations);
    else auditCapacitorSource(appRoot, configPath, violations);
  }

  violations.sort((a, b) => a.source.localeCompare(b.source) || a.line - b.line || a.type.localeCompare(b.type));
  const androidInternet = inspectAndroidInternetPermissions(appRoot);
  return {
    scope: 'FIRST_PARTY_SOURCE_STATIC_ONLY',
    inference: 'NOT_RUNTIME_NETWORK_PROOF',
    scannedRoots: [
      'index.html', 'capacitor.config.*', 'public/', 'src/',
      ...ANDROID_SOURCE_ROOTS.map((sourceRoot) => `${sourceRoot}/`),
    ],
    filesInspected: files.length,
    androidInternetPermission: androidInternet.present,
    androidInternetPermissionSources: androidInternet.sources,
    limitations: LIMITATIONS,
    violations,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = auditNetworkSurface();
  process.stdout.write(JSON.stringify(result, null, 2) + '\n');
  if (result.violations.length) process.exitCode = 1;
}
