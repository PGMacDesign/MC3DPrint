/**
 * MC3DPrint — community build submission Worker.
 *
 * Receives a .blueprint upload + metadata from the website's /submit page and
 * opens a pull request on the repo so a non-engineer can contribute a build with
 * no GitHub account and no git. The PR is the intake: a human reviews it, and the
 * existing blueprint-preview Action auto-renders the file in a comment.
 *
 * It holds a GitHub bot token (set as the GITHUB_TOKEN secret) and, optionally, a
 * Turnstile secret (TURNSTILE_SECRET) — if that's set, a captcha token is required.
 * Nothing here trusts the client: every field is bounded and the file is validated
 * as real gzip before anything touches GitHub.
 */

const MAX_FILE_BYTES = 8 * 1024 * 1024; // matches the viewer's decompress guard
const LIMITS = { name: 60, author: 40, description: 500 };

export default {
  async fetch(request, env) {
    const cors = corsHeaders(request, env);

    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    if (request.method !== "POST") return json({ error: "POST only" }, 405, cors);

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: "Body must be JSON." }, 400, cors);
    }

    // ---- validate text fields ----
    const name = str(body.name, LIMITS.name);
    const author = str(body.author, LIMITS.author);
    const description = str(body.description, LIMITS.description, true);
    if (!name) return json({ error: "A build name is required." }, 400, cors);
    if (!author) return json({ error: "Your name or handle is required." }, 400, cors);

    // ---- optional captcha ----
    if (env.TURNSTILE_SECRET) {
      const ok = await verifyTurnstile(env.TURNSTILE_SECRET, body.turnstileToken, request);
      if (!ok) return json({ error: "Captcha check failed — please try again." }, 400, cors);
    }

    // ---- validate the file ----
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

    // ---- open the PR ----
    if (!env.GITHUB_TOKEN) return json({ error: "Submission service is misconfigured (no token)." }, 500, cors);
    try {
      const url = await openPullRequest(env, { name, author, description, detected: body.detected, bytes });
      return json({ ok: true, url }, 200, cors);
    } catch (e) {
      return json({ error: `Couldn't submit: ${e.message}` }, 502, cors);
    }
  },
};

// ───────────────────────── GitHub ─────────────────────────

async function openPullRequest(env, { name, author, description, detected, bytes }) {
  const repo = env.GITHUB_REPO;
  const dir = env.SUBMISSION_DIR || "community-submissions";
  const slug = slugify(name);
  const rand = crypto.randomUUID().slice(0, 6);
  const branch = `submission/${slug}-${rand}`;
  const base = "main";

  // base sha
  const ref = await gh(env, `/repos/${repo}/git/ref/heads/${base}`);
  const baseSha = ref.object.sha;

  // new branch
  await gh(env, `/repos/${repo}/git/refs`, "POST", { ref: `refs/heads/${branch}`, sha: baseSha });

  // commit the blueprint + a metadata sidecar onto the branch
  const fileB64 = bytesToBase64(bytes);
  await gh(env, `/repos/${repo}/contents/${dir}/${slug}.blueprint`, "PUT", {
    message: `Community submission: ${name}`,
    content: fileB64,
    branch,
  });
  const meta = {
    name, author, description: description || null,
    detected: detected || null,
    submittedVia: "mc3dprint.dev",
    submittedAt: new Date().toISOString(),
  };
  await gh(env, `/repos/${repo}/contents/${dir}/${slug}.json`, "PUT", {
    message: `Metadata for ${name}`,
    content: bytesToBase64(new TextEncoder().encode(JSON.stringify(meta, null, 2) + "\n")),
    branch,
  });

  // PR
  const d = detected || {};
  const dims = d.sx ? `${d.sx}×${d.sy}×${d.sz}, ${d.blocks} blocks, T${d.tier}` : "unknown";
  const prBody = [
    `**Community build submitted via [mc3dprint.dev](https://mc3dprint.dev/submit).** This PR was opened automatically from the website — not by a person — so a maintainer can review the build.`,
    ``,
    `| | |`,
    `|---|---|`,
    `| **Build** | ${escapePipe(name)} |`,
    `| **Submitted by** | ${escapePipe(author)} |`,
    `| **Detected** | ${dims} |`,
    ``,
    description ? `**Submitter's notes:**\n\n> ${description.replace(/\n/g, "\n> ")}` : `_No description provided._`,
    ``,
    `---`,
    `The blueprint preview bot will render the file below. Promoting it to an official build still needs the usual integration work; this PR just gets the file in for review.`,
  ].join("\n");

  const pr = await gh(env, `/repos/${repo}/pulls`, "POST", {
    title: `Community build: ${name}`,
    head: branch,
    base,
    body: prBody,
  });
  return pr.html_url;
}

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

function slugify(name) {
  const s = name.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 40);
  return s || "build";
}

function escapePipe(s) {
  return s.replace(/\|/g, "\\|").replace(/\n/g, " ");
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
