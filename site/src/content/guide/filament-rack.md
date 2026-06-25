---
title: "The Filament Rack"
category: "Machines"
order: 3
summary: "A bookshelf-style rack that stores eight Filament Spools and doubles as a backup FU reservoir for machines."
---

Filament Spools don't stack, so a drawer full of them clutters fast. The Filament Rack is a bookshelf-style shelf with 8 slots (2×4) built to hold them.

## Storing spools

- Right-click the rack with a spool in hand to shelve it.
- Right-click with an empty hand to pop the last one back out — last in, first out.

Shelved spools render on the front, each tinted with its tier color, and the shelf visibly fills in as you stock it. It also emits a comparator signal scaled to how full it is.

## A backup reservoir

A rack is also a Filament Unit reservoir. When a printer or fabricator runs out of its own docked spools mid-print, it automatically pulls FU from a rack that's touching it — or one wired to it with an [MC3D Cable](/guide/mc3d-cable/).

> Docked spools always feed first; the rack is the reserve.

Down-only tier rules still apply: a higher-tier spool can pay lower-tier costs, never the reverse. Keep one next to every printer as a backup tank and your prints won't stall halfway.
