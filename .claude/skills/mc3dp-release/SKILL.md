---
name: mc3dp-release
description: >-
  Draft a new GitHub release for the MC3DPrint mod: verify the version, write
  curated player-facing release notes from the commits since the last release,
  and stage the release as a DRAFT for the user to review. This skill NEVER
  publishes on its own. Use it whenever the user wants to cut, prep, stage, tag,
  or draft a release, or asks for release notes, in the MC3DPrint repo, e.g.
  "cut a release", "draft the 1.2.0 release", "prep the next release", "write
  release notes", "stage a release for me to review", "ship a new version" (even
  if they don't name the exact version). MC3DPrint repo only. Publishing is
  always the user's own action; this skill stops at the draft and hands off.
---

# MC3DPrint: Draft a Release (never publish)

Stage a GitHub Release as a **draft** and hand it to the user. Your deliverables are
(1) accurate, human-readable release notes and (2) a draft release pinned to the right
commit. You stop there. Publishing is the user's action, taken deliberately after they
have read the notes.

Work only in the MC3DPrint repo. If you are not there, stop and say so.

## Why this is draft-only: read this before touching anything

Publishing a release here is not a quiet metadata flip. `.github/workflows/release.yml`
fires on release **publish** (not on draft) and does two irreversible, outward-facing things:

1. Builds all eight shippable jars via `scripts/build-all.sh` (seven NeoForge Stonecutter
   nodes from `main` + Forge 1.20.1 from the `legacy/1.20.1` branch) and attaches them to
   the release.
2. **Auto-publishes to CurseForge for real users**, the soak-tested targets, Forge 1.20.1
   and NeoForge 1.21.1 (`curseforge-id: 1587177`). The **release body becomes the CurseForge
   changelog verbatim.** The forward NeoForge jars (1.21.8 → 26.2) attach to the GitHub
   release but are deliberately NOT auto-published to CurseForge until each passes its own
   in-world soak.

So the notes are public copy the moment the release goes live, and a mistake reaches players.
A **draft** triggers none of this, which is exactly why this skill stops at a draft. The
user reviews, then publishes when they are ready.

**The publish gate is absolute.** Even if the user says "and publish it" or "go ahead and
release" in the same breath, do NOT publish as part of running this skill. Create the draft,
show them what publishing will do, and let them come back and tell you to publish as a
separate, deliberate step (see "If the user later asks to publish"). This is not you being
timid; it is that the cost of publishing the wrong thing is a bad changelog and bad jars in
front of every player, and the cost of waiting is thirty seconds.

## Conventions to match

- **Tags are bare semver, no `v`.** Existing tags: `1.1.0`, `1.0.0`, `0.10.0`. The workflow
  strips a leading `v` if present, but every real tag is bare, so use `1.2.0`, not `v1.2.0`.
- **`main` and `legacy/1.20.1` are the only long-lived branches.** The version must match on
  both (see step 2).
- **Release titles are `<version> - <headline feature>`**, e.g. `1.1.0 - Filament Tier Item
  Sorter`. These are full releases now, not the old `Beta N` line. Pick the headline from the
  biggest user-facing change, or ask the user if it is not obvious.
- **No em dashes anywhere** in the notes, title, or tag (owner's standing rule; the notes are
  public copy). Use a comma, colon, semicolon, period, or parentheses.
- **No Claude/Anthropic attribution** in the tag, title, notes, or any commit this skill makes.

## Workflow

### 1. Preflight: the draft must point at pushed commits

The workflow checks out the tag from `origin` and builds `legacy/1.20.1` from that branch's
pushed tip, so anything unpushed will simply not be in the release.

- Confirm you are on `main` with a clean tree: `git status -sb`.
- Confirm `main` is pushed: `git rev-list --left-right --count origin/main...main` is `0  0`.
- Confirm `legacy/1.20.1` is up to date with its remote too (the Forge jar builds from it):
  `git fetch origin` then compare `git rev-parse origin/legacy/1.20.1` against your local ref.

If anything is unpushed or dirty, resolve it (commit + push via the normal PR flow, or ask)
before drafting. Do not tag on top of unpushed work.

### 2. Determine the version, and verify it on both lines

The jars carry `mod_version` from `gradle.properties`, built separately on each line, so a
mismatch ships two different version numbers under one tag.

- Extract the bare value on each line (the RHS, not the whole `mod_version=1.2.0` line):

  ```shell
  main_ver=$(sed -n 's/^mod_version=//p' gradle.properties)
  legacy_ver=$(git show origin/legacy/1.20.1:gradle.properties | sed -n 's/^mod_version=//p')
  ```

- Validate before trusting either: each must be exactly one non-empty bare-semver value
  (`MAJOR.MINOR.PATCH`, e.g. `1.2.0`, no `v`, no suffix). If `main_ver` and `legacy_ver` differ,
  either is empty/missing/duplicated/malformed, or the user named a different version, **stop and
  surface it** rather than guessing or silently bumping. A bump is its own PR on both branches,
  not something to fold into drafting a release. The validated, agreed value is the tag.
- Find the previous tag for the notes range. Use the **latest published release**, not the newest
  tag by date, because the repo also carries tooling tags (e.g. `v0.10.0-neoforge-1.21.1`) that
  would poison the commit range:

  ```shell
  prev_tag=$(gh release list --exclude-drafts --exclude-pre-releases --limit 20 \
    --json tagName,isLatest --jq 'map(select(.isLatest)) | .[0].tagName')
  ```

  Sanity-check that `prev_tag` is a bare-semver release older than the version you are cutting, and
  that it is an ancestor of `main` (`git merge-base --is-ancestor "$prev_tag" HEAD`). If it is not,
  stop and ask rather than generating notes over the wrong range.

