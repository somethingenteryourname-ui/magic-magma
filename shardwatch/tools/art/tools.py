"""
Stage 2 — staff tools. Every tool is drawn diagonally (grip bottom-left, head top-right) like vanilla tools, so the
vanilla "handheld" display angles fit. Tier II adds rose-gold trim and gems, tier III adds a crown of crystals.
"""
import math

from generate_pack import HANDHELD, HELD_ITEM, Spec, register
from pixelart import Canvas


# ------------------------------------------------------------------ shared helpers
def obb(cx, cy, half_len, half_wid, angle_deg):
    """Corners of a rotated rectangle."""
    a = math.radians(angle_deg)
    ux, uy = math.cos(a), math.sin(a)
    px, py = -uy, ux
    return [(cx + ux * half_len + px * half_wid, cy + uy * half_len + py * half_wid),
            (cx - ux * half_len + px * half_wid, cy - uy * half_len + py * half_wid),
            (cx - ux * half_len - px * half_wid, cy - uy * half_len - py * half_wid),
            (cx + ux * half_len - px * half_wid, cy + uy * half_len - py * half_wid)]


def diamond(cx, cy, rx, ry):
    return [(cx, cy - ry), (cx + rx, cy), (cx, cy + ry), (cx - rx, cy)]


def shard(cx, cy, length, width, angle_deg):
    """An elongated hexagonal crystal pointing along angle."""
    a = math.radians(angle_deg)
    ux, uy = math.cos(a), math.sin(a)
    px, py = -uy, ux
    L, W = length / 2, width / 2
    pts = [(L, 0), (L * 0.55, W), (-L * 0.6, W), (-L, 0), (-L * 0.6, -W), (L * 0.55, -W)]
    return [(cx + ux * x + px * y, cy + uy * x + py * y) for x, y in pts]


def handle(c: Canvas, x0, y0, x1, y1, width=7.5, bands=2, tier=1):
    """Plum-wood grip with silver (tier I) or rose-gold (tier II+) bands and a pommel gem."""
    c.rod(x0, y0, x1, y1, width, 'plum_wood', depth=1.25)
    trim = 'gold' if tier >= 2 else 'silver'
    for i in range(bands):
        t = 0.25 + 0.5 * i / max(1, bands - 1) if bands > 1 else 0.5
        bx, by = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        ang = math.degrees(math.atan2(y1 - y0, x1 - x0))
        c.plate(c.poly(obb(bx, by, 1.6, width / 2 + 1.2, ang)), trim, depth=1.6, bevel=1)
    # Pommel
    c.orb(x0 - (x1 - x0) * 0.06, y0 - (y1 - y0) * 0.06, 3.6, 'gold' if tier >= 2 else 'silver', depth=1.75)
    gem = 'pink_gem' if tier < 3 else 'rose_gem'
    c.orb(x0 - (x1 - x0) * 0.06, y0 - (y1 - y0) * 0.06, 2.0, gem, depth=2.0, glow=0.5 if tier >= 2 else 0)


def crown_spikes(c: Canvas, cx, cy, r, material='teal_gem', count=5, start=-150, spread=120, size=4.5, glow=0.6):
    for i in range(count):
        ang = math.radians(start + spread * i / max(1, count - 1))
        x, y = cx + math.cos(ang) * r, cy + math.sin(ang) * r
        c.gem(shard(x, y, size * 2.2, size, math.degrees(ang)), material, depth=1.5, glow=glow, table_scale=0.35)


