// Builds the gallery manifest by decoding every curated .blueprint with the SAME
// parser the viewer uses, so size/tier never drift between them.
//
//   node site/scripts/gen_manifest.mjs <blueprintDir> <outFile> <urlBase>
//
// Defaults target a local repo-root static server. The Pages workflow overrides
// the args to point at the copied blueprints/ dir on the published site.

import { readFileSync, readdirSync, writeFileSync, mkdirSync } from 'node:fs';
import { gunzipSync } from 'node:zlib';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseNbt } from '../public/viewer/js/nbt.js';
import { decodeBlueprint } from '../public/viewer/js/blueprint.js';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');

const dir = process.argv[2] || join(repoRoot, 'src/main/resources/data/mc3dprint/blueprints');
const out = process.argv[3] || join(repoRoot, 'site/public/viewer/manifest.json');
const base = process.argv[4] || '/src/main/resources/data/mc3dprint/blueprints/';

// Smallest machine tier that can print a footprint (X/Z) of `edge`. Mirrors
// MachineTier.maxFootprint + PrinterBlockEntity's X/Z-only gate (height is free).
// T1/T2 can't print structures (footprint 0), so builds start at T3.
const TIER_FOOTPRINT = [[3, 3], [4, 5], [5, 9], [6, 15], [7, 33], [8, 51]];
function tierFor(sx, sz) {
  const edge = Math.max(sx, sz);
  for (const [tier, cap] of TIER_FOOTPRINT) if (edge <= cap) return tier;
  return 9; // larger than any printable footprint
}

const builds = [];
for (const f of readdirSync(dir).filter((f) => f.endsWith('.blueprint')).sort()) {
  try {
    const bp = decodeBlueprint(parseNbt(new Uint8Array(gunzipSync(readFileSync(join(dir, f))))));
    builds.push({
      file: f, name: bp.name,
      sx: bp.sx, sy: bp.sy, sz: bp.sz,
      blocks: bp.blockCount, tier: tierFor(bp.sx, bp.sz),
    });
  } catch (e) {
    console.warn(`skipped ${f}: ${e.message}`);
  }
}

builds.sort((a, b) => a.name.localeCompare(b.name));
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, JSON.stringify({ base, builds }, null, 0) + '\n');
console.log(`Wrote ${builds.length} builds to ${out} (base=${base})`);
