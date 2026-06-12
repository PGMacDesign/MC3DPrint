#!/usr/bin/env python3
"""
Generates the 3D Printer GUI texture (assets/mc3dprint/textures/gui/printer.png).

The slot/bar coordinates here MUST stay in lockstep with the Java side:
  - PrinterScreen.java  (imageWidth/imageHeight, bar + arrow geometry)
  - PrinterMenu.java     (template/output slot + player inventory slot positions)

Canvas is 256x256 (the blit short-overload assumes that); the panel itself is
176x200 in the top-left. Run from the repo root:  python3 tools/gen_printer_gui.py
"""
import os
import struct
import zlib

W = H = 256
PANEL_W, PANEL_H = 176, 200

# vanilla GUI palette
GRAY = (198, 198, 198, 255)   # panel face
GRAY8 = (139, 139, 139, 255)  # slot interior
DARK = (55, 55, 55, 255)      # recessed border (top/left)
DARKER = (74, 74, 74, 255)    # empty bar / arrow channel fill
WHITE = (255, 255, 255, 255)  # raised/recessed light edge
SHADOW = (85, 85, 85, 255)    # panel drop edge

buf = bytearray(W * H * 4)  # transparent


def px(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        i = (y * W + x) * 4
        buf[i:i + 4] = bytes(c)


def rect(x, y, w, h, c):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            px(xx, yy, c)


def hline(x, y, w, c):
    rect(x, y, w, 1, c)


def vline(x, y, h, c):
    rect(x, y, 1, h, c)


def slot(x, y):
    """Recessed 16x16 slot whose interaction origin is (x, y)."""
    rect(x, y, 16, 16, GRAY8)
    hline(x - 1, y - 1, 18, DARK)    # top
    vline(x - 1, y - 1, 18, DARK)    # left
    hline(x - 1, y + 16, 18, WHITE)  # bottom
    vline(x + 16, y - 1, 18, WHITE)  # right


def channel(x, y, w, h):
    """Recessed dark channel — Java paints the colored fill on top of it."""
    rect(x, y, w, h, DARKER)
    hline(x - 1, y - 1, w + 2, DARK)
    vline(x - 1, y - 1, h + 2, DARK)
    hline(x - 1, y + h, w + 2, WHITE)
    vline(x + w, y - 1, h + 2, WHITE)


# panel face + raised bevel
rect(0, 0, PANEL_W, PANEL_H, GRAY)
hline(0, 0, PANEL_W, WHITE)
hline(0, 1, PANEL_W, WHITE)
vline(0, 0, PANEL_H, WHITE)
vline(1, 0, PANEL_H, WHITE)
hline(0, PANEL_H - 2, PANEL_W, SHADOW)
hline(0, PANEL_H - 1, PANEL_W, SHADOW)
vline(PANEL_W - 2, 0, PANEL_H, SHADOW)
vline(PANEL_W - 1, 0, PANEL_H, SHADOW)

# machine band: energy bar (left), filament bar (right), template + output slots, progress arrow
channel(11, 18, 12, 50)    # energy   (ENERGY_X/Y/W/H)
channel(153, 18, 12, 50)   # filament (FU_X/Y/W/H)
slot(53, 35)               # template (TEMPLATE_SLOT_X/Y)
slot(116, 35)              # output   (OUTPUT_SLOT_X/Y)
channel(80, 36, 22, 15)    # progress (ARROW_X/Y/W/H)

# player inventory: 3x9 grid + hotbar (PrinterMenu slot Y = 116/134/152 + 174)
for row in range(3):
    for col in range(9):
        slot(8 + col * 18, 116 + row * 18)
for col in range(9):
    slot(8 + col * 18, 174)


def write_png(path):
    raw = bytearray()
    for y in range(H):
        raw.append(0)  # filter: none
        raw += buf[y * W * 4:(y + 1) * W * 4]
    comp = zlib.compress(bytes(raw), 9)

    def chunk(typ, data):
        return (struct.pack(">I", len(data)) + typ + data
                + struct.pack(">I", zlib.crc32(typ + data) & 0xffffffff))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", comp)
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


if __name__ == "__main__":
    out = os.path.join(os.path.dirname(__file__), os.pardir,
                       "src/main/resources/assets/mc3dprint/textures/gui/printer.png")
    out = os.path.normpath(out)
    write_png(out)
    print("wrote", out)
