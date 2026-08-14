---
title: "The Blueprint Repository"
category: "Machines"
order: 5
summary: "A library block that catalogues your Blueprint Discs and re-burns fresh copies of any build on demand."
---

The Blueprint Repository is your blueprint library. It starts empty. You fill it by depositing the discs you find and scan, then re-burn any catalogued build onto a fresh disc whenever you need a copy. Right-click to open the browser: catalogued builds on the left, details on the right.

## Depositing discs

Drop a written Blueprint Disc into the input slot and press `Deposit Disc`. The build is catalogued and the disc consumed. Once catalogued you can re-burn as many copies as you like, and it stays until somebody [removes it](#removing-a-scan) on purpose.

**Duplicates recycle.** Deposit a disc the library already has and it's wiped and handed back as a `Blank Blueprint Disc`, except locked discs, which are protected and left untouched. The catalogued entry itself is never touched by a re-deposit: it keeps its name and its original depositor.

## STL to GCODE

To burn a copy: select a build, drop a `Blank Blueprint Disc` into the input slot, and press `STL to GCODE` for a fresh written disc. Burned copies keep the original's official or player-scan status, so the [Resin](/guide/resins-overview/) anti-dupe rule still holds.

## Renaming your scans

The Structure Scanner names what it captures after where it was standing: `Scan @ 307,70,10`. Fine for one scan, useless once you've deposited a handful.

Select a scan in the library and a **rename** field appears under its details. Type a real name, press `Set` (or Enter), done. The new title sticks in the library **and** in the stored blueprint, so a disc you burn later carries it too.

Only **player scans** can be renamed, and only by **whoever deposited them** (or an operator). Official curated builds keep their shipped names: they're content, and on a shared library one player's edit would retitle the build for everyone on the server. Renaming rewrites the stored blueprint, not just the row, so the same ownership rule that governs removal applies here. Entries catalogued before depositors were tracked have no recorded owner, which makes them operator-only.

Names are capped at 48 characters, and formatting codes and line breaks are stripped.

## Removing a scan

Deposited something by mistake? Select it and press **Del** twice. The button arms on the first click and reads **Sure?** for five seconds, so a stray click can't clear a build.

Who can remove what:

- **Whoever deposited it** can remove it.
- **Operators** can remove anything.
- **Official curated builds can't be removed** at all, by anyone.

That split exists because the default library is shared and world-level. An open Delete button would let any player wipe builds other people contributed, while an operators-only one would leave you unable to clear your own mis-scan on an ordinary server.

Removal takes the **catalogue entry only**, never the blueprint file. A disc you burned before removing still prints, and re-depositing it puts the entry back, so a mistake is recoverable as long as a copy exists somewhere.

> Entries catalogued before this shipped have no recorded depositor, so they're operator-only to remove.

## Shared or personal

By default a repository is a **shared, world-level** library: everyone's deposits pool, every repository block sees the same catalogue, and breaking a block never loses it.

> Admins can set `blueprintRepositoryIsShared` to `false` for personal, per-player libraries.
