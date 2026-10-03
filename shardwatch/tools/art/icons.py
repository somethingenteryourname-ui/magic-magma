"""Stage 3 — GUI icons. Each is a crystal symbol with real depth so it reads as a 3D object in menus."""
import math

from art.tools import diamond, obb, shard
from generate_pack import ICON, Spec, register
from pixelart import Canvas


def star(cx, cy, r_out, r_in, points=4, rot=-90):
    pts = []
    for i in range(points * 2):
        r = r_out if i % 2 == 0 else r_in
        a = math.radians(rot + i * 180 / points)
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return pts


def chevron(c, cx, cy, size, direction, material, depth=2.0, glow=0.0, width=6):
    """A thick chevron pointing left (-1) or right (+1)."""
    d = direction
    c.rod(cx - d * size * 0.5, cy - size, cx + d * size * 0.5, cy, width, material, depth, glow)
    c.rod(cx + d * size * 0.5, cy, cx - d * size * 0.5, cy + size, width, material, depth, glow)


def bubble(c, cx, cy, w, h, material, depth=1.5):
    m = c.ellipse(cx, cy, w, h) | c.poly([(cx - w * 0.5, cy + h * 0.5), (cx - w * 0.75, cy + h * 1.35), (cx - w * 0.1, cy + h * 0.8)])
    c.plate(m, material, depth=depth, bevel=3)
    return m


# ------------------------------------------------------------------ flare + categories
def icon_flare(c, t, s):
    c.gem(star(32, 32, 27, 9, 4), 'pink_gem', depth=2.0, table_scale=0.3)
    c.gem(star(32, 32, 16, 6, 4, rot=-45), 'teal_gem', depth=2.5, glow=0.7, table_scale=0.3)
    c.orb(32, 32, 5, 'ice_gem', depth=3.0, glow=1.0)
    c.sparkle(22, 20, 2)


def icon_flare_hacking(c, t, s):
    c.plate(c.ring(32, 32, 17, 22), 'teal_gem', depth=1.75, bevel=2)
    for x0, y0, x1, y1 in ((32, 4, 32, 18), (32, 46, 32, 60), (4, 32, 18, 32), (46, 32, 60, 32)):
        c.rod(x0, y0, x1, y1, 4.5, 'teal_gem', depth=1.75)
    c.gem(diamond(32, 32, 10, 13), 'pink_gem', depth=2.5, glow=0.8)
    c.sparkle(28, 25, 2)


def icon_flare_griefing(c, t, s):
    cube_top = [(32, 8), (55, 19), (32, 30), (9, 19)]
    c.plate(c.poly(cube_top), 'teal_gem', depth=2, bevel=2)
    c.plate(c.poly([(9, 19), (32, 30), (32, 57), (9, 45)]), 'teal_cloth', depth=2, bevel=2)
    c.plate(c.poly([(32, 30), (55, 19), (55, 45), (32, 57)]), 'teal_gem', depth=2, bevel=2)
    c.rod(34, 9, 26, 24, 3, 'rose_gem', depth=2.4, glow=1.0)
    c.rod(26, 24, 36, 38, 3, 'rose_gem', depth=2.4, glow=1.0)
    c.rod(36, 38, 30, 54, 3, 'rose_gem', depth=2.4, glow=1.0)
    c.sparkle(20, 17, 2)


def icon_flare_chat(c, t, s):
    bubble(c, 32, 28, 25, 18, 'teal_gem')
    for x in (21, 32, 43):
        c.orb(x, 28, 4, 'pink_gem', depth=2.25, glow=0.8)
    c.sparkle(18, 17, 2)


def icon_flare_exploit(c, t, s):
    c.gem(diamond(25, 30, 14, 20), 'teal_gem', depth=1.75, table_scale=0.45)
    c.gem(diamond(39, 34, 14, 20), 'pink_gem', depth=2.5, glow=0.5, table_scale=0.45)
    c.sparkle(36, 22, 2)


def icon_flare_other(c, t, s):
    c.plate(c.ring(32, 21, 6, 16) & ~c.rect(14, 21, 32, 40), 'pink_gem', depth=2.25, bevel=2.5)
    c.rod(32, 31, 32, 42, 9, 'pink_gem', depth=2.25)
    c.orb(32, 52, 5, 'teal_gem', depth=2.5, glow=1.0)
    c.sparkle(26, 12, 2)


# ------------------------------------------------------------------ verdicts
def icon_chip(c, t, s):
    c.gem([(32, 6), (54, 24), (44, 56), (20, 56), (10, 24)], 'pink_gem', depth=2.5, table_scale=0.45)
    notch = c.poly([(38, 3), (64, 3), (64, 31), (49, 21)])
    for layer in c.layers:
        layer.mask &= ~notch
    c.gem([(51, 4), (61, 8), (57, 15)], 'teal_gem', depth=2.0, glow=0.8, table_scale=0.3)
    c.sparkle(24, 20, 2)


