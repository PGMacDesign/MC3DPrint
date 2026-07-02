/**
 * MC3DPrint — community submission Worker.
 *
 * Two intakes, both opening a reviewable pull request so a non-engineer can
 * contribute with no GitHub account and no git:
 *
 *   kind: "blueprint" (default) — a .blueprint upload + metadata, optionally with
 *       an image URL for a reference screenshot. Commits the file + a metadata
 *       sidecar to a branch; a maintainer reviews and the preview Action renders it.
 *
 *   kind: "screenshot" — a photo of an existing curated build, submitted as an
 *       image URL (never an upload). Drops a tiny sidecar in the screenshots dir;
 *       nothing binary is committed. A maintainer clicks the URL to screen it, then
 *       runs scripts/ingest-screenshots.mjs to fetch → crop → localize the image
 *       and credit the author. This is the "screenshot bounty" backend.
 *
 * It holds a GitHub bot token (GITHUB_TOKEN secret) and, optionally, a Turnstile
 * secret (TURNSTILE_SECRET). Nothing here trusts the client: every field is
 * bounded, an uploaded blueprint is validated as real gzip, and an image URL is
 * range-checked as a real image under a size cap before anything touches GitHub.
 */

const MAX_FILE_BYTES = 8 * 1024 * 1024; // matches the viewer's decompress guard
const MAX_IMAGE_BYTES = 8 * 1024 * 1024; // hard cap on the *source* image we'll localize later
const LIMITS = { name: 60, author: 40, description: 500, buildId: 80, imageUrl: 1000 };
const SCREENSHOTS_PER_BUILD_PER_DAY = 2; // per IP; enforced via KV counter when SCREENSHOT_KV is bound

export default {
  async fetch(request, env) {
    const cors = corsHeaders(request, env);

    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    if (request.method !== "POST") return json({ error: "POST only" }, 405, cors);

    // Per-IP flood guard — shed bursts cheaply before any parsing or GitHub work.
    const ip = request.headers.get("CF-Connecting-IP") || "anon";
    if (env.SUBMIT_RL) {
      const { success } = await env.SUBMIT_RL.limit({ key: ip });
      if (!success) return json({ error: "You're submitting too fast — wait a minute and try again." }, 429, cors);
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: "Body must be JSON." }, 400, cors);
    }

    // Early, cheap blocklist check (host/IP) — before captcha or GitHub work.
    const blocked = await isBlocked(env, ip, body);
    if (blocked) return json({ error: "This submission was blocked." }, 403, cors);

    try {
      if (body.kind === "screenshot") return await handleScreenshot(env, body, request, ip, cors);
      return await handleBlueprint(env, body, request, cors);
    } catch (e) {
      return json({ error: `Couldn't submit: ${e.message}` }, 502, cors);
    }
  },
};

// ───────────────────────── blueprint intake ─────────────────────────

async function handleBlueprint(env, body, request, cors) {
  const name = str(body.name, LIMITS.name);
  const author = str(body.author, LIMITS.author);
  const description = str(body.description, LIMITS.description, true);
  if (!name) return json({ error: "A build name is required." }, 400, cors);
  if (!author) return json({ error: "Your name or handle is required." }, 400, cors);

  // Optional reference screenshot URL (rides in the PR metadata; localized only if promoted).
  let imageUrl = null;
  if (body.imageUrl) {
    imageUrl = validImageUrl(body.imageUrl);
    if (!imageUrl) return json({ error: "The image link must be a valid https:// URL." }, 400, cors);
  }

  if (env.TURNSTILE_SECRET) {
    const ok = await verifyTurnstile(env.TURNSTILE_SECRET, body.turnstileToken, request);
    if (!ok) return json({ error: "Captcha check failed — please try again." }, 400, cors);
  }

  let bytes;
  try {
    bytes = base64ToBytes(body.file);
  } catch {
    return json({ error: "Couldn't read the uploaded file." }, 400, cors);
  }
  if (!bytes || bytes.length === 0) return json({ error: "No file was attached." }, 400, cors);
  if (bytes.length > MAX_FILE_BYTES) return json({ error: "File is too large (max 8 MB)." }, 413, cors);
  if (bytes[0] !== 0x1f || bytes[1] !== 0x8b) {
    return json({ error: "That doesn't look like a .blueprint file." }, 400, cors);
  }

  if (!env.GITHUB_TOKEN) return json({ error: "Submission service is misconfigured (no token)." }, 500, cors);
  const url = await openBlueprintPR(env, { name, author, description, detected: body.detected, imageUrl, bytes });
  return json({ ok: true, url }, 200, cors);
}

