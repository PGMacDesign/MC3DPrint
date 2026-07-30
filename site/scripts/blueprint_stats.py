#!/usr/bin/env python3
"""One-line stats for a .blueprint file: "13x22x23 - 4,210 blocks - Tier 7".

Used by the PR-preview workflow to annotate each changed blueprint. The input is
UNTRUSTED (it comes from a fork PR), so this hardens against decompression bombs
and absurd volumes before doing any allocation. It only ever parses *data*: it
never executes anything from the PR.

    python3 blueprint_stats.py <file.blueprint>
"""
import gzip
import struct
import sys

MAX_INFLATED = 32 * 1024 * 1024     # reject gzip bombs (curated builds are < 1 MB)
MAX_VOLUME = 12_000_000             # T8 is 51x51x~ ; well above any real build

# Smallest printer tier for a footprint (X/Z); mirrors MachineTier.maxFootprint.
TIER_FOOTPRINT = [(3, 3), (4, 5), (5, 9), (6, 15), (7, 33), (8, 51)]


def tier_for(sx, sz):
    edge = max(sx, sz)
    for tier, cap in TIER_FOOTPRINT:
        if edge <= cap:
            return tier
    return 9


def inflate_capped(raw):
    d = gzip.GzipFile(fileobj=__import__("io").BytesIO(raw))
    out = bytearray()
    while True:
        chunk = d.read(1 << 20)
        if not chunk:
            break
        out += chunk
        if len(out) > MAX_INFLATED:
            raise ValueError("inflated payload exceeds cap (possible bomb)")
    return bytes(out)


class Reader:
    def __init__(self, b):
        self.b, self.o = b, 0

    def u1(self):
        v = self.b[self.o]; self.o += 1; return v

    def i(self, n, fmt):
        v = struct.unpack_from(fmt, self.b, self.o)[0]; self.o += n; return v

    def string(self):
        n = self.i(2, ">H")
        s = self.b[self.o:self.o + n].decode("utf-8", "replace"); self.o += n; return s

    def payload(self, t):
        if t == 1: return self.i(1, ">b")
        if t == 2: return self.i(2, ">h")
        if t == 3: return self.i(4, ">i")
        if t == 4: return self.i(8, ">q")
        if t == 5: return self.i(4, ">f")
        if t == 6: return self.i(8, ">d")
        if t == 7:
            n = self.i(4, ">i"); v = self.b[self.o:self.o + n]; self.o += n; return v
        if t == 8: return self.string()
        if t == 9:
            et = self.u1(); n = self.i(4, ">i"); return [self.payload(et) for _ in range(n)]
        if t == 10:
            o = {}
            while True:
                tt = self.u1()
                if tt == 0: break
                key = self.string()            # read key BEFORE value: Python
                o[key] = self.payload(tt)      # evaluates RHS first in o[k]=v
            return o
        if t == 11:
            n = self.i(4, ">i"); a = struct.unpack_from(">%di" % n, self.b, self.o); self.o += 4 * n; return list(a)
        if t == 12:
            n = self.i(4, ">i"); a = struct.unpack_from(">%dq" % n, self.b, self.o); self.o += 8 * n; return list(a)
        raise ValueError("bad NBT tag %d" % t)

    def root(self):
        if self.u1() != 10:
            raise ValueError("NBT root is not a compound")
        self.string()
        return self.payload(10)


def main():
    with open(sys.argv[1], "rb") as f:
        raw = f.read()
    root = Reader(inflate_capped(raw)).root()
    sx, sy, sz = root["Size"][0], root["Size"][1], root["Size"][2]
    if sx * sy * sz > MAX_VOLUME or min(sx, sy, sz) <= 0:
        raise ValueError("implausible volume")
    blocks = sum(1 for v in root["Blocks"] if v != -1)
    print("%dx%dx%d - %s blocks - Tier %d" % (sx, sy, sz, format(blocks, ","), tier_for(sx, sz)))


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print("unreadable (%s)" % e)
        sys.exit(0)  # never fail the workflow over one bad file