def icon_hush(c, t, s):
    bubble(c, 30, 28, 24, 18, 'teal_gem')
    c.rod(10, 50, 52, 8, 6, 'rose_gem', depth=2.5, glow=0.9)
    c.sparkle(18, 16, 2)


def icon_eject(c, t, s):
    c.plate(c.rect(6, 8, 30, 56) & ~c.rect(11, 13, 25, 51), 'silver', depth=1.75, bevel=1.5)
    c.plate(c.rect(11, 13, 25, 51), 'teal_cloth', depth=1.25, bevel=2)
    c.rod(18, 32, 46, 32, 7, 'pink_gem', depth=2.5, glow=0.6)
    c.gem([(40, 18), (60, 32), (40, 46)], 'pink_gem', depth=2.75, glow=0.6, table_scale=0.35)
    c.sparkle(44, 26, 1)


def icon_encase(c, t, s):
    c.gem([(32, 3), (56, 16), (56, 48), (32, 61), (8, 48), (8, 16)], 'teal_gem', depth=2.0, table_scale=0.6, tilt=0.6)
    c.orb(32, 24, 6, 'rose_gem', depth=2.25, glow=0.8)
    c.plate(c.poly([(22, 46), (25, 33), (39, 33), (42, 46)]), 'rose_gem', depth=2.25, bevel=2, glow=0.8)
    for x in (16, 32, 48):
        c.rod(x, 8, x, 56, 2.5, 'ice_gem', depth=2.75)
    c.sparkle(20, 14, 2)


def icon_petrify(c, t, s):
    for k in range(6):
        a = math.radians(k * 60 - 90)
        x1, y1 = 32 + math.cos(a) * 27, 32 + math.sin(a) * 27
        c.rod(32, 32, x1, y1, 5, 'ice_gem', depth=2.0)
        for f in (0.55,):
            bx, by = 32 + math.cos(a) * 27 * f, 32 + math.sin(a) * 27 * f
            for side in (-1, 1):
                b = a + side * math.radians(40)
                c.rod(bx, by, bx + math.cos(b) * 8, by + math.sin(b) * 8, 3.5, 'ice_gem', depth=2.0)
    c.gem(diamond(32, 32, 8, 8), 'teal_gem', depth=2.5, glow=0.9)
    c.orb(32, 32, 3, 'pink_gem', depth=2.75, glow=1.0)
    c.sparkle(22, 16, 2)


def icon_ledger(c, t, s):
    c.plate(c.poly([(4, 14), (32, 20), (60, 14), (60, 54), (32, 60), (4, 54)]), 'rose_gem', depth=1.5, bevel=1.5)
    c.plate(c.poly([(8, 12), (31, 17), (31, 54), (8, 49)]), 'paper', depth=2.0, bevel=2)
    c.plate(c.poly([(33, 17), (56, 12), (56, 49), (33, 54)]), 'paper', depth=2.0, bevel=2)
    for i in range(4):
        y = 24 + i * 7
        c.rod(12, y - 1 + i * 0.4, 27, y + 2 + i * 0.4, 1.6, 'teal_gem', depth=2.1)
        c.rod(37, y + 2 + i * 0.4, 52, y - 1 + i * 0.4, 1.6, 'teal_gem', depth=2.1)
    c.gem(diamond(32, 18, 3, 4), 'pink_gem', depth=2.5, glow=0.8)


def icon_rewind(c, t, s):
    ring = c.ring(32, 32, 16, 24) & ~c.poly([(32, 32), (64, 0), (64, 30)])
    c.plate(ring, 'teal_gem', depth=2.0, bevel=2.5)
    c.gem([(46, 2), (60, 20), (40, 22)], 'teal_gem', depth=2.25, glow=0.6, table_scale=0.3)
    c.plate(c.rect(26, 22, 38, 25), 'gold', depth=2.0, bevel=1)
    c.plate(c.rect(26, 39, 38, 42), 'gold', depth=2.0, bevel=1)
    c.plate(c.poly([(27, 25), (37, 25), (33, 32), (31, 32)]), 'pink_gem', depth=2.25, bevel=1.5, glow=0.8)
    c.plate(c.poly([(31, 32), (33, 32), (37, 39), (27, 39)]), 'pink_gem', depth=2.25, bevel=1.5, glow=0.8)
    c.sparkle(18, 20, 2)