async function openBlueprintPR(env, { name, author, description, detected, imageUrl, bytes }) {
  const repo = env.GITHUB_REPO;
  const dir = env.SUBMISSION_DIR || "community-submissions";
  const slug = slugify(name);
  const branch = `submission/${slug}-${crypto.randomUUID().slice(0, 6)}`;
  const base = "main";

  const baseSha = (await gh(env, `/repos/${repo}/git/ref/heads/${base}`)).object.sha;
  await gh(env, `/repos/${repo}/git/refs`, "POST", { ref: `refs/heads/${branch}`, sha: baseSha });

  await gh(env, `/repos/${repo}/contents/${dir}/${slug}.blueprint`, "PUT", {
    message: `Community submission: ${name}`,
    content: bytesToBase64(bytes),
    branch,
  });
  const meta = {
    name, author, description: description || null,
    detected: detected || null,
    imageUrl: imageUrl || null,
    submittedVia: "mc3dprint.dev",
    submittedAt: new Date().toISOString(),
  };
  await gh(env, `/repos/${repo}/contents/${dir}/${slug}.json`, "PUT", {
    message: `Metadata for ${name}`,
    content: bytesToBase64(new TextEncoder().encode(JSON.stringify(meta, null, 2) + "\n")),
    branch,
  });

  const d = detected || {};
  const dims = d.sx ? `${d.sx}×${d.sy}×${d.sz}, ${d.blocks} blocks, T${d.tier}` : "unknown";
  const prBody = [
    `**Community build submitted via [mc3dprint.dev](https://mc3dprint.dev/submit).** This PR was opened automatically from the website — not by a person — so a maintainer can review the build.`,
    ``,
    `| | |`,
    `|---|---|`,
    `| **Build** | ${escapePipe(mentionSafe(name))} |`,
    `| **Submitted by** | ${escapePipe(mentionSafe(author))} |`,
    `| **Detected** | ${dims} |`,
    imageUrl ? `| **Reference shot** | ${escapePipe(imageUrl)} |` : null,
    ``,
    description ? `**Submitter's notes:**\n\n> ${mentionSafe(description).replace(/\n/g, "\n> ")}` : `_No description provided._`,
    imageUrl ? `\n**Reference screenshot** (not committed — a maintainer localizes it only if this build is promoted):\n\n${imageUrl}` : null,
    ``,
    `---`,
    `The blueprint preview bot will render the file below. Promoting it to an official build still needs the usual integration work; this PR just gets the file in for review.`,
  ].filter((l) => l !== null).join("\n");

  const pr = await gh(env, `/repos/${repo}/pulls`, "POST", {
    title: `Community build: ${name}`, head: branch, base, body: prBody,
  });
  return pr.html_url;
}

// ───────────────────────── screenshot intake ─────────────────────────

async function handleScreenshot(env, body, request, ip, cors) {
  const buildId = str(body.buildId, LIMITS.buildId);
  const author = str(body.author, LIMITS.author);
  if (!buildId || !/^[a-z0-9_]+$/.test(buildId)) return json({ error: "Missing or invalid build id." }, 400, cors);
  if (!author) return json({ error: "Your name or handle is required." }, 400, cors);

  const imageUrl = validImageUrl(body.imageUrl);
  if (!imageUrl) return json({ error: "Paste a direct https:// link to your image (PNG/JPG/WebP)." }, 400, cors);

  // The build must actually be one we ship. Best-effort: if the manifest can't be
  // fetched, don't block the submission (a maintainer reviews it anyway).
  if (!(await buildExists(env, buildId))) {
    return json({ error: "That build isn't one of the curated builds." }, 400, cors);
  }

  if (env.TURNSTILE_SECRET) {
    const ok = await verifyTurnstile(env.TURNSTILE_SECRET, body.turnstileToken, request);
    if (!ok) return json({ error: "Captcha check failed — please try again." }, 400, cors);
  }

  // Per-(build, IP) daily cap — lets folks contribute without racing, but stops one
  // person flooding a single build. Skipped if no KV is bound (dev / not configured).
  if (env.SCREENSHOT_KV) {
    const key = `cap:${buildId}:${ip}:${utcDay()}`;
    const used = parseInt((await env.SCREENSHOT_KV.get(key)) || "0", 10);
    if (used >= SCREENSHOTS_PER_BUILD_PER_DAY) {
      return json({ error: `You've already submitted ${SCREENSHOTS_PER_BUILD_PER_DAY} screenshots for this build today — thanks! Try again tomorrow.` }, 429, cors);
    }
    // Bump first so a mid-flight error still counts against the cap (fail closed).
    await env.SCREENSHOT_KV.put(key, String(used + 1), { expirationTtl: 90000 });
  }

  // Confirm the URL really points at an image under our size cap before opening a PR.
  const check = await probeImage(imageUrl);
  if (!check.ok) return json({ error: check.error }, 400, cors);

  if (!env.GITHUB_TOKEN) return json({ error: "Submission service is misconfigured (no token)." }, 500, cors);
  const url = await openScreenshotPR(env, { buildId, author, imageUrl });
  return json({ ok: true, url }, 200, cors);
}

