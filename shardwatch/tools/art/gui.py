"""
Stage 3 — GUI art that is not an item:

  * tooltip style "shardwatch:crystal" (nine-slice background + frame, 64x64 each),
  * menu backgrounds drawn through the custom font "shardwatch:gui" (a bitmap glyph the size of a chest screen,
    with transparent holes over every slot so items stay visible), plus negative-space glyphs to position it.
"""
import json

import numpy as np

from generate_pack import save_json, save_png
from pixelart import RAMP_RGB

PINK, ROSE, TEAL, ICE, SILVER, PLUM = (RAMP_RGB[k] for k in ('pink', 'rose', 'teal', 'ice', 'silver', 'plum'))


def rgba(c, a=255):
    return (*c, a)


def blank(w, h):
    return np.zeros((h, w, 4), np.uint8)


def gem_px(img, cx, cy, r, ramp):
    """A tiny diamond gem lit from the top-left."""
    for y in range(cy - r, cy + r + 1):
        for x in range(cx - r, cx + r + 1):
            d = abs(x - cx) + abs(y - cy)
            if d > r:
                continue
            if d == r:
                col = ramp[1] if (x - cx) + (y - cy) > 0 else ramp[3]
            elif x <= cx and y <= cy:
                col = ramp[6]
            elif x > cx and y > cy:
                col = ramp[3]
            else:
                col = ramp[5]
            img[y, x] = rgba(col)
    img[cy - r + 1, cx - 1 if r > 2 else cx] = rgba(ramp[7])


# ------------------------------------------------------------------ tooltip
def tooltip_background():
    img = blank(64, 64)
    for y in range(8, 56):
        for x in range(8, 56):
            if (x in (8, 55)) and (y in (8, 55)):
                continue
            # Two plum tones with a checker-dithered seam, light at the top.
            if y < 30 or (y < 34 and (x + y) % 2 == 0):
                col = PLUM[1]
            else:
                col = PLUM[0]
            img[y, x] = rgba(col, 240)
    for x in range(10, 54):
        img[10, x] = rgba(PLUM[2], 240)
    return img


def tooltip_frame():
    img = blank(64, 64)
    for y in range(8, 56):
        t = (y - 8) / 47
        outer = PINK[5] if t < 0.4 else TEAL[5] if t > 0.6 else (PINK[5] if y % 2 else TEAL[5])
        inner = PINK[2] if t < 0.5 else TEAL[2]
        for x in (8, 55):
            if y not in (8, 55):
                img[y, x] = rgba(outer)
        for x in (9, 54):
            if 9 < y < 54:
                img[y, x] = rgba(inner)
    for x in range(9, 55):
        img[8, x] = rgba(PINK[6] if x < 40 else PINK[5])
        img[55, x] = rgba(TEAL[4])
        img[9, x] = rgba(PINK[2])
        img[54, x] = rgba(TEAL[2])
    gem_px(img, 4, 4, 3, PINK)
    gem_px(img, 59, 4, 3, PINK)
    gem_px(img, 4, 59, 3, TEAL)
    gem_px(img, 59, 59, 3, TEAL)
    return img


NINE = {'gui': {'scaling': {'type': 'nine_slice', 'width': 64, 'height': 64, 'border': 9}}}


