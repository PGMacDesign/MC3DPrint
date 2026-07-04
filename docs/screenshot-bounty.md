# Screenshot Bounty — design & reference

Community-submitted screenshots for curated builds, shown on the website and
credited to the submitter. The "bounty" framing: anyone can contribute a photo of
a build, with no GitHub account, and get credited — builds without a screenshot
advertise that they want one.

Shipped + deployed 2026-07-01. This doc is the source of truth for *how it works*
so the feature can be changed without re-deriving it. Website-only — no mod/jar
code, no version cascade.

Related: worker `worker/README.md` (ops), memory `github-blueprint-renderer`
(the whole site + Worker), the existing Submit-a-Build flow it extends.

---

## The core decision: URL-based, not file upload

Submitters paste a **direct `https` image URL** (imgur, catbox, …) — they do **not**
upload a file. This was a deliberate choice over accepting uploads:

- **No risky bytes hit our infra before a human looks.** A raw upload of a
  lewd/garbage image would land in a public PR branch (and git history) before
  review. A URL is inert text until someone opens it.
- **We localize on approval**, not at runtime. When a maintainer approves, we
  fetch the image **once** and commit our **own** copy. That single choice kills
  three problems a naked hotlink would have:
  - **bait-and-switch** — submitter shows an innocent image, then swaps it at the
    same URL after approval;
  - **link rot** — imgur deletes it and the gallery breaks;
  - **privacy** — every site visitor's IP would leak to the third-party host.
- **The Worker runtime can't resize images** anyway (`sharp` is a native binary,
  not available in Workers; real server-side resizing needs paid Cloudflare
  Images). Localizing in a Node script (which *can* use `sharp`) is where the
  crop/downscale/EXIF-strip happens.

