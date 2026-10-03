"""
Stage 5 — particle sprites, used through Particle.ITEM with an item_model, so no vanilla particle is replaced.
Item particles show a random 16x16 crop of the 64x64 texture, so these textures are continuous crystal surfaces:
whichever piece the game picks looks like a crystal chip.
"""
import math
import random

import numpy as np

from generate_pack import ICON, Spec, register
from pixelart import Canvas


def tessellate(c: Canvas, seed, materials, glow_every=4, jitter=6, step=16):
    """Fills the canvas with planar facets (jittered triangle mesh), each lit like a cut gem face."""
    rnd = random.Random(seed)
    pts = {}
    for gy in range(0, 64 + step, step):
        for gx in range(0, 64 + step, step):
            jx = 0 if gx in (0, 64) else rnd.uniform(-jitter, jitter)
            jy = 0 if gy in (0, 64) else rnd.uniform(-jitter, jitter)
            pts[(gx, gy)] = (gx + jx, gy + jy)
    k = 0
    for gy in range(0, 64, step):
        for gx in range(0, 64, step):
            a, b = pts[(gx, gy)], pts[(gx + step, gy)]
            c_, d = pts[(gx + step, gy + step)], pts[(gx, gy + step)]
            for tri in ((a, b, c_), (a, c_, d)):
                m = c.poly(list(tri))
                nx, ny = rnd.uniform(-0.9, 0.9), rnd.uniform(-0.9, 0.9)
                nv = np.array([nx, ny, 1.0])
                nv /= np.linalg.norm(nv)
                normals = np.zeros((64, 64, 3))
                normals[...] = nv
                mat = materials[k % len(materials)]
                c.add(m, mat, normals, np.where(m, 0.6, 0), depth=0.75 + 0.25 * (k % 3), glow=0.8 if k % glow_every == 0 else 0)
                k += 1


def particle_shard(c, t, s):
    tessellate(c, 7, ['pink_gem', 'pink_gem', 'rose_gem', 'teal_gem'])
    for x, y in ((10, 9), (40, 22), (25, 45), (54, 52)):
        c.sparkle(x, y, 2)


def particle_spark(c, t, s):
    tessellate(c, 11, ['teal_gem', 'teal_gem', 'ice_gem', 'pink_gem'], glow_every=2)
    for x, y in ((8, 8), (24, 40), (40, 24), (56, 56), (8, 56), (56, 8)):
        c.sparkle(x, y, 3)


def particle_mote(c, t, s):
    tessellate(c, 23, ['ice_gem', 'teal_gem', 'ice_gem', 'pink_gem'], glow_every=1, jitter=4)
    for x, y in ((16, 16), (48, 16), (16, 48), (48, 48)):
        c.sparkle(x, y, 2)


register(Spec('particle_shard', 'particle', particle_shard, ICON))
register(Spec('particle_spark', 'particle', particle_spark, ICON))
register(Spec('particle_mote', 'particle', particle_mote, ICON))