async function openScreenshotPR(env, { buildId, author, imageUrl }) {
  const repo = env.GITHUB_REPO;
  const dir = env.SCREENSHOT_DIR || "community-submissions/screenshots";
  const rand = crypto.randomUUID().slice(0, 6);
  const branch = `screenshot/${buildId}-${rand}`;
  const base = "main";

  const baseSha = (await gh(env, `/repos/${repo}/git/ref/heads/${base}`)).object.sha;
  await gh(env, `/repos/${repo}/git/refs`, "POST", { ref: `refs/heads/${branch}`, sha: baseSha });

  // Text-only sidecar. The image bytes are never committed by the Worker — the
  // ingest script fetches + localizes them once a maintainer approves.
  const sidecar = {
    buildId, author,
    sourceUrl: imageUrl,
    submittedVia: "mc3dprint.dev",
    submittedAt: new Date().toISOString(),
  };
  await gh(env, `/repos/${repo}/contents/${dir}/${buildId}-${rand}.json`, "PUT", {
    message: `Screenshot submission for ${buildId}`,
    content: bytesToBase64(new TextEncoder().encode(JSON.stringify(sidecar, null, 2) + "\n")),
    branch,
  });

  const prBody = [
    `**Screenshot submitted via [mc3dprint.dev](https://mc3dprint.dev/builds/${buildId}/).** Opened automatically from the website — not by a person.`,
    ``,
    `| | |`,
    `|---|---|`,
    `| **Build** | \`${escapePipe(buildId)}\` |`,
    `| **Credited to** | ${escapePipe(mentionSafe(author))} |`,
    `| **Image URL** | ${escapePipe(imageUrl)} |`,
    ``,
    `> ⚠️ **Review the image before merging.** Open the URL above and confirm it's a real, appropriate screenshot of this build.`,
    ``,
    `**To publish it:** on this branch, run \`node site/scripts/ingest-screenshots.mjs\` — it fetches the image, crops/downscales it, strips metadata, writes \`site/public/builds/${buildId}/<n>.png\`, credits **${escapePipe(mentionSafe(author))}** in \`credits.json\`, and removes this sidecar. Review the downloaded image, commit, and merge.`,
    ``,
    `Nothing binary is in this PR — only the text sidecar below.`,
  ].join("\n");

  const pr = await gh(env, `/repos/${repo}/pulls`, "POST", {
    title: `Screenshot: ${buildId} (by ${author})`, head: branch, base, body: prBody,
  });
  return pr.html_url;
}

// ───────────────────────── image URL validation ─────────────────────────

function validImageUrl(v) {
  if (typeof v !== "string") return null;
  const s = v.trim().slice(0, LIMITS.imageUrl);
  let u;
  try { u = new URL(s); } catch { return null; }
  if (u.protocol !== "https:") return null;
  return u.toString();
}

// Range-request the URL to confirm it's a real image under the size cap, without
// downloading the whole thing. Lenient on hosts that omit headers — the ingest
// script re-validates fully on fetch, and a human reviews before merge.
async function probeImage(url) {
  try {
    let res = await fetch(url, { method: "HEAD", redirect: "follow" });
    if (!res.ok) {
      // Some hosts reject HEAD — try a 1-byte ranged GET instead.
      res = await fetch(url, { method: "GET", headers: { Range: "bytes=0-0" }, redirect: "follow" });
    }
    const type = (res.headers.get("content-type") || "").toLowerCase();
    if (type && !type.startsWith("image/")) {
      return { ok: false, error: "That link isn't an image (PNG/JPG/WebP)." };
    }
    const len = parseInt(res.headers.get("content-length") || "0", 10);
    if (len && len > MAX_IMAGE_BYTES) {
      return { ok: false, error: "That image is too large (max 8 MB) — please link a smaller one." };
    }
    return { ok: true };
  } catch {
    // Network hiccup probing the host — let it through; the maintainer/ingest will catch it.
    return { ok: true };
  }
}

