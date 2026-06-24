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

## Local dev

```bash
npx wrangler dev          # runs the Worker locally
```
Put secrets for local runs in `worker/.dev.vars` (gitignored):
```
GITHUB_TOKEN=github_pat_...
```
