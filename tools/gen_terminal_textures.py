#!/usr/bin/env python3
"""Textures for the MC3DPrint Terminal AE2 part (16x16 face + item icon).

Reads as an AE2 terminal at a glance so it sits naturally on a cable run, but in
this mod's charcoal-and-cyan console palette rather than AE2's own, so you can
tell it apart from a Crafting Terminal on a busy wall.

Three lit states, matching what AE2 asks every screen part for:
  off          unpowered, screen dark
  on           powered but no channel, screen dim
  has_channel  online, screen lit with catalog rows

Reproducible; rerun to regenerate.

  python3 tools/gen_terminal_textures.py
"""
import os
from PIL import Image, ImageDraw

PANEL = (0x1A, 0x1F, 0x2B, 255)
BEVEL_L = (0x2C, 0x33, 0x42, 255)
BEVEL_D = (0x0A, 0x0D, 0x14, 255)
SCREEN_OFF = (0x0D, 0x10, 0x18, 255)
SCREEN_DIM = (0x14, 0x2A, 0x28, 255)
SCREEN_ON = (0x10, 0x3A, 0x36, 255)
ACCENT = (0x3F, 0xE0, 0xC0, 255)
ACCENT_D = (0x27, 0x86, 0x76, 255)

BLOCK_OUT = os.path.normpath(os.path.join(os.path.dirname(__file__), os.pardir,
            "src/main/resources/assets/mc3dprint/textures/part"))
ITEM_OUT = os.path.normpath(os.path.join(os.path.dirname(__file__), os.pardir,
           "src/main/resources/assets/mc3dprint/textures/item"))


def _bezel(d):
    """The casing every state shares: charcoal panel with a lit top bevel."""
    d.rectangle([0, 0, 15, 15], fill=PANEL)
    d.line([0, 0, 15, 0], fill=BEVEL_L)
    d.line([0, 0, 0, 15], fill=BEVEL_L)
    d.line([0, 15, 15, 15], fill=BEVEL_D)
    d.line([15, 0, 15, 15], fill=BEVEL_D)


def _screen(d, fill):
    d.rectangle([2, 2, 13, 12], fill=fill)


def base():
    """The unlit casing the three states are layered over."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    _bezel(d)
    # Two indicator studs along the bottom lip, present in every state.
    d.point([(4, 14), (11, 14)], fill=BEVEL_D)
    return img


def state(kind):
    img = base()
    d = ImageDraw.Draw(img)
    if kind == "off":
        _screen(d, SCREEN_OFF)
        return img
    if kind == "on":
        _screen(d, SCREEN_DIM)
        # A single dim row: powered, but nothing to list without a channel.
        d.line([4, 7, 11, 7], fill=ACCENT_D)
        return img
    _screen(d, SCREEN_ON)
    # Catalog rows, brightest at the top like a populated list.
    d.line([4, 4, 11, 4], fill=ACCENT)
    d.line([4, 6, 10, 6], fill=ACCENT)
    d.line([4, 8, 11, 8], fill=ACCENT_D)
    d.line([4, 10, 9, 10], fill=ACCENT_D)
    d.point([(4, 14), (11, 14)], fill=ACCENT)
    return img


def item_icon():
    """Inventory icon: the online face, tilted read by a lit left edge."""
    img = state("has_channel")
    d = ImageDraw.Draw(img)
    d.line([1, 1, 1, 14], fill=ACCENT_D)
    return img


def main():
    os.makedirs(BLOCK_OUT, exist_ok=True)
    os.makedirs(ITEM_OUT, exist_ok=True)
    base().save(os.path.join(BLOCK_OUT, "mc3dprint_terminal_base.png"))
    for kind in ("off", "on", "has_channel"):
        state(kind).save(os.path.join(BLOCK_OUT, f"mc3dprint_terminal_{kind}.png"))
    item_icon().save(os.path.join(ITEM_OUT, "me_print_terminal.png"))
    print(f"wrote 4 part textures to {BLOCK_OUT}")
    print(f"wrote 1 item texture to {ITEM_OUT}")


if __name__ == "__main__":
    main()