### 3. Write the release notes: curated prose, not a commit dump

The bundled script buckets the commits since the last tag by Conventional-Commit type. Treat
its output as **raw material**, not the finished notes:

```shell
.claude/skills/mc3dp-release/scripts/release_notes.sh <prev-tag> <new-tag> > /tmp/mc3dp-raw.md
```

Then rewrite it into notes a player would actually want to read. The format that works here
(see `references/notes-example.md` for a full worked example from 1.1.0):

- **`## New`**: each headline feature as a bold lead sentence plus a short plain-language
  explanation of what it does and why it is nice. Fold the related commits into one entry;
  nobody wants ten `feat(sorter):` lines. Explain mechanics only where they help a player
  (e.g. a search syntax, a config knob).
- **`## Fixed`**: user-visible bugs. When the underlying story is interesting (a big perf win,
  a subtle correctness fix), a sentence or two of the "why" earns its place; otherwise keep it
  to the symptom and the fix.
- **`## Build`**: version-pin / toolchain notes that a pack maintainer cares about.
- **`## Downloads`**: a loader x Minecraft table of what is attached, then the CurseForge scope
  note: CurseForge carries the soak-tested targets (Forge 1.20.1, NeoForge 1.21.1); the forward
  NeoForge jars are attached to the GitHub release but have not had a full in-world soak.

Drop pure noise (chore churn, "fix typo", revert-of-revert). Verify any claim you make against
the actual diff before writing it, the same as any doc surface. Write the notes to a file, e.g.
`/tmp/mc3dp-notes-<version>.md`.

### 4. Create the DRAFT release

First fail fast if the tag or a release already claims that name. `gh release create` against an
existing tag attaches the release to **that tag's commit**, not your `HEAD`, so a re-run or a leftover
tag would silently pin the release to the wrong code:

```shell
git ls-remote --exit-code --tags origin "refs/tags/<tag>" && echo "TAG EXISTS - stop" && exit 1
gh release view <tag> >/dev/null 2>&1 && echo "RELEASE EXISTS - stop" && exit 1
```

If either exists, stop and surface it rather than drafting on top of it.

Then create it pinned to the current `main` HEAD. Get the target right the first time, because editing
`--target` after creation reshuffles the release's internal identifier, which is avoidable churn:

```shell
gh release create <tag> \
  --draft \
  --target "$(git rev-parse HEAD)" \
  --title "<version> - <headline feature>" \
  --notes-file /tmp/mc3dp-notes-<version>.md
```

`--draft` is the whole point. Never add `--latest`, and never drop `--draft`.

Verify the draft actually pinned to `HEAD` before reporting it as ready:

```shell
test "$(gh release view <tag> --json targetCommitish --jq .targetCommitish)" = "$(git rev-parse HEAD)"
```

If that does not match, the draft is pinned to the wrong commit; delete it and investigate rather
than handing the user a mispinned release.

### 5. Stop and hand off

Report to the user:

- the draft release URL,
- the tag, title, and the version you verified on both lines,
- a one- or two-line summary of the notes,
- and a plain statement of what publishing will do: build and attach all eight jars, and
  auto-publish Forge 1.20.1 + NeoForge 1.21.1 to CurseForge with these notes as the changelog.

Then stop. Do not publish. Invite them to review the draft and tell you when (or whether) to
publish.

## If the user later asks to publish

When the user, having reviewed the draft, explicitly tells you to publish it, treat that as a
real request, but still confirm the blast radius first, in this message, before doing it:
restate that this pushes Forge 1.20.1 + NeoForge 1.21.1 live to CurseForge for all users with
the current notes as the changelog, and confirm the notes are final. Only after they confirm in
that exchange, publish:

```shell
gh release edit <tag> --draft=false --latest
```

Then verify what actually happened, and do not report success from a green checkmark alone. Grab the
run that this publish triggered, not merely the newest run:

```shell
run_id=$(gh run list --workflow=release.yml --event=release --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$run_id"
```

Two things the workflow does NOT make obvious:

- **The CurseForge steps are conditional and best-effort.** They run only when the `CF_TOKEN` secret
  is set, and they carry `continue-on-error: true`, so a skipped or failed CurseForge upload still
  lets the overall run go green. A successful run is therefore NOT proof that CurseForge published.
  Inspect the two "Publish ... to CurseForge" steps in *this* run's logs
  (`gh run view "$run_id" --log`) and confirm each actually uploaded rather than skipping or erroring.
- **Jar attachment is the reliable signal.** Confirm all eight jars attached to the release
  (`gh release view <tag> --json assets --jq '.assets | length'` should be at least 8, plus the style
  packs).

Report the release URL, the CI run status, the jar-attachment count, and the **actual** CurseForge
outcome per step (published / skipped / errored), not an assumption.

Never publish as a reflex, with `--auto`, or as a probe. If you are ever unsure whether the user
means "publish now" versus "get it ready", assume the latter and ask.

## Report back

For a normal (draft) run: the tag + title, the draft URL, the version verified on both lines,
and a one-line notes summary. Make it unmistakable that nothing is public yet and publishing is
their call.