Companion decision — **vet-first, then automate**: v1 uses a **local ingest
script** a maintainer runs after eyeballing the image. If volume ever justifies
it, flip that to a merge-triggered GitHub Action (see [Extension points](#extension-points)).

---

## End-to-end flow

```
Submitter (website)                Worker                     Maintainer (local)             Site
─────────────────────              ──────                     ──────────────────             ────
"Add a screenshot" on      POST kind:"screenshot"     opens PR w/ text-only
/builds/<id>/  ───────────▶ {buildId,imageUrl,author} ─▶ sidecar in                ┌─ review PR (open the URL)
  paste https URL             validate + cap + probe      community-submissions/    │
  + handle + consent          + Turnstile                 screenshots/<id>-<rand>   ├─ on the PR branch:
                                                           .json                    │   node site/scripts/
                                                     (NO image bytes committed)     │   ingest-screenshots.mjs
                                                                                    │     fetch → crop 4:3 →
                                                                                    │     downscale ≤2560 →
                                                                                    │     re-encode PNG (EXIF gone)
                                                                                    │   → builds/<id>/<n>.png
                                                                                    │   → append credits.json
                                                                                    │   → delete sidecar
                                                                                    ├─ eyeball the PNG
                                                                                    └─ commit + merge ──────────▶ image + credit
                                                                                                                  publish together
```

Image and credit land in the **same merge** — nothing binary is public until a
human approved it, and the published copy is pinned (immune to later URL changes).

---

## Components

| File | Role |
|---|---|
| `worker/src/index.js` | The Worker. `kind:"screenshot"` intake + the existing `kind:"blueprint"` intake (now with an optional reference-shot `imageUrl`). Validation, caps, blocklist, opens the PR. |
| `worker/wrangler.toml` | Worker config: vars, the `SUBMIT_RL` rate-limit binding, the `SCREENSHOT_KV` binding. |
| `site/scripts/ingest-screenshots.mjs` | Node + `sharp` script. Turns approved sidecars into localized, cropped, EXIF-stripped PNGs + credits. The human-gated publish step. |
| `site/public/builds/credits.json` | Rendered source of truth for who-shot-what. Committed. |
| `site/public/builds/<id>/<n>.png` | Localized screenshots, `<n>` = submission order (1, 2, …). Committed by the maintainer after ingest. |
| `site/src/pages/builds/[id].astro` | Build page: ordered gallery + thumb switcher + "Screenshot by @handle" credit + inline "Add a screenshot" form. |
| `site/src/pages/gallery.astro` | Gallery cards: thumbnail = shot #1; "📷 Screenshot wanted" badge when a build has none (the bounty surface). |
| `site/src/pages/submit.astro` | New-build submit: optional screenshot URL field. |
| `site/src/pages/faq.astro` | Player-facing "Can I add a screenshot?" explainer. |
| `community-submissions/screenshots/<id>-<rand>.json` | Transient text sidecar the Worker drops per submission; deleted by ingest. |

---

## Worker endpoint contract

`POST https://mc3dprint-submit.patrick-4bc.workers.dev` (JSON). CORS-locked to
`ALLOWED_ORIGIN` (+ localhost). One Worker, branched on `body.kind`.

### `kind: "screenshot"`
```jsonc
{
  "kind": "screenshot",
  "buildId": "watchtower",              // must match ^[a-z0-9_]+$ and be a real curated build
  "imageUrl": "https://i.imgur.com/…",  // https only, ≤1000 chars
  "author": "@handle",                  // ≤40 chars
  "turnstileToken": "…"                 // required when TURNSTILE_SECRET is set
}
```
Validation order (first failure returns a 4xx with a friendly `error`):
1. **Blocklist** (host + IP) — checked before anything, `403` if hit.
2. `buildId` present + regex, `author` present.
3. `imageUrl` is a valid `https` URL.
4. **`buildId` exists** — fetched from `SITE_MANIFEST_URL` (`/viewer/manifest.json`).
   If the manifest can't be fetched, this check is skipped (don't block on a
   transient; review catches it).
5. **Turnstile** (if `TURNSTILE_SECRET` set).
6. **Daily cap** — `SCREENSHOT_KV` key `cap:<buildId>:<ip>:<UTC-date>`, max
   `SCREENSHOTS_PER_BUILD_PER_DAY` (2). Bumped **before** the work (fail-closed),
   TTL ~25 h. Skipped entirely if `SCREENSHOT_KV` is unbound.
7. **Image probe** — `HEAD` (falls back to a 1-byte ranged `GET`); rejects if
   `content-type` is present and not `image/*`, or `content-length` > 8 MB.
   Lenient when headers are absent — the ingest script re-validates on fetch.

On success: creates branch `screenshot/<buildId>-<rand>`, commits a **text-only**
sidecar to `SCREENSHOT_DIR`, opens a PR titled `Screenshot: <buildId> (by <author>)`,
returns `{ ok: true, url }`.

### `kind: "blueprint"` (default)
Unchanged from before, plus an optional `imageUrl` (a reference shot). The URL is
recorded in the PR metadata sidecar and PR body; it is **not** localized unless the
build is later promoted (same vet-first path). The `.blueprint` file is still
validated as real gzip, ≤8 MB.

---

## The ingest script

`node site/scripts/ingest-screenshots.mjs [--dry]` (run from repo root; resolves
`sharp` from `site/node_modules`).

For each `community-submissions/screenshots/*.json`:
1. Fetch `sourceUrl` (hard 8 MB cap; reject non-image `content-type`).
2. `sharp`: `.rotate()` (honor EXIF orientation) → `.resize(1920, 1440, { fit:
   "cover", position: "centre", withoutEnlargement: true })` → `.png({
   compressionLevel: 9 })`. Re-encoding **drops all EXIF/GPS**. `withoutEnlargement`
   means small sources stay smaller than 4:3 — fine, the site containers are
   `object-fit: cover`.
3. Write `site/public/builds/<buildId>/<n>.png`, `<n>` = next free index (max of
   existing published entries + files on disk, +1) → numbering follows submission
   order and re-runs never clobber.
4. Append to `credits.json` `builds[buildId]`.
5. Delete the sidecar.

`--dry` reports what it would do and writes nothing. Failures leave the sidecar in
place for retry. Run it **on the submission PR's branch** so image + credit merge
atomically.

### `credits.json` shape
```jsonc
{
  "_comment": "…",
  "builds": {
    "watchtower": [
      {
        "author": "@handle",
        "file": "1.png",                       // relative to /builds/watchtower/
        "sourceUrl": "https://…",              // provenance
        "submittedAt": "2026-…",               // from the sidecar
        "publishedAt": "2026-…"                // when ingested
      }
    ]
  }
}
```
The site renders only entries that have a `file`, in array order (first-submitted
first). Unlimited entries per build; there is **no hard count cap** — curation
happens at review, and the daily cap + burst limit handle floods.

---

## Limits & config (all the knobs)

| Knob | Value | Where |
|---|---|---|
| Source image size cap | 8 MB | `MAX_IMAGE_BYTES` (worker), `MAX_BYTES` (ingest) |
| Blueprint file cap | 8 MB | `MAX_FILE_BYTES` (worker) |
| Output crop / max edge | 1920×1440 (4:3), ≤2560px, no upscale | `TARGET_W/H` (ingest) |
| Daily cap per (build, IP) | 2 / UTC day | `SCREENSHOTS_PER_BUILD_PER_DAY` (worker) + `SCREENSHOT_KV` |
| Burst limit per IP | 5 / 60 s | `SUBMIT_RL` ratelimit binding |
| Field caps | name 60, author 40, desc 500, buildId 80, imageUrl 1000 | `LIMITS` (worker) |
| Turnstile | enabled | `TURNSTILE_SECRET` (worker secret) + `TURNSTILE_SITEKEY` in the pages |
| Blocklist | KV key `blocklist` = `{ "hosts": [], "ips": [] }` | `SCREENSHOT_KV`; edit without redeploy |
| Vars | `ALLOWED_ORIGIN`, `GITHUB_REPO`, `SUBMISSION_DIR`, `SCREENSHOT_DIR`, `SITE_MANIFEST_URL` | `wrangler.toml` |
| Endpoint | `https://mc3dprint-submit.patrick-4bc.workers.dev` | hardcoded in the pages |

Both KV features (daily cap + blocklist) **degrade gracefully** if `SCREENSHOT_KV`
is unbound — useful for local dev.

---

## Deployment

Site auto-deploys from `main` (Pages). The Worker does **not** — deploy it
explicitly after changing `worker/`:

```bash
cd worker && npx wrangler deploy
```

`wrangler` is authed in the maintainer's dev shell (`workers` + `workers_kv`
write), so this can be done without the CF dashboard. The `SCREENSHOT_KV` binding
+ id live in `wrangler.toml`. Secrets `GITHUB_TOKEN` + `TURNSTILE_SECRET` persist
across deploys. Blocklist edits:

