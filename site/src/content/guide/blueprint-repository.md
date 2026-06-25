---
title: "The Blueprint Repository"
category: "Machines"
order: 5
summary: "A library block that catalogues your Blueprint Discs and re-burns fresh copies of any build on demand."
---

The Blueprint Repository is your blueprint library. It starts empty — you fill it by depositing the discs you find and scan, then re-burn any catalogued build onto a fresh disc whenever you need a copy. Right-click to open the browser: catalogued builds on the left, details on the right.

## Depositing discs

Drop a written Blueprint Disc into the input slot and press `Deposit Disc`. The build is catalogued and the disc consumed. Once catalogued, a build stays forever and you can re-burn as many copies as you like.

**Duplicates recycle.** Deposit a disc the library already has and it's wiped and handed back as a `Blank Blueprint Disc` — except locked discs, which are protected and left untouched.

## STL to GCODE

To burn a copy: select a build, drop a `Blank Blueprint Disc` into the input slot, and press `STL to GCODE` for a fresh written disc. Burned copies keep the original's official or player-scan status, so the [Resin](/guide/resins-overview/) anti-dupe rule still holds.

## Shared or personal

By default a repository is a **shared, world-level** library — everyone's deposits pool, every repository block sees the same catalogue, and breaking a block never loses it.

> Admins can set `blueprintRepositoryIsShared` to `false` for personal, per-player libraries.
