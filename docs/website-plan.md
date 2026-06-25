# MC3DPrint Website — Build-Out Plan

_Goal: make [mc3dprint.dev](https://mc3dprint.dev) **fully functional** (zero "coming
soon" anywhere) and **looking a lot better**, on-brand with the mod. **Evolve** the
current site — don't rebuild it. Plan locked via a grill session; implementation
starts on Patrick's approval._

The companion **[shot list](website-shot-list.md)** enumerates every screenshot/GIF
to capture (hero, GUIs, install, and all 134 per-build photos).

---

## Decisions (locked)

| # | Decision | Resolution |
|---|---|---|
| Imagery | How visuals are sourced | **Hybrid** — build on generated assets now (GUI/item textures, 3D renders); Patrick's in-game screenshots/GIFs slot into defined holes as they land. High capture appetite (incl. all 134 builds). |
| Palette | Visual identity | **Keep amber** (primary warm accent). **Drop teal → cyan** (the mod's "magic glow"). Add **machined-grey / dark-console** surfaces + the **per-tier accent ramp** (T1→T8) as a motif. |
| Guide | Depth + relationship to Patchouli | **Primary web guide**, polished but proportionate (most players learn in-game). Content **adapted from the 30 Patchouli entries**; **mirror the Patchouli categories**. Tone: for Minecraft players. |
| Scope | Net-new pages | Finish the existing pages + **add an FAQ** (from 7 Patchouli FAQ entries). **Link out** for changelog (GitHub Releases). **Defer** the blocks/items wiki. |
| Download | Distribution | **GitHub Releases + CurseForge** buttons. (CurseForge auto-publish CI is already wired & shipping.) |
| Gallery | Per-build imagery | **Per-build photos with graceful render fallback** + lightweight **build detail pages** (static from the manifest; each build gets a shareable URL). |
| Landing | Ambition | **Evolve, don't redesign.** Keep the clean layout + the hero printer animation (polish it, revertible). Palette tweak (teal→cyan) + logo in nav. Media-rich energy goes **below the fold + other pages**. |

---

## Design system

Adopt the mod's identity (`docs/VISUAL-REVAMP-BRIEF.md`) as the web palette, keeping amber:

- **Surfaces** — dark console: `#0c0d12` / `#12141b` bg, `#171a23` panels, `#1d212c` raised; machined-grey accents (`#dce1e6 #bcc4cc #9aa3ad #6e767f`) for "hardware" elements echoing the in-game GUIs.
- **Amber (primary, KEEP)** — `#f4a23c` / hi `#ffc06a`. Signature warm accent, CTAs, the filament motif.
- **Cyan (secondary, replaces teal)** — `#5cc8ff` / hi `#bfe9ff`. The "magic glow" — links, tech accents, the hero glow's cool counterpart.
- **Tier ramp (motif)** — T1 `#b6bcc8` · T2 `#6fd07a` · T3 `#5bb6ff` · T4 `#c47bff` · T5 `#ffd24a` · T6 `#ff8a3c` · T7 `#ff5d6c` · T8 `#57f5e0`. Used for gallery tier chips, build detail accents, and any T1→T8 progression.
- **Type** — keep **Outfit**. **Logo** (`site/public/brand/logo.svg`) replaces the nav's gradient square; favicon/apple-touch already rendered in `site/public/brand/`.

Concretely in code: update `site/src/styles/global.css` tokens (`--teal*` → `--cyan*`, add machined-grey + tier vars), wire the favicon/og into `Base.astro`, swap the `Nav.astro` brand mark for the logo.

## Imagery architecture (the fallback system)

So the site looks complete immediately and improves as photos arrive:

- **Per build:** show `site/public/builds/<id>.png` (real screenshot) **if it exists**, else the **3D render** (generated from the viewer pipeline), else the **styled tier card**. Astro globs the folder at build time and maps by build id — dropping in a photo is the only step; no code change.
- **Hero:** a **scan→print GIF/video** slot with a generated-render/animation fallback until captured.
- **Feature sections / guide steps:** real screenshots where available, generated GUI composites otherwise.
- Captures are **never required** — an un-photographed build never looks broken.

## Page-by-page

- **Landing (`index.astro`)** — *evolve.* Keep layout + hero animation (polish, revertible); teal→cyan; logo in nav. Below the fold: feature cards backed by real screenshots, a **"See it in action"** GIF block, a **featured-builds strip** pulling from the gallery, the submission CTA.
- **Get Started (`getting-started.astro`)** — remove the "Modrinth/CurseForge coming soon" line; add **GitHub + CurseForge** download buttons (CF → `https://www.curseforge.com/minecraft/mc-mods/mc3dprint`, live on approval); add install screenshots (mods folder, mod list).
- **Guide (`guide/`)** — build the real thing. Restructure to mirror Patchouli: **Basics / Machines / Multiblocks / Resins / FAQ**. Each topic becomes a Markdown content-collection entry with steps + screenshots + GUI shots + relevant build renders, adapted from the 30 Patchouli entries. Kills all "Coming soon" cards.
- **Gallery (`gallery.astro`)** — keep search/filter; cards show photo-or-render; clicking → **build detail page** (`/builds/<id>`, static-generated from the manifest: hero image, stats, tier chip, "Open in 3D viewer").
- **Viewer (`/viewer`)** — unchanged (already solid); inherits the palette where it shares tokens.
- **Submit (`submit.astro`)** — already functional; apply palette/logo; add one screenshot of the flow.
- **About (`about.astro`)** — light polish, palette, logo.
- **FAQ (new)** — built from the 7 Patchouli FAQ entries (auto-vs-manual, getting blueprints, mixed structures, moving a print, not-printable, pauses, upgrades).

## Technical notes

- Guide + FAQ as **Astro content collections** (Markdown) for easy authoring/maintenance.
- Build detail pages via `getStaticPaths` over the manifest.
- Wire **favicon / apple-touch / OG image** (use `logo-1024.png` or a dedicated OG card) in `Base.astro`.
- Add a **sitemap** (`@astrojs/sitemap`); canonical/OG meta already present.
- Deploy path unchanged (`pages.yml` builds Astro + injects blueprints/manifest).

## Work sequence (single push, but ordered so it's visible fast)

1. **Design system + landing evolve** — palette tokens, logo/favicon, hero polish, below-the-fold sections (with fallbacks). *Visible win on day one.*
2. **Page build-out** — Guide content (the big lift), Gallery detail pages + fallback, FAQ, Get Started download/CurseForge, Submit/About polish. *Can fan out across parallel agents per page/section.*
3. **Imagery integration** — drop in captures as they arrive; fallbacks mean no blocking.

## Dependencies on Patrick

- **Captures** — see the **[shot list](website-shot-list.md)** (hero GIF is the single highest-impact item).
- **CurseForge approval** — the CF download button + any CF links go live once the project clears moderation.

## Out of scope (deferred)

Blocks/items mini-wiki; a bespoke changelog page (link to GitHub Releases); Modrinth (the CI makes adding it a one-liner later).
