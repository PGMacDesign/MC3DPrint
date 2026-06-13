#!/usr/bin/env python3
"""
Generates the Patchouli guidebook diagram images for MC3DPrint into
    assets/mc3dprint/textures/patchouli/

Patchouli renders a `patchouli:image` page by drawing the full PNG scaled to a
fixed on-page width (~100 GUI px) keeping aspect ratio. The convention this mod
follows is a 256x256 canvas with all meaningful content kept in the top ~200px
(Patchouli letterboxes the bottom of square images on some page layouts), so we
draw on a transparent 256x256 and keep every diagram inside the top 200px.

Images written (all 256x256):
  layout_3x3.png  layout_5x5.png  layout_7x7.png  layout_9x9.png
      Top-down multiblock base diagrams for T5/T6/T7/T8. Corner cells labelled
      (casing, or AWAKENED for T8), centre cell = controller.
  printer_gui.png
      Annotated printer GUI: energy bar, template slot, output slot, filament
      bar, upgrade slots, status line, X/Y/Z offset buttons. Geometry mirrors
      tools/gen_printer_gui.py / PrinterMenu so the diagram matches the game.
  print_flow.png
      "Scan -> Disc -> Print" flow diagram.

Run from repo root:
    python3 tools/gen_guide_images.py

The console/tier palette is shared with the rest of the texture set
(tools/tex_common.py and tools/gen_printer_gui.py) so the book art reads as part
of the same mod, not a bolt-on.
"""
import os

from PIL import Image, ImageDraw, ImageFont

# ---------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(
    ROOT, "src/main/resources/assets/mc3dprint/textures/patchouli")

W = H = 256
CONTENT_H = 200  # keep all content in the top 200px

# ---------------------------------------------------------------------------
# Palette — matches gen_printer_gui.py ("GUI dark tech-console") + tier accents
# from tex_common.py so the book art is cohesive with the mod's textures.
# ---------------------------------------------------------------------------
FIELD       = (0x10, 0x14, 0x1E, 255)  # darkest backdrop
PANEL       = (0x1A, 0x1F, 0x2B, 255)  # charcoal panel face
BEVEL_LIGHT = (0x2C, 0x33, 0x42, 255)  # top/left bevel
BEVEL_DARK  = (0x0A, 0x0D, 0x14, 255)  # bottom/right bevel
ACCENT      = (0x3F, 0xE0, 0xC0, 255)  # cyan console accent
ACCENT_DIM  = (0x27, 0x86, 0x76, 255)
LABEL       = (0xC0, 0xC0, 0xC8, 255)
LABEL_DIM   = (0x82, 0x88, 0x92, 255)
WHITE       = (0xF0, 0xF3, 0xF6, 255)
WELL_FLOOR  = (0x23, 0x2A, 0x39, 255)
WELL_RIM    = (0x0E, 0x12, 0x1A, 255)
GLOW_CORE   = (0xBF, 0xE9, 0xFF, 255)
GLOW_BODY   = (0x5C, 0xC8, 0xFF, 255)
CASING      = (0x9A, 0xA3, 0xAD, 255)  # BODY[3] — machined grey casing
CASING_HI   = (0xBC, 0xC4, 0xCC, 255)  # BODY[2]
CASING_LO   = (0x6E, 0x76, 0x7F, 255)  # BODY[4]

# Tier accents (tex_common.TIER), used to tint each layout's controller/title.
TIER = {
    5: (0xE0, 0xB4, 0x3A, 255),  # gold
    6: (0xE8, 0x7A, 0x3A, 255),  # orange
    7: (0x9B, 0x6B, 0xE8, 255),  # violet
    8: (0xE8, 0x4F, 0xB0, 255),  # magenta
}