```bash
npx wrangler kv key put --binding SCREENSHOT_KV blocklist \
  '{"hosts":["bad-host.example"],"ips":["1.2.3.4"]}'
```

---

## Extension points

Where to reach when changing the feature:

- **Guidebook acknowledgements** — attribution is website-only for now. To also
  thank contributors in the in-game Patchouli book, generate names into
  `…/patchouli_books/guide/en_us/entries/about/acknowledgements.json` from
  `credits.json` (deferred; would need a generator + a doc-surface sync).
- **Auto-publish (merge Action)** — replace the manual ingest step with a
  merge-triggered GitHub Action that runs the same script. Only do this once the
  human-in-the-loop gate is proven unnecessary; the script already does all the
  work, so it's mostly wiring + a size/type guard in CI.
- **Multiple hosts / allowlist** — currently any `https` image URL is accepted and
  validated on fetch. If abuse appears, add an allowlist in `validImageUrl` /
  `probeImage` instead of relying only on the blocklist.
- **Per-build image cap or a "primary" pick** — today it's unlimited + ordered.
  If a build accretes too many, add a soft cap or a `"primary": true` flag in
  `credits.json` that the gallery thumbnail honors.
- **Richer credit display** — e.g. a global contributors page built from
  `credits.json`, or lightbox/captions on the build page.
- **Alt text / accessibility** — `alt` is currently generic; could carry a
  submitter-provided caption (add a field to the sidecar + form).
