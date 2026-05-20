#!/usr/bin/env bun
// Captain-blessed helper for SpriteCook POST /v1/api/assets/import (gma-7gl.12).
//
// Resolves the API key from env → macOS Keychain → 1Password (in that priority
// order) and never echoes it. Idempotent: short-circuits when the file's sha12
// is already in tools/sprite-pipeline/spritecook-assets.json.
//
// Usage:
//   bun run tools/sprite-pipeline/import-asset.ts --file <path> [--label <name>]
// stdout (one JSON line):
//   {"asset_id":"<uuid>","sha12":"<12-hex>","label":"<name|null>","cached":<bool>}

import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { basename, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
export const MANIFEST_PATH = join(HERE, 'spritecook-assets.json');
const API_BASE = process.env.SPRITECOOK_API_BASE || 'https://api.spritecook.ai';
const IMPORT_PATH = '/v1/api/assets/import';
export const KEYCHAIN_SERVICE = 'gma-spritecook';
export const OP_REFERENCE = 'op://gma/spritecook/api-key';

export interface ManifestEntry {
  asset_id: string;
  sha12: string;
  label: string;
}
export interface Manifest {
  $schema?: string;
  assets: ManifestEntry[];
}

export interface KeySources {
  env?: () => string | undefined;
  keychain?: () => string | undefined;
  op?: () => string | undefined;
}

function usageAndExit(): never {
  console.error('usage: import-asset.ts --file <path> [--label <name>]');
  process.exit(2);
}

function parseArgs(): { file: string; label: string | null } {
  const args = process.argv.slice(2);
  let file: string | null = null;
  let label: string | null = null;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--file') file = args[++i] ?? null;
    else if (args[i] === '--label') label = args[++i] ?? null;
    else { console.error(`unknown arg: ${args[i]}`); usageAndExit(); }
  }
  if (!file) usageAndExit();
  return { file: file!, label };
}

export function sha12(buf: Buffer): string {
  return createHash('sha256').update(buf).digest('hex').slice(0, 12);
}

export function readManifest(path: string = MANIFEST_PATH): Manifest {
  if (!existsSync(path)) return { assets: [] };
  const parsed = JSON.parse(readFileSync(path, 'utf8'));
  return {
    ...parsed,
    assets: Array.isArray(parsed.assets) ? parsed.assets : [],
  };
}

export function writeManifest(m: Manifest, path: string = MANIFEST_PATH): void {
  writeFileSync(path, JSON.stringify(m, null, 2) + '\n');
}

function defaultKeychainSource(): string | undefined {
  try {
    const out = execFileSync(
      'security',
      ['find-generic-password', '-a', process.env.USER || '', '-s', KEYCHAIN_SERVICE, '-w'],
      { stdio: ['ignore', 'pipe', 'ignore'] },
    );
    const key = out.toString().trim();
    return key.length > 0 ? key : undefined;
  } catch {
    return undefined;
  }
}

function defaultOpSource(): string | undefined {
  try {
    const out = execFileSync('op', ['read', OP_REFERENCE], { stdio: ['ignore', 'pipe', 'ignore'] });
    const key = out.toString().trim();
    return key.length > 0 ? key : undefined;
  } catch {
    return undefined;
  }
}

export class ApiKeyUnresolvedError extends Error {
  constructor() {
    super(
      `SPRITECOOK_API_KEY not resolvable — tried env var, macOS Keychain (-s ${KEYCHAIN_SERVICE}), and 1Password (${OP_REFERENCE}).`,
    );
    this.name = 'ApiKeyUnresolvedError';
  }
}

export function resolveApiKey(sources: KeySources = {}): string {
  const envSrc = sources.env ?? (() => process.env.SPRITECOOK_API_KEY);
  const keychainSrc = sources.keychain ?? defaultKeychainSource;
  const opSrc = sources.op ?? defaultOpSource;

  const fromEnv = envSrc();
  if (fromEnv && fromEnv.length > 0) return fromEnv;

  const fromKeychain = keychainSrc();
  if (fromKeychain && fromKeychain.length > 0) return fromKeychain;

  const fromOp = opSrc();
  if (fromOp && fromOp.length > 0) return fromOp;

  throw new ApiKeyUnresolvedError();
}

function guessMimeType(filename: string): string {
  const lower = filename.toLowerCase();
  if (lower.endsWith('.png')) return 'image/png';
  if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg';
  if (lower.endsWith('.webp')) return 'image/webp';
  if (lower.endsWith('.gif')) return 'image/gif';
  return 'application/octet-stream';
}

async function postImport(apiKey: string, file: string, buf: Buffer): Promise<unknown> {
  const form = new FormData();
  const filename = basename(file);
  form.append('file', new Blob([buf], { type: guessMimeType(filename) }), filename);

  const res = await fetch(`${API_BASE}${IMPORT_PATH}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${apiKey}` },
    body: form,
  });

  if (!res.ok) {
    const body = await res.text();
    // Print status + body. NEVER request headers (they hold the bearer).
    console.error(`error: HTTP ${res.status} ${res.statusText}`);
    console.error(`body: ${body}`);
    process.exit(5);
  }

  return await res.json();
}

function extractAssetId(payload: unknown): string {
  if (payload && typeof payload === 'object') {
    const obj = payload as Record<string, unknown>;
    const candidates = [obj.asset_id, obj.id, (obj.asset as Record<string, unknown> | undefined)?.id];
    for (const c of candidates) {
      if (typeof c === 'string' && c.length > 0) return c;
    }
  }
  console.error(`error: response payload has no asset_id / id field`);
  console.error(`payload: ${JSON.stringify(payload)}`);
  process.exit(6);
}

async function main(): Promise<void> {
  const { file, label } = parseArgs();
  if (!existsSync(file)) {
    console.error(`error: file not found: ${file}`);
    process.exit(4);
  }

  const buf = readFileSync(file);
  const sha = sha12(buf);

  const manifest = readManifest();
  const existing = manifest.assets.find(a => a.sha12 === sha);
  if (existing) {
    console.log(JSON.stringify({
      asset_id: existing.asset_id,
      sha12: sha,
      label: existing.label,
      cached: true,
    }));
    return;
  }

  let apiKey: string;
  try {
    apiKey = resolveApiKey();
  } catch (err) {
    console.error(`error: ${(err as Error).message}`);
    process.exit(3);
  }
  const payload = await postImport(apiKey, file, buf);
  const assetId = extractAssetId(payload);

  if (label) {
    manifest.assets.push({ asset_id: assetId, sha12: sha, label });
    writeManifest(manifest);
  }

  console.log(JSON.stringify({
    asset_id: assetId,
    sha12: sha,
    label: label ?? null,
    cached: false,
  }));
}

// Run main() only when executed directly (Bun / Node) — not when imported by tests.
const isDirectRun = (() => {
  try {
    const entrypoint = fileURLToPath(import.meta.url);
    return process.argv[1] === entrypoint;
  } catch {
    return false;
  }
})();
if (isDirectRun) {
  await main();
}