# ------------------------------------------------------------------ Echo Lens
def echo_lens(c: Canvas, tier, state):
    handle(c, 10, 55, 27, 38, bands=2, tier=tier)
    cx, cy, r = 39, 25, 16
    if tier >= 3:
        crown_spikes(c, cx, cy, r + 2, 'pink_gem', count=5, start=-160, spread=140)
    ring = 'gold' if tier >= 2 else 'silver'
    c.plate(c.ring(cx, cy, r - 4, r), ring, depth=2.0, bevel=1.6)
    lens = c.circle(cx, cy, r - 4)
    n, h = c.dome(lens, cx - 2, cy - 2, r - 3, 0.8)
    c.add(lens, 'ice_gem', n, h, depth=1.5, glow=0.45)
    # Etched echo rings inside the glass.
    c.plate(c.ring(cx + 1, cy + 1, 6, 7), 'teal_gem', depth=1.6, bevel=1, glow=0.8)
    c.plate(c.ring(cx + 1, cy + 1, 2, 3), 'teal_gem', depth=1.6, bevel=1, glow=0.8)
    if tier >= 2:
        for ang in (45, 135, 225, 315):
            a = math.radians(ang)
            c.gem(diamond(cx + math.cos(a) * (r - 2), cy + math.sin(a) * (r - 2), 2.6, 2.6), 'pink_gem', depth=2.4,
                  glow=0.5)
    c.sparkle(cx - 6, cy - 6, 2).sparkle(cx + 5, cy - 2, 1)


# ------------------------------------------------------------------ Timeglass
def timeglass_frame(c: Canvas, tier):
    trim = 'gold' if tier >= 2 else 'silver'
    c.plate(c.rect(31, 3, 53, 8), trim, depth=2.0, bevel=1.2)
    c.plate(c.rect(31, 37, 53, 42), trim, depth=2.0, bevel=1.2)
    c.rod(33, 7, 33, 38, 3, trim, depth=1.75)
    c.rod(51, 7, 51, 38, 3, trim, depth=1.75)
    if tier >= 2:
        c.gem(diamond(42, 5.5, 3, 2.4), 'pink_gem', depth=2.4, glow=0.5)
        c.gem(diamond(42, 39.5, 3, 2.4), 'teal_gem', depth=2.4, glow=0.5)
    if tier >= 3:
        crown_spikes(c, 42, 4, 2, 'pink_gem', count=3, start=-135, spread=90, size=3.6)


def bulbs(c: Canvas):
    top = c.poly([(35, 8), (49, 8), (49, 13), (43.5, 22.5), (40.5, 22.5), (35, 13)])
    bot = c.poly([(40.5, 22.5), (43.5, 22.5), (49, 32), (49, 37), (35, 37), (35, 32)])
    return top, bot


def timeglass(c: Canvas, tier, state):
    handle(c, 8, 58, 30, 42, bands=2, tier=tier)
    timeglass_frame(c, tier)
    top, bot = bulbs(c)
    n, h = c.bevel(top | bot, 2.5)
    c.add(top | bot, 'ice_gem', n, h, depth=1.5)
    drained = state == 'drained'
    # Sand: the static part of the sand lives in the base texture; the stream is animated in the glow layer.
    upper = c.poly([(36, 12), (48, 12), (43, 20), (41, 20)]) & top
    lower = c.poly([(36, 36.5), (48, 36.5), (48, 33), (42, 27), (36, 33)]) & bot
    if drained:
        lower = c.poly([(36, 36.5), (48, 36.5), (48, 30), (42, 24), (36, 30)]) & bot
        c.plate(lower, 'pink_gem', depth=1.6, bevel=1.5)
    else:
        c.plate(upper, 'pink_gem', depth=1.6, bevel=1.5)
        c.plate(lower, 'pink_gem', depth=1.6, bevel=1.5)
    c.sparkle(37, 10, 1).sparkle(47, 31, 1)


def timeglass_glow(c: Canvas, t, tier, state):
    """Falling sand: a stream of grains drops through the neck; the upper heap shimmers. Drained: embers only."""
    top, bot = bulbs(c)
    if state == 'drained':
        k = int(t * 8)
        c.add(c.rect(41 + (k % 3), 34, 42 + (k % 3), 35) & bot, 'rose_gem', depth=1.6, glow=0.6)
        return
    for i in range(5):
        y = 21 + ((t * 15 + i * 3.1) % 14)
        c.add(c.rect(41, y, 43, y + 2), 'pink_gem', depth=1.6, glow=1.0)
    upper = c.poly([(36, 12), (48, 12), (43, 20), (41, 20)]) & top
    c.add(upper & c.rect(36, 12, 48, 12 + 2 + 2 * math.sin(t * 2 * math.pi) ** 2), 'rose_gem', depth=1.6, glow=0.8)