def icon_glint(c, t, s):
    c.plate(c.ellipse(32, 34, 27, 15), 'paper', depth=1.75, bevel=3)
    iris = c.circle(32, 34, 11)
    n, h = c.dome(iris, 29, 31, 12)
    c.add(iris, 'teal_gem', n, h, depth=2.25, glow=0.6)
    c.gem(diamond(32, 34, 4.5, 6), 'pink_gem', depth=2.5, glow=1.0)
    c.gem(star(50, 13, 9, 2.5), 'ice_gem', depth=2.0, glow=1.0, table_scale=0.2)
    c.sparkle(25, 29, 2)


def icon_facet(c, t, s):
    c.plate(c.rect(10, 40, 54, 52), 'gold', depth=2.0, bevel=2)
    crown = c.poly([(10, 42), (8, 14), (21, 28), (32, 8), (43, 28), (56, 14), (54, 42)])
    c.plate(crown, 'gold', depth=1.75, bevel=3)
    for x, y, m in ((8, 13, 'teal_gem'), (32, 7, 'pink_gem'), (56, 13, 'teal_gem')):
        c.orb(x, y, 4.5, m, depth=2.5, glow=0.8)
    c.gem(diamond(32, 46, 4, 4), 'pink_gem', depth=2.4)
    c.sparkle(26, 24, 2)


def icon_scope(c, t, s):
    win = c.rect(5, 9, 59, 55)
    c.plate(win, 'silver', depth=1.5, bevel=1.5)
    c.plate(c.rect(9, 19, 55, 51), 'paper', depth=1.75, bevel=2)
    for i, (x, m) in enumerate(((11, 'rose_gem'), (17, 'teal_gem'), (23, 'ice_gem'))):
        c.orb(x, 14, 2.4, m, depth=2.0, glow=0.6)
    c.plate(c.rect(29, 12, 52, 16), 'teal_cloth', depth=1.75, bevel=1)
    for i in range(4):
        c.rod(14, 25 + i * 6.5, 44 - (i % 2) * 10, 25 + i * 6.5, 2.4, 'teal_gem' if i % 2 else 'pink_gem', depth=2.0)
    c.orb(48, 42, 5, 'pink_gem', depth=2.25, glow=0.8)


# ------------------------------------------------------------------ progression
def icon_lustre(c, t, s):
    c.gem(star(32, 33, 28, 12, 5), 'pink_gem', depth=2.5, table_scale=0.35)
    c.gem(star(32, 33, 12, 6, 5), 'teal_gem', depth=3.0, glow=1.0, table_scale=0.3)
    c.sparkle(24, 20, 2).sparkle(46, 46, 1)


def icon_refine(c, t, s):
    c.gem([(32, 30), (50, 42), (32, 60), (14, 42)], 'pink_gem', depth=2.25, table_scale=0.4)
    for i, y in enumerate((22, 10)):
        c.rod(16, y + 12, 32, y, 6, 'teal_gem', depth=2.5, glow=0.6 + 0.3 * i)
        c.rod(32, y, 48, y + 12, 6, 'teal_gem', depth=2.5, glow=0.6 + 0.3 * i)
    c.sparkle(26, 40, 2)


def icon_keepsake(c, t, s):
    c.plate(c.rect(8, 26, 56, 58), 'rose_gem', depth=2.0, bevel=2.5)
    c.plate(c.rect(5, 18, 59, 28), 'pink_gem', depth=2.4, bevel=2)
    c.plate(c.rect(28, 18, 36, 58), 'teal_gem', depth=2.6, bevel=1.5, glow=0.5)
    c.gem([(32, 18), (14, 6), (18, 18)], 'teal_gem', depth=2.75, glow=0.6, table_scale=0.3)
    c.gem([(32, 18), (50, 6), (46, 18)], 'teal_gem', depth=2.75, glow=0.6, table_scale=0.3)
    c.sparkle(14, 32, 2)


def icon_locked(c, t, s):
    c.plate(c.ring(32, 24, 9, 15) & c.rect(0, 0, 64, 30), 'silver', depth=1.75, bevel=2)
    c.plate(c.rect(12, 28, 52, 58), 'silver', depth=2.25, bevel=3)
    c.orb(32, 39, 5, 'pink_gem', depth=2.5, glow=0.8)
    c.plate(c.poly([(30, 40), (34, 40), (35, 51), (29, 51)]), 'pink_gem', depth=2.5, bevel=1, glow=0.8)
    c.gem(diamond(46, 34, 3, 3), 'teal_gem', depth=2.5)


