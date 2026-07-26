import pako from 'https://cdn.jsdelivr.net/npm/pako@2.1.0/+esm';
import { parseNbt } from './nbt.js';
import { decodeBlueprint } from './blueprint.js';
import { loadColorTable, isUnknown } from './colors.js';
import { Viewer } from './viewer.js';

const $ = (id) => document.getElementById(id);
const viewer = new Viewer($('canvas'));
const status = $('status');

// Hardening: the file/URL may be a stranger's upload, so cap both the delivered
// bytes and the inflated payload (gzip-bomb defense) and restrict ?src= fetches.
const MAX_COMPRESSED = 8 * 1024 * 1024;
const MAX_INFLATED = 32 * 1024 * 1024;
const SRC_ALLOWED_HOSTS = new Set(['raw.githubusercontent.com', location.hostname]);

// Smallest printer tier for a footprint (X/Z); mirrors MachineTier.maxFootprint.
const TIER_FOOTPRINT = [[3, 3], [4, 5], [5, 9], [6, 15], [7, 33], [8, 51]];
function tierFor(sx, sz) {
  const e = Math.max(sx, sz);
  for (const [t, c] of TIER_FOOTPRINT) if (e <= c) return t;
  return 9;
}

// Stream-inflate, discarding output past the cap so a bomb can't exhaust memory.
function inflateCapped(bytes) {
  const inf = new pako.Inflate();
  const chunks = [];
  let total = 0;
  inf.onData = (c) => { total += c.length; if (total <= MAX_INFLATED) chunks.push(c); };
  inf.push(bytes, true);
  if (inf.err) throw new Error(inf.msg || 'not a valid gzip file');
  if (total > MAX_INFLATED) throw new Error('decompressed payload exceeds cap');
  const out = new Uint8Array(total);
  let off = 0;
  for (const c of chunks) { out.set(c, off); off += c.length; }
  return out;
}

await loadColorTable();

function setStatus(msg, error = false) {
  status.textContent = msg;
  status.classList.toggle('error', error);
}

function show(bp) {
  const drawn = viewer.load(bp);
  const unknown = new Set();
  for (const p of bp.palette) if (isUnknown(p.id)) unknown.add(p.id);

  $('layer').max = bp.sy;
  $('layer').value = bp.sy;
  $('layerVal').textContent = `${bp.sy}/${bp.sy}`;

  $('info').innerHTML = '';
  const line = (label, val) => {
    const d = document.createElement('div');
    d.className = 'stat';
    const k = document.createElement('span'); k.className = 'k'; k.textContent = label;
    const v = document.createElement('span'); v.className = 'v'; v.textContent = val;
    d.append(k, v); $('info').append(d);
  };
  const tier = tierFor(bp.sx, bp.sz);
  line('Name', bp.name);
  line('Size', `${bp.sx} × ${bp.sy} × ${bp.sz}`);
  line('Min tier', tier > 8 ? 'too large to print' : `T${tier} printer`);
  line('Blocks', bp.blockCount.toLocaleString());
  line('Palette', `${bp.palette.length} states`);
  line('Drawn', `${drawn.toLocaleString()} solid voxels`);
  if (unknown.size) {
    line('Unknown', `${unknown.size} block(s) → magenta`);
    const u = document.createElement('div');
    u.className = 'unknown';
    u.textContent = [...unknown].join(', ');
    $('info').append(u);
  }
  setStatus(`Loaded “${bp.name}”`);
}

function handleBytes(bytes, label) {
  try {
    if (bytes.length > MAX_COMPRESSED) throw new Error('file too large');
    show(decodeBlueprint(parseNbt(inflateCapped(bytes))));
  } catch (e) {
    setStatus(`Couldn't read ${label || 'file'}: ${e.message}`, true);
    console.error(e);
  }
}

// Only fetch same-origin (gallery/relative) or raw.githubusercontent.com (the PR
// deep-link). Blocks the viewer being abused as an open fetch-proxy via ?src=.
function srcAllowed(url) {
  try {
    const u = new URL(url, location.href);
    return u.protocol === 'http:' || u.protocol === 'https:'
      ? SRC_ALLOWED_HOSTS.has(u.hostname) : false;
  } catch { return false; }
}

async function loadUrl(url) {
  if (!srcAllowed(url)) {
    setStatus('Source not allowed (only this site or raw.githubusercontent.com).', true);
    return;
  }
  setStatus(`Fetching ${url}…`);
  try {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    handleBytes(new Uint8Array(await res.arrayBuffer()), url);
  } catch (e) {
    setStatus(`Fetch failed: ${e.message}`, true);
  }
}

// --- inputs ---

document.body.addEventListener('dragover', (e) => { e.preventDefault(); document.body.classList.add('drag'); });
document.body.addEventListener('dragleave', () => document.body.classList.remove('drag'));
document.body.addEventListener('drop', async (e) => {
  e.preventDefault();
  document.body.classList.remove('drag');
  const file = e.dataTransfer.files[0];
  if (file) handleBytes(new Uint8Array(await file.arrayBuffer()), file.name);
});

$('file').addEventListener('change', async (e) => {
  const file = e.target.files[0];
  if (file) handleBytes(new Uint8Array(await file.arrayBuffer()), file.name);
});

$('loadUrl').addEventListener('click', () => { const u = $('url').value.trim(); if (u) loadUrl(u); });
$('url').addEventListener('keydown', (e) => { if (e.key === 'Enter') $('loadUrl').click(); });

$('ghost').addEventListener('change', (e) => viewer.setGhost(e.target.checked));
$('layer').addEventListener('input', (e) => {
  viewer.setLayer(+e.target.value);
  $('layerVal').textContent = `${e.target.value}/${$('layer').max}`;
});

// --- curated gallery (from manifest.json; absent locally is fine) ---

let gallery = [];
function renderGallery(filter = '') {
  const q = filter.toLowerCase();
  const list = $('gallery');
  list.innerHTML = '';
  for (const b of gallery) {
    if (q && !b.name.toLowerCase().includes(q)) continue;
    const item = document.createElement('div');
    item.className = 'item';
    const name = document.createElement('span'); name.className = 'name'; name.textContent = b.name;
    const dims = document.createElement('span'); dims.className = 'dims'; dims.textContent = `${b.sx}×${b.sy}×${b.sz}`;
    const tier = document.createElement('span'); tier.className = 'tier'; tier.textContent = `T${b.tier}`;
    item.append(name, dims, tier);
    item.addEventListener('click', () => loadUrl(galleryBase + b.file));
    list.append(item);
  }
}

let galleryBase = '';
(async () => {
  try {
    const res = await fetch('manifest.json');
    if (!res.ok) return;
    const manifest = await res.json();
    galleryBase = manifest.base || '';
    gallery = manifest.builds || [];
    if (gallery.length) {
      $('galleryGroup').hidden = false;
      renderGallery();
      $('search').addEventListener('input', (e) => renderGallery(e.target.value));
    }
  } catch (_) { /* no manifest locally, gallery just stays hidden */ }
})();

// ?src= deep-link (the PR-comment flow) works locally too.
const src = new URLSearchParams(location.search).get('src');
if (src) { $('url').value = src; loadUrl(src); }
else setStatus('Drag a .blueprint onto the page, or load one by path/URL.');