# ------------------------------------------------------------------ Verdict Gavel
def verdict_gavel(c: Canvas, tier, state):
    handle(c, 9, 57, 35, 31, bands=3 if tier >= 2 else 2, tier=tier)
    cx, cy = 42, 22
    head = obb(cx, cy, 15, 7.5, 45)
    c.gem(head, 'pink_gem', depth=3.0, table_scale=0.55, tilt=0.7)
    trim = 'gold' if tier >= 2 else 'silver'
    for s in (-1, 1):
        ex, ey = cx + s * 0.707 * 13.5, cy + s * 0.707 * 13.5
        c.plate(c.poly(obb(ex, ey, 2.4, 8.4, 45)), trim, depth=3.5, bevel=1.2)
    # Glowing teal vein through the crystal head.
    c.rod(cx - 7, cy - 7, cx + 7, cy + 7, 2.2, 'teal_gem', depth=3.1, glow=1.0)
    if tier >= 2:
        c.gem(diamond(cx, cy, 3.4, 3.4), 'teal_gem', depth=3.6, glow=0.9)
    if tier >= 3:
        crown_spikes(c, cx + 4, cy - 4, 9, 'teal_gem', count=3, start=-90, spread=90, size=3.6)
    c.sparkle(cx - 5, cy - 9, 2)


# ------------------------------------------------------------------ Petrify Prism
def petrify_prism(c: Canvas, tier, state):
    # Short wrapped grip
    c.rod(9, 57, 21, 45, 8.5, 'cloth', depth=1.5)
    for i in range(4):
        bx, by = 10.5 + i * 3.2, 55.5 - i * 3.2
        c.plate(c.poly(obb(bx, by, 0.8, 4.2, -45)), 'rose_gem' if tier < 2 else 'gold', depth=1.75, bevel=0.8)
    c.plate(c.poly(obb(23, 43, 2.4, 7.5, -45)), 'gold' if tier >= 2 else 'silver', depth=2.0, bevel=1)
    # The prism: a long ice crystal with a glowing core.
    body = shard(39, 25, 42, 19, -45)
    c.gem(body, 'ice_gem', depth=3.0, table_scale=0.5, tilt=0.8)
    core = shard(39, 25, 22, 6, -45)
    c.gem(core, 'teal_gem', depth=3.2, glow=1.0, table_scale=0.4)
    if tier >= 2:
        for off in (-1, 1):
            c.gem(shard(38 + off * 7, 26 + off * 7, 11, 5, -45), 'pink_gem', depth=2.2, glow=0.5, table_scale=0.35)
    if tier >= 3:
        c.gem(shard(52, 12, 9, 5, -45), 'pink_gem', depth=3.4, glow=0.8)
    c.sparkle(33, 19, 2).sparkle(44, 27, 1)


# ------------------------------------------------------------------ Veil Lantern
def lantern_body(c: Canvas, tier):
    trim = 'gold' if tier >= 2 else 'silver'
    c.plate(c.ring(32, 10, 4, 7) & c.rect(0, 0, 64, 11), trim, depth=1.25, bevel=1)   # hanging loop
    c.plate(c.poly([(22, 18), (42, 18), (38, 11), (26, 11)]), trim, depth=2.5, bevel=1.5)  # cap
    c.plate(c.rect(21, 18, 43, 21), trim, depth=3.0, bevel=1)
    c.plate(c.rect(21, 47, 43, 51), trim, depth=3.0, bevel=1)
    c.plate(c.poly([(23, 51), (41, 51), (38, 56), (26, 56)]), trim, depth=2.5, bevel=1.2)
    glass = c.rect(23, 21, 41, 47)
    n, h = c.bevel(glass, 3)
    c.add(glass, 'teal_gem', n, h, depth=2.5)
    for x in (22, 32, 42):
        c.rod(x, 20, x, 48, 2.5, trim, depth=3.0)
    c.gem(diamond(32, 14.5, 3.4 if tier >= 2 else 2.6, 2.6), 'pink_gem', depth=3.0, glow=0.6)
    if tier >= 3:
        for x in (21, 43):
            c.gem(diamond(x, 49, 2.6, 2.6), 'pink_gem', depth=3.4, glow=0.6)


