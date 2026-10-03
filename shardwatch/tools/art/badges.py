"""Stage 2 — Facet sigils: one rank badge per Facet, each with its own silhouette and colour."""
import math

from art.tools import diamond, shard
from generate_pack import HELD_ITEM, Spec, register
from pixelart import Canvas


def sigil_shardling(c: Canvas, tier, state):
    c.plate(c.ring(32, 32, 22, 28), 'silver', depth=1.5, bevel=2)
    disk = c.circle(32, 32, 22)
    n, h = c.dome(disk, 29, 29, 26, 0.45)
    c.add(disk, 'teal_gem', n, h, depth=1.25)
    c.gem(shard(32, 32, 30, 13, -90), 'pink_gem', depth=2.5, glow=0.9, table_scale=0.45)
    for ang in (0, 120, 240):
        a = math.radians(ang + 30)
        c.gem(diamond(32 + math.cos(a) * 25, 32 + math.sin(a) * 25, 2.6, 2.6), 'pink_gem', depth=2.0)
    c.sparkle(24, 18, 2).sparkle(36, 26, 1)


def sigil_prismkeeper(c: Canvas, tier, state):
    shield = [(32, 5), (55, 13), (52, 38), (32, 59), (12, 38), (9, 13)]
    c.plate(c.poly(shield), 'gold', depth=1.5, bevel=2)
    inner = [(32, 10), (50, 16), (47, 37), (32, 53), (17, 37), (14, 16)]
    m = c.poly(inner)
    n, h = c.bevel(m, 4)
    c.add(m, 'rose_gem', n, h, depth=1.75)
    c.gem(shard(26, 31, 30, 10, -60), 'teal_gem', depth=2.75, glow=0.9, table_scale=0.4)
    c.gem(shard(38, 31, 30, 10, -120), 'teal_gem', depth=2.5, glow=0.9, table_scale=0.4)
    c.gem(diamond(32, 46, 3, 3), 'pink_gem', depth=2.5)
    c.sparkle(21, 18, 2).sparkle(40, 22, 1)


def sigil_lumenwarden(c: Canvas, tier, state):
    hexagon = [(32 + math.cos(math.radians(a)) * 28, 34 + math.sin(math.radians(a)) * 26) for a in range(-90, 270, 60)]
    c.plate(c.poly(hexagon), 'silver', depth=1.5, bevel=2)
    inner = [(32 + math.cos(math.radians(a)) * 23, 34 + math.sin(math.radians(a)) * 21) for a in range(-90, 270, 60)]
    m = c.poly(inner)
    n, h = c.bevel(m, 4)
    c.add(m, 'ice_gem', n, h, depth=1.75)
    eye = c.ellipse(32, 36, 15, 8)
    c.plate(eye, 'paper', depth=2.0, bevel=2)
    iris = c.circle(32, 36, 6.5)
    n, h = c.dome(iris, 30, 34, 7)
    c.add(iris, 'teal_gem', n, h, depth=2.4, glow=0.8)
    c.orb(32, 36, 2.6, 'rose_gem', depth=2.6, glow=1.0)
    for i, x in enumerate((24, 32, 40)):
        c.gem(shard(x, 17 - (3 if i == 1 else 0), 11, 5, -90), 'pink_gem', depth=2.2, glow=0.5, table_scale=0.35)
    c.sparkle(27, 33, 1).sparkle(20, 26, 2)


def sigil_crownfacet(c: Canvas, tier, state):
    band = c.rect(10, 40, 54, 52)
    c.plate(band, 'gold', depth=2.0, bevel=2)
    crown = c.poly([(10, 42), (8, 16), (21, 30), (32, 10), (43, 30), (56, 16), (54, 42)])
    n, h = c.bevel(crown, 3)
    c.add(crown, 'gold', n, h, depth=1.75)
    for x, y, mat in ((8, 15, 'teal_gem'), (32, 9, 'pink_gem'), (56, 15, 'teal_gem')):
        c.orb(x, y, 4.2, mat, depth=2.5, glow=0.9)
    c.gem(diamond(32, 31, 6, 8), 'pink_gem', depth=2.6, glow=0.7)
    for x, mat in ((18, 'teal_gem'), (32, 'pink_gem'), (46, 'teal_gem')):
        c.gem(diamond(x, 46, 3.6, 3.6), mat, depth=2.4, glow=0.4)
    c.sparkle(28, 26, 2).sparkle(5, 12, 1)


register(Spec('sigil_shardling', 'badge', sigil_shardling, HELD_ITEM))
register(Spec('sigil_prismkeeper', 'badge', sigil_prismkeeper, HELD_ITEM))
register(Spec('sigil_lumenwarden', 'badge', sigil_lumenwarden, HELD_ITEM))
register(Spec('sigil_crownfacet', 'badge', sigil_crownfacet, HELD_ITEM))