# ------------------------------------------------------------------ controls
def icon_confirm(c, t, s):
    c.rod(10, 34, 26, 50, 9, 'teal_gem', depth=2.25, glow=0.5)
    c.rod(26, 50, 54, 14, 9, 'teal_gem', depth=2.25, glow=0.5)
    c.gem(diamond(26, 50, 5, 5), 'pink_gem', depth=2.75, glow=0.9)
    c.sparkle(46, 20, 2)


def icon_cancel(c, t, s):
    c.rod(12, 12, 52, 52, 9, 'rose_gem', depth=2.25, glow=0.5)
    c.rod(52, 12, 12, 52, 9, 'rose_gem', depth=2.25, glow=0.5)
    c.gem(diamond(32, 32, 6, 6), 'teal_gem', depth=2.75, glow=0.9)
    c.sparkle(18, 16, 2)


def icon_prev(c, t, s):
    chevron(c, 30, 32, 18, -1, 'teal_gem', 2.25, 0.4, width=8)
    c.gem(diamond(48, 32, 5, 5), 'pink_gem', depth=2.5, glow=0.9)


def icon_next(c, t, s):
    chevron(c, 34, 32, 18, 1, 'teal_gem', 2.25, 0.4, width=8)
    c.gem(diamond(16, 32, 5, 5), 'pink_gem', depth=2.5, glow=0.9)


def icon_close(c, t, s):
    c.plate(c.ring(32, 32, 20, 28), 'silver', depth=1.75, bevel=2)
    c.rod(22, 22, 42, 42, 6, 'rose_gem', depth=2.25, glow=0.5)
    c.rod(42, 22, 22, 42, 6, 'rose_gem', depth=2.25, glow=0.5)
    c.gem(diamond(32, 5, 3, 3), 'teal_gem', depth=2.25)


def icon_filter(c, t, s):
    c.plate(c.poly([(6, 8), (58, 8), (38, 32), (38, 54), (26, 60), (26, 32)]), 'teal_gem', depth=2.0, bevel=2.5)
    for i, m in enumerate(('pink_gem', 'ice_gem', 'pink_gem')):
        c.orb(20 + i * 12, 15, 3.4, m, depth=2.5, glow=0.7)


def icon_info(c, t, s):
    disc = c.circle(32, 32, 27)
    n, h = c.dome(disc, 28, 28, 30, 0.45)
    c.add(disc, 'ice_gem', n, h, depth=1.75)
    c.orb(32, 17, 5, 'pink_gem', depth=2.5, glow=0.9)
    c.rod(32, 28, 32, 48, 8, 'teal_gem', depth=2.25)
    c.sparkle(20, 22, 2)


def icon_settings(c, t, s):
    teeth = []
    for k in range(16):
        r = 28 if k % 2 == 0 else 21
        a = math.radians(k * 22.5 - 90)
        teeth.append((32 + math.cos(a) * r, 32 + math.sin(a) * r))
    c.plate(c.poly(teeth) & ~c.circle(32, 32, 9), 'silver', depth=2.0, bevel=2.5)
    c.gem(diamond(32, 32, 8, 8), 'teal_gem', depth=2.5, glow=0.8)
    c.orb(32, 32, 3, 'pink_gem', depth=2.75, glow=1.0)


def icon_clock(c, t, s):
    c.plate(c.circle(32, 32, 28), 'gold', depth=1.75, bevel=2.5)
    face = c.circle(32, 32, 22)
    n, h = c.dome(face, 29, 29, 24, 0.4)
    c.add(face, 'paper', n, h, depth=2.0)
    for k in range(12):
        a = math.radians(k * 30)
        c.orb(32 + math.cos(a) * 18, 32 + math.sin(a) * 18, 1.4 if k % 3 else 2.2, 'teal_gem', depth=2.25)
    c.rod(32, 32, 32, 15, 3.6, 'pink_gem', depth=2.5, glow=0.8)
    c.rod(32, 32, 44, 38, 3.0, 'teal_gem', depth=2.5, glow=0.8)
    c.orb(32, 32, 2.6, 'gold', depth=2.75)


def icon_pane(c, t, s):
    c.plate(c.rect(1, 1, 63, 63), 'paper', depth=0.5, bevel=5)
    c.gem(diamond(32, 32, 3.5, 3.5), 'pink_gem', depth=0.75, glow=0.3)
    for x, y in ((7, 7), (57, 57)):
        c.gem(diamond(x, y, 2.5, 2.5), 'teal_gem', depth=0.75)


# The background filler faces the viewer square-on so neighbouring slots tile into one surface.
PANE = {**ICON, 'gui': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}}

ICON_FUNCS = {name: fn for name, fn in globals().items() if name.startswith('icon_') and callable(fn)}
for _name, _fn in ICON_FUNCS.items():
    register(Spec(_name, 'icon', _fn, PANE if _name == 'icon_pane' else ICON))