def veil_lantern(c: Canvas, tier, state):
    lantern_body(c, tier)
    c.sparkle(26, 24, 2)


def veil_lantern_glow(c: Canvas, t, tier, state):
    if state == 'unlit':
        c.orb(32, 43, 2.0 + 0.5 * math.sin(t * 2 * math.pi), 'rose_gem', depth=2.6, glow=0.5)
        return
    # Flickering crystal flame in two panes.
    sway = math.sin(t * 2 * math.pi) * 1.2
    for px in (27, 37):
        flame = c.poly([(px + sway * 0.6, 26 + abs(sway)), (px + 3.5, 38), (px + 2, 44), (px - 2, 44), (px - 3.5, 38)])
        c.add(flame, 'pink_gem', *c.dome(flame, px, 39, 6, 0.9), depth=2.6, glow=1.0)
        inner = c.poly([(px, 34 + abs(sway) * 0.5), (px + 1.6, 40), (px, 43), (px - 1.6, 40)])
        c.add(inner, 'ice_gem', depth=2.6, glow=1.0)


# ------------------------------------------------------------------ Flare Compass
def flare_compass(c: Canvas, tier, state):
    trim = 'gold' if tier >= 2 else 'silver'
    c.plate(c.ring(32, 9, 3, 6) & c.rect(0, 0, 64, 10), trim, depth=1.25, bevel=1)
    c.plate(c.circle(32, 34, 22), trim, depth=2.5, bevel=2.5)
    c.gem(diamond(32, 12.5, 2.6, 2.2), 'pink_gem', depth=2.75, glow=0.5)
    face = c.circle(32, 34, 17)
    n, h = c.dome(face, 30, 32, 18, 0.35)
    c.add(face, 'paper', n, h, depth=2.6)
    for ang in range(0, 360, 45):
        a = math.radians(ang)
        r0 = 13 if ang % 90 else 11
        c.rod(32 + math.cos(a) * r0, 34 + math.sin(a) * r0, 32 + math.cos(a) * 15.5, 34 + math.sin(a) * 15.5,
              1.6 if ang % 90 else 2.2, 'teal_gem', depth=2.7)
    c.orb(32, 34, 2.4, 'gold', depth=3.0)
    if tier >= 2:
        for ang in (0, 90, 180, 270):
            a = math.radians(ang)
            c.gem(diamond(32 + math.cos(a) * 19.5, 34 + math.sin(a) * 19.5, 2.6, 2.6), 'pink_gem', depth=3.0, glow=0.5)
    if tier >= 3:
        crown_spikes(c, 32, 34, 23, 'teal_gem', count=4, start=-135, spread=90, size=3.2)
    c.sparkle(22, 24, 2)


NEEDLES = 16


def compass_needle(k):
    """Needle k points k * 22.5 degrees clockwise from straight up (target ahead)."""
    def draw(c: Canvas, tier, state):
        a = math.radians(k * 360 / NEEDLES - 90)
        ux, uy = math.cos(a), math.sin(a)
        px, py = -uy, ux
        tip = (32 + ux * 14.5, 34 + uy * 14.5)
        tail = (32 - ux * 10, 34 - uy * 10)
        left = (32 + px * 3.6, 34 + py * 3.6)
        right = (32 - px * 3.6, 34 - py * 3.6)
        c.gem([tip, left, (32, 34), right], 'pink_gem', depth=3.4, glow=1.0, table_scale=0.3)
        c.gem([tail, right, (32, 34), left], 'teal_gem', depth=3.4, glow=0.6, table_scale=0.3)
        c.orb(32, 34, 2.2, 'ice_gem', depth=3.6, glow=1.0)
    return draw


