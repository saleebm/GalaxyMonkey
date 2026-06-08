#!/usr/bin/env bun
// Legacy → normalized port script (gma-7gl.3).
// Reads public/assets/sprites/legacy/, writes tools/sprite-pipeline/normalized/.
// Handles: L/R enemy rename (per Enemy.java velX-sign mapping), blackhole
// downscale to WebP ≤512px / ≤100KB, back.jpg → WebP ≤200KB. All other staged
// files pass through unchanged. Idempotent — skips ops whose output already
// exists with the recorded sha. Writes port-report.json for audit.

import { createHash } from 'node:crypto';
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { dirname, join, relative } from 'node:path';
import sharp from 'sharp';

const HERE = import.meta.dir;
const REPO_ROOT = join(HERE, '..', '..');
const LEGACY = join(REPO_ROOT, 'public', 'assets', 'sprites', 'legacy');
const NORMALIZED = join(HERE, 'normalized');
const REPORT_PATH = join(HERE, 'port-report.json');

const BLACKHOLE_MAX_DIM = 512;
const BLACKHOLE_MAX_BYTES = 100 * 1024;
const BACK_MAX_BYTES = 200 * 1024;

// Per asset-map.json: each entry's `condition` field is the truth source for
// L/R. WHITE uses `1` for RIGHT; SHADY uses `1` for LEFT. We name outputs by
// semantic direction (left/right), not by legacy filename.
const ENEMY_RENAMES: Array<{ from: string; to: string }> = [
  { from: 'enemies/enemy.png',    to: 'enemies/preppy_left.png'  }, // PREPPY  velX <  0
  { from: 'enemies/enemyr.png',   to: 'enemies/preppy_right.png' }, // PREPPY  velX >= 0
  { from: 'enemies/enemyb.png',   to: 'enemies/white_left.png'   }, // WHITE   velX <  0
  { from: 'enemies/enemyb1.png',  to: 'enemies/white_right.png'  }, // WHITE   velX >= 0  (1 = RIGHT)
  { from: 'enemies/enemys1.png',  to: 'enemies/shady_left.png'   }, // SHADY   velX <  0  (1 = LEFT)
  { from: 'enemies/enemysr.png',  to: 'enemies/shady_right.png'  }, // SHADY   velX >= 0
];

type OpKind = 'rename' | 'resize-webp' | 'webp' | 'passthrough';
interface Op {
  kind: OpKind;
  inputRel: string;
  outputRel: string;
}
interface OpReport extends Op {
  bytesBefore: number;
  bytesAfter: number;
  reduction: number; // 0..1
  shaBefore: string;
  shaAfter: string;
  skipped: boolean;
}

function sha12(buf: Buffer): string {
  return createHash('sha256').update(buf).digest('hex').slice(0, 12);
}

function walk(dir: string): string[] {
  const out: string[] = [];
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) out.push(...walk(p));
    else out.push(p);
  }
  return out;
}

function ensureDir(filePath: string): void {
  mkdirSync(dirname(filePath), { recursive: true });
}

function readPriorReport(): Record<string, OpReport> | null {
  if (!existsSync(REPORT_PATH)) return null;
  try {
    const parsed = JSON.parse(readFileSync(REPORT_PATH, 'utf8'));
    const map: Record<string, OpReport> = {};
    for (const op of parsed.operations as OpReport[]) map[op.outputRel] = op;
    return map;
  } catch {
    return null;
  }
}

// Pick quality that fits the byte budget. WebP output is deterministic on a
// fixed libvips, so re-runs with the same input yield byte-identical outputs.
async function encodeWebpUnderBudget(
  input: Buffer | string,
  budgetBytes: number,
  resize?: { width: number; height: number; fit: 'inside' },
): Promise<{ buffer: Buffer; quality: number }> {
  const qualities = [85, 80, 75, 70, 65, 60, 55, 50, 45, 40];
  let lastBuf: Buffer | null = null;
  let lastQ = qualities[0];
  for (const q of qualities) {
    let pipeline = sharp(input);
    if (resize) pipeline = pipeline.resize(resize);
    const buf = await pipeline.webp({ quality: q, effort: 6 }).toBuffer();
    lastBuf = buf;
    lastQ = q;
    if (buf.length <= budgetBytes) return { buffer: buf, quality: q };
  }
  return { buffer: lastBuf!, quality: lastQ };
}

