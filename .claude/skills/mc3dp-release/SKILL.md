---
name: mc3dp-release
description: >-
  Cut and publish a new GitHub release for the MC3DPrint Forge mod — create the
  version tag, auto-generate grouped release notes from the commits since the
  last release, and publish the GitHub Release (which triggers the CI that builds
  and attaches the mod jar). Use this whenever the user wants to cut, publish,
  tag, or ship a release in the MC3DPrint repo — e.g. "cut a release", "do a
  0.9.0 release", "release the mod", "publish the next beta", "tag and release",
  "ship a new version" — even if they don't name the exact version. MC3DPrint
  repo only.
---

# MC3DPrint — Cut a GitHub Release

Publish a new GitHub Release for this mod. Publishing is the *trigger*: the
`.github/workflows/release.yml` Action fires on release publish, builds the
production jar with `-Pmod_version=<tag>`, and attaches `mc3dprint-<ver>.jar` to
the release. **This skill does NOT build the jar** — CI does. Your job is to tag,
write good notes, and publish; then confirm CI took over.

Work only in the MC3DPrint repo. If you're not there, stop and say so.

## How releases work here (conventions — match them)

- **Tags are bare semver, no `v` prefix.** Existing: `0.8.0`, `0.7.0`, … The
  Action tolerates a leading `v` (it strips it), but every real tag is bare — so
  use `0.9.0`, not `v0.9.0`.
- **Releases are pre-releases** (the mod is pre-launch). Default to `--prerelease`
  unless the user says this is the real launch.
- **Display titles follow an `Alpha N` → `Beta N` sequence** (latest was
  "Beta 3"), separate from the version number. Don't guess the next title —
  **ask the user** for it (and default to the bare version if they don't care).
- **No Claude/Anthropic attribution** anywhere in the tag, title, or notes.
- The jar is built by CI from the tag, so `gradle.properties` doesn't strictly
  need bumping for the release to be correct — but keep it in sync (see step 3).

## Workflow

### 1. Preflight (the tag must point at a pushed commit)
The Action checks out the tag, so the commit you tag has to be on `origin`.
- Confirm a clean tree and that you're on `main`: `git status -sb`.
- Confirm HEAD is pushed: `git rev-list --left-right --count origin/main...HEAD`
  should be `0  0`. If anything is unpushed/uncommitted, resolve it (commit +
  push, or ask) before tagging.

### 2. Pick the new version + title
- Find the previous release tag: `git tag --sort=-creatordate | head -1`
  (or `gh release view --json tagName -q .tagName`).
- Decide the **new bare-semver version** (e.g. `0.9.0`). If the user gave one,
  use it; otherwise propose the next semver bump and confirm.
- Ask for the **display title** (e.g. "Beta 4"), or default it to the version.

### 3. Sync `gradle.properties` (optional but tidy)
If `mod_version` in `gradle.properties` doesn't already equal the new version,
bump it, then commit + push so HEAD still matches what you're about to tag:
```
# edit mod_version=<new-version> in gradle.properties
git commit -am "chore: bump mod_version to <new-version>"
git push origin main
```
Skip if it's already in sync or the user doesn't want the bump.

### 4. Generate the release notes
Use the bundled script — it buckets commits by Conventional-Commit type and adds
a compare link:
```
.claude/skills/mc3dp-release/scripts/release_notes.sh <prev-tag> <new-tag> > /tmp/mc3dp-notes.md
```
Read `/tmp/mc3dp-notes.md`, then **tidy it for humans**: lead with a one or two
sentence summary of the headline changes, drop or merge noise (trivial chores,
revert-churn, "fix typo"), and keep the grouped sections. The script is a
starting point, not the final copy.

### 5. Create + publish the release (this triggers CI)
`gh release create` creates the tag and the release in one step:
```
gh release create <new-tag> \
  --target main \
  --title "<display-title>" \
  --notes-file /tmp/mc3dp-notes.md \
  --prerelease
```
Drop `--prerelease` only for a real launch. `--target main` pins the tag to the
current `main` HEAD.

### 6. Confirm CI took over
Publishing fires `release.yml` (build + attach jar). Confirm it started and,
optionally, watch it:
```
gh run list --workflow="Build & attach mod jar to release" --limit 1
gh run watch <run-id>        # optional — wait for the jar to attach
gh release view <new-tag>    # verify mc3dprint-<ver>.jar is attached once CI finishes
```
Report the release URL and whether the jar attached.

## Report back
Give the user: the new tag + title, the release URL, a one-line notes summary,
and the CI/jar status (kicked off / attached / failed).

---

## TBD / future (fill in as we add steps)

Placeholders for release-automation we want later — not implemented yet:

- [ ] **CHANGELOG.md** — prepend the generated notes to a repo `CHANGELOG.md` and
      commit it as part of the release.
- [ ] **Modrinth / CurseForge publish** — push the built jar to the mod
      distribution platforms (their APIs / `gh`-style CLIs) after the GitHub
      release is live.
- [ ] **Patchouli/docs version stamp** — bump any in-repo version references
      (guide book, docs/ROADMAP header) alongside the tag.
- [ ] **Announcement draft** — generate a short release announcement (Discord /
      forum) from the notes.
- [ ] _(add more here)_