def compass_with_needle(model):
    """Body (by tier) + a needle chosen by the vanilla compass property aimed at the item's lodestone target."""
    entries = [{'threshold': 0.0, 'model': {'type': 'minecraft:model', 'model': 'shardwatch:item/flare_compass_needle_00'}}]
    for k in range(1, NEEDLES):
        entries.append({'threshold': k - 0.5,
                        'model': {'type': 'minecraft:model', 'model': f'shardwatch:item/flare_compass_needle_{k:02d}'}})
    entries.append({'threshold': NEEDLES - 0.5,
                    'model': {'type': 'minecraft:model', 'model': 'shardwatch:item/flare_compass_needle_00'}})
    needle = {'type': 'minecraft:range_dispatch', 'property': 'minecraft:compass', 'target': 'lodestone',
              'scale': float(NEEDLES), 'entries': entries}
    return {'type': 'minecraft:composite', 'models': [model, needle]}


# ------------------------------------------------------------------ Glint Monocle
def glint_monocle(c: Canvas, tier, state):
    trim = 'gold' if tier >= 2 else 'silver'
    # Chain of links down to the bottom-left.
    for i in range(7):
        t = i / 6
        x, y = 10 + t * 14, 56 - t * 16 + math.sin(t * math.pi) * 5
        c.plate(c.ring(x, y, 1.0, 2.9), trim, depth=1.25, bevel=0.8)
    cx, cy, r = 38, 24, 16
    c.plate(c.ring(cx, cy, r - 4, r), trim, depth=2.25, bevel=1.6)
    lens = c.circle(cx, cy, r - 4)
    n, h = c.dome(lens, cx - 3, cy - 3, r - 2, 0.9)
    c.add(lens, 'teal_gem', n, h, depth=1.75, glow=0.55)
    # Glint reticle
    c.rod(cx - 6, cy, cx + 6, cy, 1.4, 'pink_gem', depth=1.9, glow=1.0)
    c.rod(cx, cy - 6, cx, cy + 6, 1.4, 'pink_gem', depth=1.9, glow=1.0)
    c.plate(c.ring(cx, cy, 3, 4.2), 'pink_gem', depth=1.9, bevel=1, glow=1.0)
    if tier >= 2:
        c.gem(diamond(cx + r - 1, cy - r + 5, 3, 3), 'pink_gem', depth=2.6, glow=0.6)
    if tier >= 3:
        crown_spikes(c, cx, cy, r + 1.5, 'ice_gem', count=4, start=-170, spread=110, size=3.4)
    c.sparkle(cx - 7, cy - 7, 2)


# ------------------------------------------------------------------ Lustre Shard
def lustre_shard(c: Canvas, tier, state):
    c.gem(shard(34, 29, 54, 26, -60), 'pink_gem', depth=3.0, table_scale=0.5, tilt=0.8)
    c.gem(shard(34, 29, 28, 10, -60), 'teal_gem', depth=3.4, glow=1.0, table_scale=0.4)
    c.gem(shard(17, 44, 16, 8, -110), 'teal_gem', depth=2.0, table_scale=0.4)
    c.gem(shard(50, 45, 14, 7, -20), 'pink_gem', depth=1.75, glow=0.4, table_scale=0.4)
    c.sparkle(28, 14, 3).sparkle(42, 33, 2).sparkle(18, 40, 1)


register(Spec('echo_lens', 'tool', echo_lens, HANDHELD, tiers=3))
register(Spec('timeglass', 'tool', timeglass, HANDHELD, tiers=3, states=['drained'], glow=timeglass_glow,
              state_rule='cooldown', frametime=2))
register(Spec('verdict_gavel', 'tool', verdict_gavel, HANDHELD, tiers=3))
register(Spec('petrify_prism', 'tool', petrify_prism, HANDHELD, tiers=3))
register(Spec('veil_lantern', 'tool', veil_lantern, HELD_ITEM, tiers=3, states=['unlit'], glow=veil_lantern_glow,
              state_rule='flag', frametime=3))
register(Spec('flare_compass', 'tool', flare_compass, HELD_ITEM, tiers=3, wrap_item=compass_with_needle))
for _k in range(NEEDLES):
    register(Spec(f'flare_compass_needle_{_k:02d}', 'part', compass_needle(_k), HELD_ITEM, part=True))
register(Spec('glint_monocle', 'tool', glint_monocle, HELD_ITEM, tiers=3))
register(Spec('lustre_shard', 'tool', lustre_shard, HELD_ITEM))
