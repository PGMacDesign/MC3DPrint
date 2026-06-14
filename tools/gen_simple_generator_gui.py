#!/usr/bin/env python3
"""
Generates the dark tech-console GUI sheet for the MC3DPrint Simple Generator
(clock generator):
  - assets/mc3dprint/textures/gui/simple_generator.png  (176x166 panel in 256x256)

Like gen_printer_gui.py, this paints only the STATIC chrome (panel, bevels,
accent line, recessed fuel-slot well, recessed flame + RF channels, player-inv
wells). The dynamic fills (RF bar, flame) and all text labels are drawn in CODE
on top, in SimpleGeneratorScreen.java. The COORDINATES here MUST stay in
lockstep with:
  - SimpleGeneratorMenu.java  (fuel slot 80,53; player inv 8,84 / 8,142)
  - SimpleGeneratorScreen.java (ENERGY_X/Y/W/H = 152,18,14,54;
                                FLAME_X/Y/W/H = 81,36,14,14)

Run from repo root:
    python3 tools/gen_simple_generator_gui.py

Reuses the palette + draw helpers from gen_printer_gui.py so the sheet matches
the printer/winder visual identity exactly.
"""
import os

from PIL import Image

# Reuse the exact palette + draw helpers from the printer/winder generator so
# the Simple Generator looks identical in style.
from gen_printer_gui import (  # noqa: E402
    W, H, panel, slot, channel, status_leds,
)

# Lockstep coordinates (see module docstring).
FUEL_SLOT_X, FUEL_SLOT_Y = 80, 53
FLAME_X, FLAME_Y, FLAME_W, FLAME_H = 81, 36, 14, 14
ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H = 152, 18, 14, 54


def build_generator():
    """Simple Generator sheet (176x166): a centered fuel slot, a flame channel
    above it, a tall RF bar on the right, and the standard player inventory."""
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    pw, ph = 176, 166
    panel(px, pw, ph)

    channel(px, FLAME_X, FLAME_Y, FLAME_W, FLAME_H)      # flame indicator
    channel(px, ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H)  # RF bar

    slot(px, FUEL_SLOT_X, FUEL_SLOT_Y)                   # fuel input

    # player inventory: 3x9 grid + hotbar (matches SimpleGeneratorMenu)
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
    img = build_generator()
    out = os.path.join(GUI_DIR, "simple_generator.png")
    img.save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
