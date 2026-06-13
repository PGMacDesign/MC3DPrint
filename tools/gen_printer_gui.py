#!/usr/bin/env python3
"""
Generates the dark tech-console GUI sheets for MC3DPrint:
  - assets/mc3dprint/textures/gui/printer.png  (176x200 panel in a 256x256 sheet)
  - assets/mc3dprint/textures/gui/machine.png  (176x166 panel — the Filament Winder)

The sheet only paints the STATIC chrome (panel, bevels, accent line, recessed
slot wells, recessed bar/arrow channels). All dynamic fills (energy/filament/
progress) and every text label are drawn in CODE on top:
  - PrinterScreen.java  (imageWidth/imageHeight, bar + arrow geometry, labels)
  - WinderScreen.java   (same bars/arrow, status message, red-X)
  - PrinterMenu.java / WinderMenu.java  (slot x/y positions)

The slot/bar COORDINATES here MUST stay in lockstep with the Java side and are
read straight from those classes — do not invent new ones. Run from repo root:
    python3 tools/gen_printer_gui.py

Palette (VISUAL-REVAMP-BRIEF, "GUI — dark tech-console"):
    field        #10141E   panel        #1A1F2B
    bevel-light  #2C3342   bevel-dark   #0A0D14
    accent line  #3FE0C0   label text   #C0C0C8
"""
import os

from PIL import Image

# ---------------------------------------------------------------------------
# Console palette (RGBA)
# ---------------------------------------------------------------------------
FIELD       = (0x10, 0x14, 0x1E, 255)  # darkest backdrop behind the panel
PANEL       = (0x1A, 0x1F, 0x2B, 255)  # charcoal panel face
BEVEL_LIGHT = (0x2C, 0x33, 0x42, 255)  # 1px top/left bevel highlight
BEVEL_DARK  = (0x0A, 0x0D, 0x14, 255)  # 1px bottom/right bevel shadow
ACCENT      = (0x3F, 0xE0, 0xC0, 255)  # cyan "console" accent line
ACCENT_DIM  = (0x27, 0x86, 0x76, 255)  # dimmed accent (LED off / faint trim)

# Recessed slot well: a dark rim sunk into the panel with a slightly lighter
# inset floor so item icons stay legible against it.
WELL_RIM    = (0x0E, 0x12, 0x1A, 255)  # rim shadow (sunken edge)
WELL_FLOOR  = (0x23, 0x2A, 0x39, 255)  # lighter inset floor (icons read on this)
WELL_HILITE = (0x33, 0x3C, 0x4E, 255)  # 1px lit edge on the bottom/right of the well

# Recessed bar / arrow channel: darker than slots so the coloured fill pops.
CHAN_RIM    = (0x07, 0x0A, 0x10, 255)
CHAN_FLOOR  = (0x0D, 0x10, 0x18, 255)
CHAN_HILITE = (0x2C, 0x33, 0x42, 255)

# Status LED dots (drawn dim/off in the static sheet; the Screen can brighten in
# code, but a couple of subtle housings give the corner a console feel).
LED_OFF     = (0x12, 0x17, 0x20, 255)
LED_GREEN   = (0x2E, 0x7D, 0x4A, 255)
LED_AMBER   = (0x8A, 0x6A, 0x20, 255)

W = H = 256


# ---------------------------------------------------------------------------
# Low-level draw helpers (operate on a PIL pixel-access object)
# ---------------------------------------------------------------------------
def rect(px, x, y, w, h, c):
    for yy in range(y, y + h):
        if not (0 <= yy < H):
            continue
        for xx in range(x, x + w):
            if 0 <= xx < W:
                px[xx, yy] = c


def hline(px, x, y, w, c):
    rect(px, x, y, w, 1, c)


def vline(px, x, y, h, c):
    rect(px, x, y, 1, h, c)


def panel(px, w, h):
    """Charcoal console panel with top/left light bevel + bottom/right dark bevel
    and the thin cyan accent line running along the very top edge."""
    rect(px, 0, 0, w, h, PANEL)

    # outer 1px frame: a near-black seam so the panel reads as a raised console
    hline(px, 0, 0, w, BEVEL_DARK)
    vline(px, 0, 0, h, BEVEL_DARK)
    hline(px, 0, h - 1, w, BEVEL_DARK)
    vline(px, w - 1, 0, h, BEVEL_DARK)

    # inner bevel: light on top/left, dark on bottom/right (1px each)
    hline(px, 1, 1, w - 2, BEVEL_LIGHT)
    vline(px, 1, 1, h - 2, BEVEL_LIGHT)
    hline(px, 1, h - 2, w - 2, BEVEL_DARK)
    vline(px, w - 2, 1, h - 2, BEVEL_DARK)

    # thin cyan accent line just under the top bevel = "console" identity,
    # with a 1px dimmer line below it for a soft glow falloff
    hline(px, 2, 2, w - 4, ACCENT)
    hline(px, 2, 3, w - 4, ACCENT_DIM)


def slot(px, x, y):
    """Recessed 16x16 slot whose interaction origin is (x, y).

    Sunken rim around the outside, a slightly lighter inset floor inside so item
    icons stay legible, and a 1px lit edge on the bottom/right of the floor."""
    # lighter inset floor (item icons render on top of this)
    rect(px, x, y, 16, 16, WELL_FLOOR)
    # 1px inner shadow top + left = the floor sits below the panel (deeper well)
    hline(px, x, y, 16, WELL_RIM)
    vline(px, x, y, 16, WELL_RIM)
    # 1px lit edge bottom + right of the floor (catches the top-left light)
    hline(px, x, y + 15, 16, WELL_HILITE)
    vline(px, x + 15, y, 16, WELL_HILITE)
    # sunken rim (1px) around the slot
    hline(px, x - 1, y - 1, 18, WELL_RIM)
    vline(px, x - 1, y - 1, 18, WELL_RIM)
    hline(px, x - 1, y + 16, 18, BEVEL_LIGHT)
    vline(px, x + 16, y - 1, 18, BEVEL_LIGHT)


