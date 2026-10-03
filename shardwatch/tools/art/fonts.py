"""
Stage 4 — chat sigils: eight small crystal glyphs in the "shardwatch:sigils" font, shown before staff names.
Glyphs are drawn at 16x16 with the same shading engine (a 64x64 glyph would be shrunk to text height and lose its
pixel art), then packed into one 64x32 atlas.
"""
import math

import numpy as np

from art.tools import diamond, shard
from generate_pack import save_json, save_png
from pixelart import Canvas

# Order matches dev.shardwatch.progress.SigilService.GLYPHS (U+E100 …).
ORDER = ['shardling', 'prismkeeper', 'lumenwarden', 'crownfacet', 'sigil_heart', 'sigil_star', 'sigil_moon', 'sigil_bloom']


def g_shardling(c):
    c.gem(shard(8, 8, 15, 7, -90), 'teal_gem', depth=1, table_scale=0.4)
    c.gem(diamond(8, 8, 2, 3), 'pink_gem', depth=1.2)


def g_prismkeeper(c):
    c.gem(shard(6, 8, 14, 5, -70), 'pink_gem', depth=1, table_scale=0.35)
    c.gem(shard(10, 8, 14, 5, -110), 'teal_gem', depth=1.2, table_scale=0.35)


def g_lumenwarden(c):
    c.plate(c.ellipse(8, 9, 7, 4.5), 'paper', depth=1, bevel=1)
    c.orb(8, 9, 3, 'teal_gem', depth=1.2)
    c.add(c.circle(8, 9, 1.2), 'pink_gem', depth=1.4)
    c.gem([(8, 0.5), (10, 3.5), (6, 3.5)], 'pink_gem', depth=1.2, table_scale=0.3)


def g_crownfacet(c):
    c.plate(c.poly([(1, 13), (1, 4), (4.5, 8), (8, 2), (11.5, 8), (15, 4), (15, 13)]), 'gold', depth=1, bevel=1)
    c.add(c.circle(8, 3, 1.6), 'pink_gem', depth=1.2)
    c.add(c.circle(1.8, 4.2, 1.4) | c.circle(14.2, 4.2, 1.4), 'teal_gem', depth=1.2)


def g_heart(c):
    m = c.circle(5, 6, 3.6) | c.circle(11, 6, 3.6) | c.poly([(1.6, 7), (14.4, 7), (8, 14.5)])
    n, h = c.dome(m, 6, 6, 9, 0.8)
    c.add(m, 'pink_gem', n, h, depth=1)
    c.add(c.circle(5, 5, 1.2), 'teal_gem', depth=1.2)


def g_star(c):
    pts = []
    for i in range(8):
        r = 7.5 if i % 2 == 0 else 2.6
        a = math.radians(-90 + i * 45)
        pts.append((8 + math.cos(a) * r, 8 + math.sin(a) * r))
    c.gem(pts, 'ice_gem', depth=1, table_scale=0.3)
    c.add(c.circle(8, 8, 1.5), 'pink_gem', depth=1.2)
    c.add(c.circle(13, 3, 0.9), 'teal_gem', depth=1.2)


def g_moon(c):
    m = c.circle(8, 8, 7) & ~c.circle(11, 6, 5.5)
    n, h = c.dome(m, 6, 7, 8, 0.8)
    c.add(m, 'teal_gem', n, h, depth=1)
    c.add(c.circle(12.5, 11.5, 1.2), 'pink_gem', depth=1.2)


def g_bloom(c):
    for k in range(5):
        a = math.radians(-90 + k * 72)
        c.orb(8 + math.cos(a) * 4, 8 + math.sin(a) * 4, 3, 'pink_gem', depth=1)
    c.orb(8, 8, 2.2, 'teal_gem', depth=1.3)


FUNCS = {'shardling': g_shardling, 'prismkeeper': g_prismkeeper, 'lumenwarden': g_lumenwarden,
         'crownfacet': g_crownfacet, 'sigil_heart': g_heart, 'sigil_star': g_star, 'sigil_moon': g_moon,
         'sigil_bloom': g_bloom}


def build_extra(ns):
    atlas = np.zeros((32, 64, 4), np.uint8)
    for i, name in enumerate(ORDER):
        c = Canvas(16, 16)
        FUNCS[name](c)
        albedo, *_ = c.render()
        x, y = (i % 4) * 16, (i // 4) * 16
        atlas[y:y + 16, x:x + 16] = albedo
    save_png(atlas, ns / 'textures' / 'font' / 'sigils.png')
    chars = [''.join(chr(0xE100 + r * 4 + k) for k in range(4)) for r in range(2)]
    save_json({'providers': [{'type': 'bitmap', 'file': 'shardwatch:font/sigils.png', 'ascent': 8, 'height': 9,
                              'chars': chars}]}, ns / 'font' / 'sigils.json')
