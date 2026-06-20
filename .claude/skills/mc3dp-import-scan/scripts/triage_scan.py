#!/usr/bin/env python3
"""Pre-flight triage for a player-scanned MC3DPrint .blueprint, BEFORE writing any
Java. Decodes the GZIP-NBT and reports everything the import decision needs:

  - name, dimensions, block count, estimated min printer tier
  - the full palette (so you can eyeball which blocks may need FU values)
  - scaffolding cells + count + positions  (these get stripped on import)
  - block-entities carrying container "Items" (these get stripped on import)
  - the distinct block-id list (the set to sanity-check against the FU economy)

Usage:  python3 triage_scan.py <path/to/scan.blueprint>

Pure stdlib; safe to run on any .blueprint (curated or raw scan).
"""
import gzip
import io
import struct
import sys
from collections import Counter

TIER_FOOTPRINT = [(3, 3), (4, 5), (5, 9), (6, 15), (7, 33), (8, 51)]


def tier_for(sx, sz):
    edge = max(sx, sz)
    for tier, cap in TIER_FOOTPRINT:
        if edge <= cap:
            return tier
    return 9


class Reader:
    def __init__(self, b):
        self.b, self.o = b, 0

    def u1(self):
        v = self.b[self.o]; self.o += 1; return v

    def n(self, size, fmt):
        v = struct.unpack_from(fmt, self.b, self.o)[0]; self.o += size; return v

    def string(self):
        ln = self.n(2, ">H")
        s = self.b[self.o:self.o + ln].decode("utf-8", "replace"); self.o += ln; return s

    def payload(self, t):
        if t == 1: return self.n(1, ">b")
        if t == 2: return self.n(2, ">h")
        if t == 3: return self.n(4, ">i")
        if t == 4: return self.n(8, ">q")
        if t == 5: return self.n(4, ">f")
        if t == 6: return self.n(8, ">d")
        if t == 7:
            ln = self.n(4, ">i"); v = self.b[self.o:self.o + ln]; self.o += ln; return v
        if t == 8: return self.string()
        if t == 9:
            et = self.u1(); ln = self.n(4, ">i"); return [self.payload(et) for _ in range(ln)]
        if t == 10:
            o = {}
            while True:
                tt = self.u1()
                if tt == 0: break
                key = self.string()
                o[key] = self.payload(tt)
            return o
        if t == 11:
            ln = self.n(4, ">i"); a = struct.unpack_from(">%di" % ln, self.b, self.o); self.o += 4 * ln; return list(a)
        if t == 12:
            ln = self.n(4, ">i"); a = struct.unpack_from(">%dq" % ln, self.b, self.o); self.o += 8 * ln; return list(a)
        raise ValueError("bad NBT tag %d" % t)

    def root(self):
        if self.u1() != 10:
            raise ValueError("NBT root is not a compound")
        self.string()
        return self.payload(10)


def block_id(palette_entry):
    bracket = palette_entry.find("[")
    return palette_entry if bracket < 0 else palette_entry[:bracket]


def main():
    raw = open(sys.argv[1], "rb").read()
    root = Reader(gzip.GzipFile(fileobj=io.BytesIO(raw)).read()).root()

    sx, sy, sz = root["Size"]
    palette = root["Palette"]
    blocks = root["Blocks"]

    placed = sum(1 for v in blocks if v != -1)
    print(f"Name:    {root.get('Name', '(unnamed)')}")
    print(f"Size:    {sx} x {sy} x {sz}  (W x H x D)")
    print(f"Blocks:  {placed:,}    Min printer tier: T{tier_for(sx, sz)}")
    print(f"Palette: {len(palette)} states\n")

    # scaffolding cells (stripped on import — Patrick's scanning crutch)
    scaffold_idx = {i for i, p in enumerate(palette) if block_id(p) == "minecraft:scaffolding"}
    scaffold_cells = []
    if scaffold_idx:
        for i, v in enumerate(blocks):
            if v in scaffold_idx:
                x = i % sx
                z = (i // sx) % sz
                y = i // (sx * sz)
                scaffold_cells.append((x, y, z))
    print(f"SCAFFOLDING: {len(scaffold_cells)} cell(s) — STRIP THESE on import")
    if scaffold_cells:
        preview = ", ".join(f"({x},{y},{z})" for x, y, z in scaffold_cells[:20])
        print(f"  positions: {preview}{' …' if len(scaffold_cells) > 20 else ''}")
    print()

    # block-entities carrying container contents (stripped on import)
    bes = root.get("BlockEntities", [])
    with_items = [be for be in bes if isinstance(be.get("Data"), dict) and "Items" in be["Data"]]
    print(f"BLOCK-ENTITIES: {len(bes)} total, {len(with_items)} carrying container Items (STRIP Items)")
    for be in with_items[:20]:
        pos = be.get("Pos", "?")
        bid = be["Data"].get("id", "?")
        print(f"  {bid} @ {pos}")
    print()

    # distinct block ids — the FU sanity list (FU is game-side; eyeball / let the
    # printability gametest confirm which are unvalued)
    ids = Counter(block_id(palette[v]) for v in blocks if v != -1)
    print(f"DISTINCT BLOCK IDS ({len(ids)}) — check these against the FU economy:")
    for bid, cnt in sorted(ids.items()):
        print(f"  {cnt:6,}  {bid}")


if __name__ == "__main__":
    main()
