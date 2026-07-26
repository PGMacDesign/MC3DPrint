---
title: "Scanning a build"
category: "Basics"
order: 1
summary: "Capture any structure with the Structure Scanner and save it to a portable, printable Blueprint Disc."
---

The Structure Scanner turns any build you've made into a reusable blueprint. Crafted from quartz (its "eyes"), glass to focus the lens, and an iron frame, it's your entry point into the whole print loop.

## How to scan

1. Right-click the **first corner** block of your build.
2. Right-click the **opposite corner** to set the bounding box.
3. With a **Blank Blueprint Disc** anywhere in your inventory, right-click the air to capture the scan.

The result is a written [Blueprint Disc](/guide/blueprints/): portable, tradeable, and printable on any machine of the right [tier](/guide/printer-tiers/). Blank discs craft two at a time, and each disc holds exactly one scan.

## Clear the area first

> The printer never overwrites existing blocks: it only fills empty space.

If a position is already occupied, the printer **pauses** there (its status reads *Obstructed*) instead of overwriting or skipping it, then resumes automatically once you clear the block out of the way. Clear the print area before starting so the job runs straight through.

## Importing existing files

Server operators don't have to scan everything by hand. You can pull WorldEdit `.schem` files, Litematica `.litematic` files, and Create or vanilla `.nbt` files straight onto discs with `/mc3dprint import`. From there they behave like any other written disc: print them anywhere a printer can reach.

Once you've got a written disc, head to [Blueprint Discs & files](/guide/blueprints/) to learn how tiers and print costs work.
