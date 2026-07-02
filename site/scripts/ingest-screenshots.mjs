// Localize community-submitted screenshots after a maintainer has eyeballed them.
//
//   node site/scripts/ingest-screenshots.mjs [--dry]
//
// For each sidecar in community-submissions/screenshots/*.json (dropped there by the
// submission Worker), this:
//   1. fetches the image from its sourceUrl (hard 8 MB cap),
//   2. verifies it's really an image,
//   3. crops to 4:3 + downscales to <= 2560px + re-encodes PNG (this strips all EXIF,
//      so no GPS/camera metadata rides along),
//   4. writes site/public/builds/<buildId>/<n>.png  (n = next free index, so order = submission order),
//   5. appends a published credit entry to site/public/builds/credits.json,
//   6. deletes the sidecar.
//
// Run it on the submission PR's branch, review the downloaded image, then commit +
// merge — image and credit land together, and nothing binary is committed until a
// human has approved it. --dry reports what it would do without writing anything.
//
// Requires `sharp` (already a site dependency). Run from the repo root or anywhere;
// paths are resolved relative to this script.

import { readFileSync, writeFileSync, readdirSync, mkdirSync, existsSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const SIDECAR_DIR = join(repoRoot, 'community-submissions', 'screenshots');
const BUILDS_DIR = join(repoRoot, 'site', 'public', 'builds');
const CREDITS = join(BUILDS_DIR, 'credits.json');

const MAX_BYTES = 8 * 1024 * 1024;   // reject a source image larger than this
const TARGET_W = 1920, TARGET_H = 1440; // 4:3, <= 2560px longest edge; never upscales
const DRY = process.argv.includes('--dry');

const idOk = (s) => typeof s === 'string' && /^[a-z0-9_]+$/.test(s);

function loadCredits() {
  if (!existsSync(CREDITS)) return { builds: {} };
  const j = JSON.parse(readFileSync(CREDITS, 'utf8'));
  j.builds = j.builds || {};
  return j;
}

// Next free image index for a build: max existing (published entries + files on disk) + 1,
// so re-runs never clobber and numbering follows submission order.
function nextIndex(credits, buildId) {
  let max = 0;
  for (const e of credits.builds[buildId] || []) {
    const m = /^(\d+)\.png$/.exec(e.file || '');
    if (m) max = Math.max(max, parseInt(m[1], 10));
  }
  const dir = join(BUILDS_DIR, buildId);
  if (existsSync(dir)) {
    for (const f of readdirSync(dir)) {
      const m = /^(\d+)\.png$/.exec(f);
      if (m) max = Math.max(max, parseInt(m[1], 10));
    }
  }
  return max + 1;
}

async function fetchImage(url) {
  const res = await fetch(url, { redirect: 'follow' });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const type = (res.headers.get('content-type') || '').toLowerCase();
  if (type && !type.startsWith('image/')) throw new Error(`not an image (${type})`);
  const buf = Buffer.from(await res.arrayBuffer());
  if (buf.length > MAX_BYTES) throw new Error(`too large (${(buf.length / 1048576).toFixed(1)} MB > 8 MB)`);
  if (buf.length === 0) throw new Error('empty response');
  return buf;
}

const credits = loadCredits();
let processed = 0, failed = 0;

if (!existsSync(SIDECAR_DIR)) {
  console.log(`No screenshot submissions dir (${SIDECAR_DIR}) — nothing to ingest.`);
  process.exit(0);
}

const sidecars = readdirSync(SIDECAR_DIR).filter((f) => f.endsWith('.json')).sort();
if (sidecars.length === 0) {
  console.log('No pending screenshot sidecars — nothing to ingest.');
  process.exit(0);
}

for (const name of sidecars) {
  const path = join(SIDECAR_DIR, name);
  let meta;
  try {
    meta = JSON.parse(readFileSync(path, 'utf8'));
  } catch (e) {
    console.warn(`✗ ${name}: unreadable sidecar (${e.message})`);
    failed++;
    continue;
  }
  const { buildId, author, sourceUrl } = meta;
  if (!idOk(buildId) || !author || !sourceUrl) {
    console.warn(`✗ ${name}: sidecar missing buildId/author/sourceUrl`);
    failed++;
    continue;
  }

  try {
    const raw = await fetchImage(sourceUrl);
    const n = nextIndex(credits, buildId);
    const rel = `${buildId}/${n}.png`;
    const outDir = join(BUILDS_DIR, buildId);
    const outFile = join(outDir, `${n}.png`);

    if (DRY) {
      const m = await sharp(raw).metadata();
      console.log(`• ${name}: would write ${rel}  (source ${m.width}×${m.height}, by ${author})`);
    } else {
      mkdirSync(outDir, { recursive: true });
      await sharp(raw)
        .rotate() // honor EXIF orientation, THEN drop metadata on re-encode
        .resize(TARGET_W, TARGET_H, { fit: 'cover', position: 'centre', withoutEnlargement: true })
        .png({ compressionLevel: 9 })
        .toFile(outFile);

      (credits.builds[buildId] ||= []).push({
        author,
        file: `${n}.png`,
        sourceUrl,
        submittedAt: meta.submittedAt || null,
        publishedAt: new Date().toISOString(),
      });
      rmSync(path);
      console.log(`✓ ${name}: wrote ${rel}  (credited to ${author})`);
    }
    processed++;
  } catch (e) {
    console.warn(`✗ ${name}: ${e.message} — leaving sidecar in place for retry`);
    failed++;
  }
}

if (!DRY && processed > 0) {
  // Stable ordering for a clean diff: builds alphabetical, entries by file index.
  const sorted = {};
  for (const id of Object.keys(credits.builds).sort()) {
    sorted[id] = credits.builds[id].slice().sort((a, b) =>
      (parseInt(a.file) || 0) - (parseInt(b.file) || 0));
  }
  credits.builds = sorted;
  writeFileSync(CREDITS, JSON.stringify(credits, null, 2) + '\n');
  console.log(`\nUpdated ${CREDITS}`);
}

console.log(`\nDone: ${processed} ${DRY ? 'ingestable' : 'ingested'}, ${failed} failed.`);
if (!DRY && processed > 0) console.log('Review the downloaded image(s), then commit + merge the PR.');