async function buildExists(env, buildId) {
  const manifestUrl = env.SITE_MANIFEST_URL || "https://mc3dprint.dev/viewer/manifest.json";
  try {
    const res = await fetch(manifestUrl, { cf: { cacheTtl: 300, cacheEverything: true } });
    if (!res.ok) return true; // can't verify → don't block; review catches it
    const m = await res.json();
    return (m.builds || []).some((b) => (b.file || "").replace(/\.blueprint$/, "") === buildId);
  } catch {
    return true;
  }
}

// ───────────────────────── blocklist ─────────────────────────

// KV key "blocklist" holds { "hosts": [...], "ips": [...] }. Absent KV = no blocklist.
// Update without a redeploy:  wrangler kv key put --binding SCREENSHOT_KV blocklist '{"hosts":["bad.example"],"ips":["1.2.3.4"]}'
async function isBlocked(env, ip, body) {
  if (!env.SCREENSHOT_KV) return false;
  let list;
  try {
    list = JSON.parse((await env.SCREENSHOT_KV.get("blocklist")) || "{}");
  } catch {
    return false;
  }
  if (Array.isArray(list.ips) && list.ips.includes(ip)) return true;
  const raw = body && (body.imageUrl || "");
  if (Array.isArray(list.hosts) && list.hosts.length && typeof raw === "string" && raw) {
    try {
      const host = new URL(raw).hostname.toLowerCase();
      if (list.hosts.some((h) => host === h || host.endsWith("." + h))) return true;
    } catch { /* not a URL — nothing to match */ }
  }
  return false;
}

// ───────────────────────── GitHub ─────────────────────────

async function gh(env, path, method = "GET", payload) {
  const res = await fetch(`https://api.github.com${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${env.GITHUB_TOKEN}`,
      Accept: "application/vnd.github+json",
      "User-Agent": "mc3dprint-submit-worker",
      "X-GitHub-Api-Version": "2022-11-28",
      ...(payload ? { "Content-Type": "application/json" } : {}),
    },
    body: payload ? JSON.stringify(payload) : undefined,
  });
  if (!res.ok) {
    const txt = await res.text().catch(() => "");
    throw new Error(`GitHub ${res.status}${txt ? `: ${txt.slice(0, 140)}` : ""}`);
  }
  return res.json();
}

// ───────────────────────── helpers ─────────────────────────

async function verifyTurnstile(secret, token, request) {
  if (!token) return false;
  const form = new FormData();
  form.append("secret", secret);
  form.append("response", token);
  const ip = request.headers.get("CF-Connecting-IP");
  if (ip) form.append("remoteip", ip);
  try {
    const res = await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", { method: "POST", body: form });
    const out = await res.json();
    return out.success === true;
  } catch {
    return false;
  }
}

function corsHeaders(request, env) {
  const origin = request.headers.get("Origin") || "";
  const allowed = (env.ALLOWED_ORIGIN || "").split(",").map((s) => s.trim());
  const ok = allowed.includes(origin) || /^https?:\/\/localhost(:\d+)?$/.test(origin);
  return {
    "Access-Control-Allow-Origin": ok ? origin : allowed[0] || "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Access-Control-Max-Age": "86400",
    Vary: "Origin",
  };
}

function json(obj, status, headers) {
  return new Response(JSON.stringify(obj), { status, headers: { ...headers, "Content-Type": "application/json" } });
}

function str(v, max, allowEmpty = false) {
  if (typeof v !== "string") return allowEmpty ? "" : null;
  const t = v.trim().slice(0, max);
  return t || (allowEmpty ? "" : null);
}

function utcDay() {
  return new Date().toISOString().slice(0, 10); // YYYY-MM-DD (UTC)
}

function slugify(name) {
  const s = name.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 40);
  return s || "build";
}

function escapePipe(s) {
  return String(s).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

// Neutralize @mentions and #issue-refs in submitter text so the bot can't be used
// to notify/ping people. A zero-width space after the sigil keeps it readable but
// stops GitHub from linking it.
function mentionSafe(s) {
  return String(s).replace(/([@#])(?=\w)/g, "$1\\u200b");
}

function base64ToBytes(b64) {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function bytesToBase64(bytes) {
  let bin = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    bin += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
  }
  return btoa(bin);
}