def channel(px, x, y, w, h):
    """Recessed dark channel for a bar / progress arrow. Java paints the coloured
    fill on top, so this stays very dark so the fill pops."""
    rect(px, x, y, w, h, CHAN_FLOOR)
    # sunken rim top/left, faint lit edge bottom/right
    hline(px, x - 1, y - 1, w + 2, CHAN_RIM)
    vline(px, x - 1, y - 1, h + 2, CHAN_RIM)
    hline(px, x - 1, y + h, w + 2, CHAN_HILITE)
    vline(px, x + w, y - 1, h + 2, CHAN_HILITE)


def led(px, x, y, c):
    """Tiny 2x2 status LED housing with a 1px dark surround."""
    rect(px, x - 1, y - 1, 4, 4, BEVEL_DARK)
    rect(px, x, y, 2, 2, c)


# ---------------------------------------------------------------------------
# Sheet builders
# ---------------------------------------------------------------------------
def machine_band(px):
    """The energy/filament bars + progress arrow shared by both machines.
    Coordinates come straight from PrinterScreen/WinderScreen (identical)."""
    channel(px, 11, 18, 12, 50)    # energy   (ENERGY_X/Y/W/H)
    channel(px, 153, 18, 12, 50)   # filament (FU_X/Y/W/H)
    channel(px, 80, 36, 22, 15)    # progress (ARROW_X/Y/W/H)


def status_leds(px, panel_w):
    """2 tiny status LEDs in the top-right corner (green=ready, amber=working).
    Drawn dim here so they read as housings; purely decorative chrome."""
    led(px, panel_w - 9, 6, LED_GREEN)
    led(px, panel_w - 9, 12, LED_AMBER)


# Upgrade-slot wells: a 2-column grid on the right of the machine area. These
# x/y/step values MUST match PrinterMenu (UPGRADE_SLOT_X/Y, UPGRADE_COL_STEP,
# UPGRADE_ROW_STEP, UPGRADE_COLS, MAX_UPGRADE_SLOTS). The menu only adds the
# slots a tier actually has, but we paint all MAX_UPGRADE_SLOTS wells so any
# tier's GUI has a backing well under each live slot.
UPGRADE_SLOT_X = 178
UPGRADE_SLOT_Y = 18
UPGRADE_COL_STEP = 18
UPGRADE_ROW_STEP = 18
UPGRADE_COLS = 2
MAX_UPGRADE_SLOTS = 8


def build_printer():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    # widened from 176 to host the upgrade-slot column (PrinterScreen.imageWidth)
    pw, ph = 230, 200
    panel(px, pw, ph)
    machine_band(px)
    slot(px, 53, 35)    # template (PrinterMenu.TEMPLATE_SLOT_X/Y)
    slot(px, 116, 35)   # output   (PrinterMenu.OUTPUT_SLOT_X/Y)
    # upgrade-slot wells (2-col grid, row-major), up to MAX_UPGRADE_SLOTS
    for i in range(MAX_UPGRADE_SLOTS):
        col = i % UPGRADE_COLS
        row_idx = i // UPGRADE_COLS
        slot(px, UPGRADE_SLOT_X + col * UPGRADE_COL_STEP,
             UPGRADE_SLOT_Y + row_idx * UPGRADE_ROW_STEP)
    # player inventory: 3x9 grid + hotbar (PrinterMenu slot Y = 116/134/152 + 174)
    for row in range(3):
        for col in range(9):
            slot(px, 8 + col * 18, 116 + row * 18)
    for col in range(9):
        slot(px, 8 + col * 18, 174)
    status_leds(px, pw)
    return img


def build_machine():
    """Filament Winder sheet (176x166). Same machine band; the input + spool
    slots sit at the same x as the printer (53 / 116) but the player inventory
    starts higher (y=84) because the winder panel is shorter."""
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    pw, ph = 176, 166
    panel(px, pw, ph)
    machine_band(px)
    slot(px, 53, 35)    # input  (WinderMenu addSlot SLOT_INPUT 53,35)
    slot(px, 116, 35)   # spool  (WinderMenu addSlot SLOT_SPOOL 116,35)
    # player inventory: 3x9 grid + hotbar (WinderMenu slot Y = 84/102/120 + 142)
    for row in range(3):
        for col in range(9):
            slot(px, 8 + col * 18, 84 + row * 18)
    for col in range(9):
        slot(px, 8 + col * 18, 142)
    status_leds(px, pw)
    return img


GUI_DIR = os.path.normpath(os.path.join(
    os.path.dirname(__file__), os.pardir,
    "src/main/resources/assets/mc3dprint/textures/gui"))


def main():
    os.makedirs(GUI_DIR, exist_ok=True)
    printer = build_printer()
    machine = build_machine()
    printer.save(os.path.join(GUI_DIR, "printer.png"))
    machine.save(os.path.join(GUI_DIR, "machine.png"))
    print("wrote", os.path.join(GUI_DIR, "printer.png"))
    print("wrote", os.path.join(GUI_DIR, "machine.png"))


if __name__ == "__main__":
    main()