async function runOp(op: Op, prior: Record<string, OpReport> | null): Promise<OpReport> {
  const inputAbs = join(LEGACY, op.inputRel);
  const outputAbs = join(NORMALIZED, op.outputRel);

  const inputBuf = readFileSync(inputAbs);
  const shaBefore = sha12(inputBuf);

  if (
    existsSync(outputAbs) &&
    prior?.[op.outputRel]?.shaBefore === shaBefore
  ) {
    const outBuf = readFileSync(outputAbs);
    return {
      ...op,
      bytesBefore: inputBuf.length,
      bytesAfter: outBuf.length,
      reduction: 1 - outBuf.length / inputBuf.length,
      shaBefore,
      shaAfter: sha12(outBuf),
      skipped: true,
    };
  }

  ensureDir(outputAbs);

  let outBuf: Buffer;
  switch (op.kind) {
    case 'rename':
    case 'passthrough':
      copyFileSync(inputAbs, outputAbs);
      outBuf = inputBuf;
      break;
    case 'resize-webp': {
      const result = await encodeWebpUnderBudget(inputBuf, BLACKHOLE_MAX_BYTES, {
        width: BLACKHOLE_MAX_DIM,
        height: BLACKHOLE_MAX_DIM,
        fit: 'inside',
      });
      writeFileSync(outputAbs, result.buffer);
      outBuf = result.buffer;
      break;
    }
    case 'webp': {
      const result = await encodeWebpUnderBudget(inputBuf, BACK_MAX_BYTES);
      writeFileSync(outputAbs, result.buffer);
      outBuf = result.buffer;
      break;
    }
  }

  return {
    ...op,
    bytesBefore: inputBuf.length,
    bytesAfter: outBuf.length,
    reduction: 1 - outBuf.length / inputBuf.length,
    shaBefore,
    shaAfter: sha12(outBuf),
    skipped: false,
  };
}

async function main(): Promise<void> {
  if (!existsSync(LEGACY)) {
    console.error(`legacy dir missing: ${LEGACY}`);
    process.exit(1);
  }

  // Build the op list. Explicit ops first, then pass-through for everything
  // else under legacy/.
  const renameInputs = new Set(ENEMY_RENAMES.map(r => r.from));
  const specialInputs = new Set(['fx/blackhole.png', 'celestial/back.jpg', ...renameInputs]);

  const ops: Op[] = [];

  for (const r of ENEMY_RENAMES) {
    ops.push({ kind: 'rename', inputRel: r.from, outputRel: r.to });
  }
  ops.push({ kind: 'resize-webp', inputRel: 'fx/blackhole.png', outputRel: 'fx/blackhole.webp' });
  ops.push({ kind: 'webp', inputRel: 'celestial/back.jpg', outputRel: 'celestial/back.webp' });

  for (const abs of walk(LEGACY).sort()) {
    const rel = relative(LEGACY, abs);
    if (specialInputs.has(rel)) continue;
    ops.push({ kind: 'passthrough', inputRel: rel, outputRel: rel });
  }

  const prior = readPriorReport();
  const reports: OpReport[] = [];
  for (const op of ops) {
    reports.push(await runOp(op, prior));
  }

  const report = {
    $schema: 'port-report.schema (informal)',
    meta: {
      sourceRoot: relative(REPO_ROOT, LEGACY),
      outputRoot: relative(REPO_ROOT, NORMALIZED),
    },
    budgets: {
      'fx/blackhole.webp': { maxDim: BLACKHOLE_MAX_DIM, maxBytes: BLACKHOLE_MAX_BYTES },
      'celestial/back.webp': { maxBytes: BACK_MAX_BYTES },
    },
    operations: reports.sort((a, b) => a.outputRel.localeCompare(b.outputRel)),
  };
  writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2) + '\n');

  const blackhole = reports.find(r => r.outputRel === 'fx/blackhole.webp')!;
  const back = reports.find(r => r.outputRel === 'celestial/back.webp')!;
  const transformed = reports.filter(r => r.kind !== 'passthrough');
  const skipped = reports.filter(r => r.skipped).length;

  console.log(`[port-legacy] ${reports.length} ops (${skipped} skipped, ${reports.length - skipped} written)`);
  console.log(`[port-legacy] transforms:`);
  for (const op of transformed) {
    const pct = (op.reduction * 100).toFixed(1);
    console.log(`  ${op.inputRel} → ${op.outputRel}   ${op.bytesBefore}B → ${op.bytesAfter}B  (-${pct}%)`);
  }
  console.log(`[port-legacy] blackhole budget: ${blackhole.bytesAfter}B / ${BLACKHOLE_MAX_BYTES}B  ${blackhole.bytesAfter <= BLACKHOLE_MAX_BYTES ? 'PASS' : 'FAIL'}`);
  console.log(`[port-legacy] back      budget: ${back.bytesAfter}B / ${BACK_MAX_BYTES}B  ${back.bytesAfter <= BACK_MAX_BYTES ? 'PASS' : 'FAIL'}`);

  if (blackhole.bytesAfter > BLACKHOLE_MAX_BYTES || back.bytesAfter > BACK_MAX_BYTES) {
    process.exit(2);
  }
}

await main();