# ------------------------------------------------------------------ menu backgrounds
def menu_background(rows):
    """176 x (114 + rows*18) chest overlay with transparent slot holes."""
    h = 114 + rows * 18
    img = blank(176, h)
    inv_label_y = h - 94
    # Panel fill: pink -> silver -> ice, ordered-dithered seams, diagonal facet streaks lit from the top-left.
    bands = [PINK[7], SILVER[6], ICE[6]]
    for y in range(h):
        t = y / h * 3
        i = min(2, int(t))
        frac = t - i
        for x in range(176):
            col = bands[i]
            if i < 2 and frac > 0.8 and (x + y) % 2 == 0:
                col = bands[i + 1]
            if (x + y) % 37 in (0, 1) and (x + y) < h:
                col = (255, 255, 255)
            elif (x + y) % 37 == 2 and (x + y) < h:
                col = PINK[6] if i == 0 else ICE[5]
            img[y, x] = rgba(col)
    # Title band and inventory label band.
    for y in range(3, 16):
        for x in range(4, 172):
            img[y, x] = rgba(PINK[7] if y > 4 else PINK[6])
    for y in range(inv_label_y - 2, inv_label_y + 10):
        for x in range(4, 172):
            img[y, x] = rgba(ICE[6])
    # Frame: 3 px, light on top/left, shadow on bottom/right.
    for y in range(h):
        for x in range(176):
            edge = min(x, y, 175 - x, h - 1 - y)
            if edge > 2:
                continue
            top_left = x + y < (175 - x) + (h - 1 - y)
            ramp = PINK if y < h / 2 else TEAL
            col = [ramp[1], ramp[5] if top_left else ramp[3], ramp[6] if top_left else ramp[4]][edge]
            img[y, x] = rgba(col)
    # Rounded outer corners.
    for cx, cy in ((0, 0), (175, 0), (0, h - 1), (175, h - 1)):
        img[cy, cx] = (0, 0, 0, 0)
    # Slot settings: a rose/teal rim around each slot block, holes over every slot.
    def block(x0, y0, cols, rws, ramp):
        x1, y1 = x0 + cols * 18, y0 + rws * 18
        for x in range(x0 - 2, x1 + 2):
            img[y0 - 2, x] = rgba(ramp[5])
            img[y0 - 1, x] = rgba(ramp[3])
            img[y1, x] = rgba(ramp[6])
            img[y1 + 1, x] = rgba(ramp[4])
        for y in range(y0 - 2, y1 + 2):
            img[y, x0 - 2] = rgba(ramp[5])
            img[y, x0 - 1] = rgba(ramp[3])
            img[y, x1] = rgba(ramp[6])
            img[y, x1 + 1] = rgba(ramp[4])
        img[y0:y1, x0:x1] = 0
    block(7, 17, 9, rows, PINK)
    inv_y = h - 83
    block(7, inv_y, 9, 3, TEAL)
    block(7, h - 25, 9, 1, TEAL)
    # Corner gems.
    gem_px(img, 6, 6, 3, PINK)
    gem_px(img, 169, 6, 3, PINK)
    gem_px(img, 6, h - 7, 3, TEAL)
    gem_px(img, 169, h - 7, 3, TEAL)
    gem_px(img, 88, inv_label_y + 4, 3, TEAL)
    return img


# Negative and positive space glyphs: advance in GUI pixels.
SHIFTS = {}
for i, n in enumerate((1, 2, 4, 8, 16, 32, 64, 128)):
    SHIFTS[chr(0xF801 + i)] = -n
    SHIFTS[chr(0xF821 + i)] = n


def build_extra(ns):
    sprites = ns / 'textures' / 'gui' / 'sprites' / 'tooltip'
    save_png(tooltip_background(), sprites / 'crystal_background.png')
    save_png(tooltip_frame(), sprites / 'crystal_frame.png')
    for name in ('crystal_background', 'crystal_frame'):
        save_json(NINE, sprites / f'{name}.png.mcmeta')
    fonts = ns / 'textures' / 'font'
    m6, m3 = menu_background(6), menu_background(3)
    save_png(m6, fonts / 'menu_6.png')
    save_png(m3, fonts / 'menu_3.png')
    providers = [
        {'type': 'space', 'advances': SHIFTS},
        {'type': 'bitmap', 'file': 'shardwatch:font/menu_6.png', 'ascent': 13, 'height': m6.shape[0], 'chars': ['']},
        {'type': 'bitmap', 'file': 'shardwatch:font/menu_3.png', 'ascent': 13, 'height': m3.shape[0], 'chars': ['']},
    ]
    save_json({'providers': providers}, ns / 'font' / 'gui.json')
