# mc3dprint-submit (Cloudflare Worker)

The backend for the website's **Submit a Build** page. It takes a `.blueprint`
upload + metadata, validates it, and opens a pull request on this repo so anyone
can contribute a build with **no GitHub account and no git**. The PR is the
intake; a maintainer reviews it and the blueprint-preview Action renders it.

Lives at a `*.workers.dev` URL (the website's form POSTs there). No custom domain
is needed because `mc3dprint.dev` DNS isn't on Cloudflare.

## One-time setup

1. **Install deps** (uses `npx`, nothing global):
   ```bash
   cd worker && npm install
   ```

2. **Authenticate wrangler to Cloudflare** (opens a browser):
   ```bash
   npx wrangler login
   ```

3. **Create the GitHub bot token.** A *fine-grained* personal access token, scoped
   to **only** `PGMacDesign/MC3DPrint`, with:
   - Repository permissions → **Contents: Read and write**
   - Repository permissions → **Pull requests: Read and write**

   Then store it as a secret (you'll paste it; it's never committed):
   ```bash
   npx wrangler secret put GITHUB_TOKEN
   ```

4. **Deploy:**
   ```bash
   npx wrangler deploy
   ```
   Copy the printed `https://mc3dprint-submit.<subdomain>.workers.dev` URL into
   the website: set `SUBMIT_ENDPOINT` in `site/src/pages/submit.astro`.

## Optional: captcha (recommended before any real launch)

The endpoint is public, so once the project is known, add Cloudflare **Turnstile**
to stop spam PRs:

1. Create a Turnstile widget in the Cloudflare dashboard (domain `mc3dprint.dev`).
2. Put the **site key** into `TURNSTILE_SITEKEY` in `site/src/pages/submit.astro`.
3. Store the **secret key**: `npx wrangler secret put TURNSTILE_SECRET`, then redeploy.

When `TURNSTILE_SECRET` is set, the Worker requires a valid captcha token; until
then it runs without one (fine pre-launch — every PR is reviewed anyway).

## Screenshot submissions (the "screenshot bounty")

> Full design & reference: [`docs/screenshot-bounty.md`](../docs/screenshot-bounty.md).
> This section is the ops summary.

The same Worker also backs the **"Add a screenshot"** control on each `/builds/<id>/`
page. A visitor pastes a **direct image URL** (never an upload) for an existing curated
build; the Worker validates it, enforces the daily cap, and opens a PR that contains
**only a small text sidecar** in `community-submissions/screenshots/` — no image bytes.

Publishing an approved screenshot is a deliberate, human-gated step:

1. Review the PR — open the image URL in the sidecar/PR body; confirm it's a real,
   appropriate shot of that build.
2. On the PR branch, run the ingest script (from repo root):
   ```bash
   node site/scripts/ingest-screenshots.mjs        # or --dry to preview
   ```
   It fetches each pending image, crops to 4:3 + downscales ≤2560px + re-encodes PNG
   (stripping all EXIF/GPS), writes `site/public/builds/<id>/<n>.png` in submission
   order, appends a credited entry to `site/public/builds/credits.json`, and deletes
   the sidecar.
3. Eyeball the downloaded image, commit, and merge — image + credit land together.

The same reference-shot lifecycle applies to a `/submit` new-build PR: its `imageUrl`
rides in the metadata and is only ingested if/when you promote the build.

### KV (daily cap + blocklist)

Both features degrade gracefully without KV, but for production create the namespace
and uncomment the binding in `wrangler.toml`:

```bash
cd worker && npx wrangler kv namespace create SCREENSHOT_KV
# paste the printed id into wrangler.toml, uncomment [[kv_namespaces]], redeploy
```

- **Daily cap:** 2 screenshots per build per IP per UTC day (counter keys auto-expire).
- **Blocklist** (ban a bad actor/host without a redeploy):
  ```bash
  npx wrangler kv key put --binding SCREENSHOT_KV blocklist \
    '{"hosts":["bad-host.example"],"ips":["1.2.3.4"]}'
  ```

## Local dev

```bash
npx wrangler dev          # runs the Worker locally
```
Put secrets for local runs in `worker/.dev.vars` (gitignored):
```
GITHUB_TOKEN=github_pat_...
```