# ---------------------------------------------------------------------------
# Font handling — prefer a crisp bitmap-ish TTF, fall back to PIL default.
# ---------------------------------------------------------------------------
def _load_font(size):
    candidates = [
        "/System/Library/Fonts/SFNSMono.ttf",
        "/System/Library/Fonts/Menlo.ttc",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


FONT_TITLE = _load_font(18)
FONT_BODY = _load_font(13)
FONT_SMALL = _load_font(11)
FONT_TINY = _load_font(9)


def _text(draw, xy, s, font, fill, anchor="la"):
    draw.text(xy, s, font=font, fill=fill, anchor=anchor)


def _text_center(draw, cx, y, s, font, fill):
    draw.text((cx, y), s, font=font, fill=fill, anchor="ma")


def _fit_font(draw, s, start_size, max_w, floor=10):
    """Pick the largest cached title size whose text width fits in max_w."""
    for size in range(start_size, floor - 1, -1):
        f = _load_font(size)
        if draw.textlength(s, font=f) <= max_w:
            return f
    return _load_font(floor)


# ---------------------------------------------------------------------------
# Shared chrome
# ---------------------------------------------------------------------------
def new_canvas():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def backdrop(draw, x, y, w, h, fill=FIELD):
    """Rounded-ish console backdrop panel with bevels + cyan accent line."""
    draw.rectangle([x, y, x + w - 1, y + h - 1], fill=fill)
    # outer dark frame
    draw.rectangle([x, y, x + w - 1, y + h - 1], outline=BEVEL_DARK, width=1)
    # inner light bevel top/left
    draw.line([(x + 1, y + 1), (x + w - 2, y + 1)], fill=BEVEL_LIGHT)
    draw.line([(x + 1, y + 1), (x + 1, y + h - 2)], fill=BEVEL_LIGHT)
    # cyan accent under the top edge
    draw.line([(x + 2, y + 3), (x + w - 3, y + 3)], fill=ACCENT)
    draw.line([(x + 2, y + 4), (x + w - 3, y + 4)], fill=ACCENT_DIM)


def title_bar(draw, text, accent=ACCENT):
    """Top title strip used by every diagram. Auto-shrinks to fit the canvas."""
    font = _fit_font(draw, text, 18, W - 24)
    _text_center(draw, W // 2, 8, text, font, WHITE)
    # underline in the accent colour
    tw = draw.textlength(text, font=font)
    x0 = int(W // 2 - tw / 2)
    x1 = int(W // 2 + tw / 2)
    draw.line([(x0, 32), (x1, 32)], fill=accent, width=2)


def bevel_cell(draw, x, y, s, fill, hi, lo):
    """A beveled square cell (a block, top-down)."""
    draw.rectangle([x, y, x + s - 1, y + s - 1], fill=fill)
    draw.line([(x, y), (x + s - 1, y)], fill=hi)
    draw.line([(x, y), (x, y + s - 1)], fill=hi)
    draw.line([(x, y + s - 1), (x + s - 1, y + s - 1)], fill=lo)
    draw.line([(x + s - 1, y), (x + s - 1, y + s - 1)], fill=lo)


# ---------------------------------------------------------------------------
# Multiblock layout diagrams (top-down)
# ---------------------------------------------------------------------------
def gen_layout(tier, edge):
    """Top-down N×N base: corners = casing (AWAKENED for T8), centre = controller."""
    img, draw = new_canvas()
    accent = TIER[tier]
    title_bar(draw, f"Tier {tier}  -  {edge}x{edge} Base", accent)

    # grid geometry: fit the edge×edge grid into a ~118px square, centered, with
    # headroom above (corner caption) and below (controller + form captions).
    grid_px = 118
    cell = grid_px // edge
    grid_px = cell * edge
    gap = max(1, cell // 12)
    gx = (W - grid_px) // 2
    gy = 48

    half = edge // 2
    for r in range(edge):
        for c in range(edge):
            x = gx + c * cell
            y = gy + r * cell
            is_center = (r == half and c == half)
            is_corner = (r in (0, edge - 1) and c in (0, edge - 1))
            inner = cell - gap
            if is_center:
                # controller: tier-accent core with a glow ring
                bevel_cell(draw, x, y, inner, accent,
                           tuple(min(255, v + 40) for v in accent[:3]) + (255,),
                           tuple(max(0, v - 50) for v in accent[:3]) + (255,))
                # emissive dot
                ccx, ccy = x + inner // 2, y + inner // 2
                draw.ellipse([ccx - 3, ccy - 3, ccx + 3, ccy + 3], fill=GLOW_CORE)
            elif is_corner and tier == 8:
                # AWAKENED draconium corner (magenta-tinted)
                drac = (0xC0, 0x3A, 0x8A, 255)
                bevel_cell(draw, x, y, inner, drac,
                           (0xE0, 0x60, 0xB0, 255), (0x70, 0x20, 0x50, 255))
            else:
                bevel_cell(draw, x, y, inner, CASING, CASING_HI, CASING_LO)

    # corner label: leader from the top-left corner cell up to a caption that
    # sits ABOVE the grid (between the title underline and the grid top).
    corner_cx = gx + cell // 2
    corner_cy = gy + cell // 2
    corner_label = ("corners = Awakened Draconium" if tier == 8
                    else "corners = Printer Casing")
    draw.line([(corner_cx, corner_cy), (gx + 4, gy - 5)], fill=LABEL_DIM)
    _text_center(draw, W // 2, 36, corner_label, FONT_SMALL, LABEL)

    # controller label (center cell) — leader down to a caption below the grid.
    cen_cx = gx + half * cell + cell // 2
    cen_cy = gy + half * cell + cell // 2
    label_y = gy + grid_px + 6
    draw.line([(cen_cx, cen_cy), (cen_cx, label_y)], fill=accent)
    _text_center(draw, cen_cx, label_y, "controller (center)", FONT_SMALL, accent)

    # footer: how to form (two short centred lines, well below the grid)
    _text_center(draw, W // 2, label_y + 16,
                 f"{edge*edge - 1} casing  +  1 controller", FONT_SMALL, LABEL_DIM)
    _text_center(draw, W // 2, label_y + 30,
                 "right-click controller to form", FONT_SMALL, LABEL_DIM)
    return img


# ---------------------------------------------------------------------------
# Printer GUI annotation diagram
# ---------------------------------------------------------------------------
def gen_printer_gui():
    """Annotated printer GUI. Geometry mirrors PrinterMenu / gen_printer_gui.py."""
    img, draw = new_canvas()
    title_bar(draw, "Printer GUI", ACCENT)

    # We draw a compact stylised panel (not 1:1 px with the game sheet, but the
    # same elements in the same relative positions) and annotate each part.
    # Panel is centred with margins so the left/right callout text never clips.
    px0, py0, pw, ph = 66, 44, 124, 92
    backdrop(draw, px0, py0, pw, ph, fill=PANEL)

    def slot(x, y, s=16, fill=WELL_FLOOR):
        draw.rectangle([x, y, x + s - 1, y + s - 1], fill=fill,
                       outline=WELL_RIM, width=1)

    def bar(x, y, w, h, fill):
        draw.rectangle([x, y, x + w - 1, y + h - 1], fill=(0x0D, 0x10, 0x18, 255),
                       outline=WELL_RIM, width=1)
        # partial fill from the bottom up
        fh = int(h * 0.62)
        draw.rectangle([x + 1, y + h - fh, x + w - 2, y + h - 2], fill=fill)

    # energy bar (left), filament bar (right), arrow (center), slots, upgrades
    eb = (px0 + 8, py0 + 14, 8, 60)
    fb = (px0 + pw - 16, py0 + 14, 8, 60)
    bar(*eb, fill=(0xE0, 0x4F, 0x3A, 255))         # energy = red/RF
    bar(*fb, fill=(0x4F, 0xC3, 0xF7, 255))         # filament = cyan
    tmpl = (px0 + 28, py0 + 30)
    outp = (px0 + 64, py0 + 30)
    slot(*tmpl)                                     # template slot
    slot(*outp)                                     # output slot
    # progress arrow between them
    ax = tmpl[0] + 18
    ay = tmpl[1] + 4
    draw.polygon([(ax, ay), (ax + 12, ay + 4), (ax, ay + 8)], fill=ACCENT)
    # upgrade slots: small 2-col grid
    ux, uy = px0 + 92, py0 + 14
    for i in range(4):
        c, r = i % 2, i // 2
        slot(ux + c * 11, uy + r * 11, s=9, fill=(0x2A, 0x32, 0x42, 255))
    # status line
    sx, sy = px0 + 6, py0 + ph - 14
    draw.rectangle([sx, sy, px0 + pw - 6, sy + 8], fill=(0x0D, 0x10, 0x18, 255))
    _text(draw, (sx + 3, sy - 1), "READY", FONT_TINY, ACCENT)
    # offset buttons row (X- X+  Y- Y+  Z- Z+) under the status
    ox, oy = px0 + 6, py0 + ph - 3
    # (drawn just below panel as a control strip)

    # ---- annotations with leader lines ----
    def annot(target, label_xy, text, col=LABEL, anchor="la"):
        draw.line([target, label_xy], fill=ACCENT_DIM, width=1)
        draw.ellipse([target[0] - 1, target[1] - 1, target[0] + 1, target[1] + 1],
                     fill=ACCENT)
        _text(draw, label_xy, text, FONT_SMALL, col, anchor=anchor)

    annot((eb[0] + 4, eb[1] + 4), (6, 50), "Energy (RF)", anchor="la")
    annot((tmpl[0] + 8, tmpl[1] + 8), (6, 72), "Template/Disc", anchor="la")
    annot((outp[0] + 8, outp[1] + 8), (6, 96), "Output", anchor="la")
    annot((ux + 16, uy + 8), (W - 6, 54), "Upgrade slots", col=LABEL, anchor="ra")
    annot((fb[0] + 4, fb[1] + 4), (W - 6, 78), "Filament (FU)", col=LABEL,
          anchor="ra")
    annot((sx + 14, sy + 4), (W - 6, 100), "Status line", col=ACCENT, anchor="ra")

    # control strip below the panel: Start / Auto + offset steppers
    strip_y = py0 + ph + 6
    _text(draw, (px0, strip_y), "[ Start ]", FONT_SMALL, ACCENT)
    _text(draw, (px0 + 52, strip_y), "[ Auto: OFF ]", FONT_SMALL, LABEL_DIM)
    # offset steppers
    oy2 = strip_y + 16
    for i, ax_lbl in enumerate(("X", "Y", "Z")):
        bx = px0 + i * 46
        _text(draw, (bx, oy2), f"{ax_lbl} -  +", FONT_SMALL, LABEL)
    _text_center(draw, W // 2, oy2 + 16,
                 "offsets move the build area  (+/- 32)", FONT_TINY, LABEL_DIM)
    return img


# ---------------------------------------------------------------------------
# Scan -> Disc -> Print flow diagram
# ---------------------------------------------------------------------------
def gen_print_flow():
    img, draw = new_canvas()
    title_bar(draw, "Scan  >  Disc  >  Print", ACCENT)

    # three stage cards across the middle
    cw, ch = 64, 64
    gap = 18
    total = cw * 3 + gap * 2
    x0 = (W - total) // 2
    y0 = 56
    centers = []
    stages = [
        ("SCAN", "Structure\nScanner", (0x34, 0xC0, 0xC0, 255)),
        ("DISC", "Blueprint\nDisc", (0x4F, 0x9B, 0xE8, 255)),
        ("PRINT", "Printer /\nFabricator", (0x46, 0xC6, 0x6B, 255)),
    ]
    for i, (tag, sub, col) in enumerate(stages):
        x = x0 + i * (cw + gap)
        backdrop(draw, x, y0, cw, ch, fill=PANEL)
        centers.append((x + cw // 2, y0 + ch // 2))
        # icon glyph per stage
        cx, cy = x + cw // 2, y0 + 22
        if tag == "SCAN":
            # scanner: a bracketed reticle
            draw.rectangle([cx - 12, cy - 10, cx + 12, cy + 10], outline=col, width=2)
            draw.line([(cx, cy - 6), (cx, cy + 6)], fill=col)
            draw.line([(cx - 6, cy), (cx + 6, cy)], fill=col)
        elif tag == "DISC":
            # disc: a ring with a hub
            draw.ellipse([cx - 12, cy - 12, cx + 12, cy + 12], outline=col, width=2)
            draw.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], fill=col)
        else:
            # printer: stacked layers being built up
            for k, yy in enumerate((cy + 8, cy + 2, cy - 4)):
                wlen = 18 - k * 4
                draw.rectangle([cx - wlen, yy, cx + wlen, yy + 3],
                               fill=col if k < 2 else GLOW_BODY)
        _text_center(draw, x + cw // 2, y0 + 36, tag, FONT_BODY, WHITE)
        for j, line in enumerate(sub.split("\n")):
            _text_center(draw, x + cw // 2, y0 + 48 + j * 11, line, FONT_TINY, LABEL_DIM)

    # arrows between cards
    for (ax, ay), (bx, by) in zip(centers, centers[1:]):
        mx = (ax + cw // 2 + bx - cw // 2) // 2
        sy = y0 + ch // 2
        x_start = ax + cw // 2 + 2
        x_end = bx - cw // 2 - 2
        draw.line([(x_start, sy), (x_end - 6, sy)], fill=ACCENT, width=2)
        draw.polygon([(x_end, sy), (x_end - 7, sy - 5), (x_end - 7, sy + 5)],
                     fill=ACCENT)

    # one short caption per stage, kept narrow enough not to collide
    caps = ["capture", "carry", "build"]
    for i, cap in enumerate(caps):
        x = x0 + i * (cw + gap) + cw // 2
        _text_center(draw, x, y0 + ch + 8, cap, FONT_SMALL, LABEL)

    # explanatory lines, centred across the whole width (no per-card crowding).
    # Kept short so they never clip the 256px canvas at font size 11.
    detail_y = y0 + ch + 28
    for j, line in enumerate((
        "Scanner saves a build to a disc.",
        "Discs are portable & found in loot.",
        "Load, press Start, build with RF + FU.",
    )):
        _text_center(draw, W // 2, detail_y + j * 13, line, FONT_SMALL, LABEL_DIM)

    # footer rule
    _text_center(draw, W // 2, CONTENT_H - 14,
                 "powered by RF + Filament Units (FU)", FONT_SMALL, ACCENT_DIM)
    return img


# ---------------------------------------------------------------------------
# Driver
# ---------------------------------------------------------------------------
def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    written = []

    for tier, edge in ((5, 3), (6, 5), (7, 7), (8, 9)):
        img = gen_layout(tier, edge)
        path = os.path.join(OUT_DIR, f"layout_{edge}x{edge}.png")
        img.save(path)
        written.append(path)

    gui = gen_printer_gui()
    gui_path = os.path.join(OUT_DIR, "printer_gui.png")
    gui.save(gui_path)
    written.append(gui_path)

    flow = gen_print_flow()
    flow_path = os.path.join(OUT_DIR, "print_flow.png")
    flow.save(flow_path)
    written.append(flow_path)

    for p in written:
        print("wrote", p)


if __name__ == "__main__":
    main()
