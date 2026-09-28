#!/usr/bin/env python3
"""
Generates the MagicNuke resource pack (3D models + procedural textures).

Output goes to ../src/main/pack, which Gradle zips into the plugin jar.
The generated files are committed, so you only need to run this if you
want to tweak the look of the nuke.

    pip install pillow numpy
    python3 tools/generate_pack.py            # write the pack
    python3 tools/generate_pack.py --preview  # also write preview PNGs to build/preview

Minecraft models are made of boxes, so round parts are built from eight thin
slabs rotated in 22.5 degree steps. Their outer faces form an exact 16-sided
prism, and the textures wrap continuously around it.
"""
import json
import math
import os
import shutil
import sys

import numpy as np
from PIL import Image

NS = "magicnuke"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "pack")
PACK_FORMAT = 75  # Minecraft Java 1.21.11

TAN = math.tan(math.radians(11.25))
SLAB_ANGLES = (0.0, 22.5, 45.0, -22.5)

rng = np.random.default_rng(1945)


# ---------------------------------------------------------------------------
# Model building
# ---------------------------------------------------------------------------

def r4(v):
    return round(float(v), 4)


def face_uv(face, frm, to, fn):
    """UV for an axis-aligned face, given fn(x, y, z) -> (u, v) on the pre-rotation box."""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    if face == "north":
        lt, rb = (x1, y1, z0), (x0, y0, z0)
    elif face == "south":
        lt, rb = (x0, y1, z1), (x1, y0, z1)
    elif face == "east":
        lt, rb = (x1, y1, z1), (x1, y0, z0)
    elif face == "west":
        lt, rb = (x0, y1, z0), (x0, y0, z1)
    elif face == "up":
        lt, rb = (x0, y1, z0), (x1, y1, z1)
    else:  # down
        lt, rb = (x0, y0, z1), (x1, y0, z0)
    u1, v1 = fn(*lt)
    u2, v2 = fn(*rb)
    # UVs outside 0..16 would sample neighbouring textures in the atlas
    return [r4(min(16.0, max(0.0, c))) for c in (u1, v1, u2, v2)]


def element(frm, to, faces, rotation=None, shade=True, light=None):
    e = {"from": [r4(c) for c in frm], "to": [r4(c) for c in to]}
    if rotation is not None and rotation[2] != 0:
        axis, origin, angle = rotation
        e["rotation"] = {"origin": [r4(c) for c in origin], "axis": axis, "angle": angle}
    if not shade:
        e["shade"] = False
    if light:
        e["light_emission"] = light
    e["faces"] = faces
    return e


def cylinder(y0, y1, r, side_tex, v_top, v_bot, cap_tex=None, top=True, bottom=True,
             u_lo=0.0, u_hi=16.0, cap_mode="solid", shade=True, light=None, cap_eps=0.0,
             cx=8.0, cz=8.0):
    """16-sided prism made of 8 rotated slabs. Side texture wraps once around u_lo..u_hi."""
    hw = r * TAN
    du = (u_hi - u_lo) / 16.0
    out = []
    j = 0
    for orient in ("z", "x"):
        for a in SLAB_ANGLES:
            eps = j * cap_eps
            j += 1
            ya, yb = y0 - eps, y1 + eps
            if orient == "z":
                frm, to = (cx - hw, ya, cz - r), (cx + hw, yb, cz + r)
                sides = {"north": -90.0, "south": 90.0}
            else:
                frm, to = (cx - r, ya, cz - hw), (cx + r, yb, cz + hw)
                sides = {"east": 0.0, "west": 180.0}
            faces = {}
            for name, phi0 in sides.items():
                k = int(round((a - phi0) / 22.5)) % 16
                faces[name] = {"uv": [r4(u_lo + k * du), r4(v_top), r4(u_lo + (k + 1) * du), r4(v_bot)],
                               "texture": side_tex}
            if cap_tex:
                if cap_mode == "solid":
                    cap_uv = [4, 4, 12, 12]
                    if top:
                        faces["up"] = {"uv": cap_uv, "texture": cap_tex}
                    if bottom:
                        faces["down"] = {"uv": cap_uv, "texture": cap_tex}
                else:  # radial: texture centred on the axis, symmetric so rotated slabs agree
                    s = 8.0 / r
                    fn = lambda x, y, z: (8 + (x - cx) * s, 8 + (z - cz) * s)
                    if top:
                        faces["up"] = {"uv": face_uv("up", frm, to, fn), "texture": cap_tex}
                    if bottom:
                        faces["down"] = {"uv": face_uv("down", frm, to, fn), "texture": cap_tex}
            rot = ("y", (cx, (y0 + y1) / 2, cz), a)
            out.append(element(frm, to, faces, rot, shade, light))
    return out


def box(frm, to, tex, fn, rotation=None, faces=("north", "south", "east", "west", "up", "down"),
        shade=True, light=None, face_tex=None):
    fs = {}
    for f in faces:
        t = (face_tex or {}).get(f, tex)
        fs[f] = {"uv": face_uv(f, frm, to, fn), "texture": t}
    return element(frm, to, fs, rotation, shade, light)


# ---------------------------------------------------------------------------
# The nuke
# ---------------------------------------------------------------------------

BODY_TOP, BODY_BOT = 17.5, -7.5
NOSE_BOT, NOSE_TOP = 18.5, 28.0
NOZ_TOP, NOZ_BOT = -9.5, -12.0


def body_v(y):
    return (BODY_TOP - y) / (BODY_TOP - BODY_BOT) * 16


def nose_v(y):
    return (NOSE_TOP - y) / (NOSE_TOP - NOSE_BOT) * 16


def noz_v(y):
    return (NOZ_TOP - y) / (NOZ_TOP - NOZ_BOT) * 16


NOSE_STEPS = []


def build_nuke():
    els = []
    # nozzle glow disc (bottom)
    els += cylinder(-12.06, -12.0, 2.55, "#glow", 0, 1, cap_tex="#glow", top=False,
                    cap_mode="radial", shade=False, light=15)
    # engine bell
    for (a, b, r) in ((-12.0, -11.2, 3.15), (-11.2, -10.4, 2.85), (-10.4, -9.5, 2.5)):
        els += cylinder(a, b, r, "#nozzle", noz_v(b), noz_v(a), cap_tex="#dark")
    # boat tail
    els += cylinder(-9.5, -7.5, 3.7, "#metal", 0, 8, cap_tex="#dark")
    # main body
    els += cylinder(BODY_BOT, BODY_TOP, 4.0, "#body", 0, 16, cap_tex="#dark")
    # raised rings
    for (a, b, r) in ((-7.7, -7.2, 4.25), (4.55, 5.0, 4.18), (9.5, 9.95, 4.18)):
        els += cylinder(a, b, r, "#metal", 8, 10, cap_tex="#dark")
    # collar
    els += cylinder(17.3, 18.5, 4.3, "#metal", 10, 16, cap_tex="#dark")
    # nose cone (ogive)
    n = 22
    h = (27.2 - NOSE_BOT) / n
    for i in range(n):
        a = NOSE_BOT + i * h
        b = a + h
        s = (i + 0.5) / n
        r = 4.0 * (1 - s ** 1.6) ** 0.62
        r = max(r, 0.45)
        NOSE_STEPS.append((a, b, r))
        els += cylinder(a, b, r, "#nose", nose_v(b), nose_v(a), cap_tex="#red")
    # tip
    els += cylinder(27.2, 28.0, 0.32, "#nose", nose_v(28.0), nose_v(27.2), cap_tex="#dark")

    # fins: N/E/S/W, each a root plate + a 45 degree swept leading edge + a red tip cap
    fin_uv = lambda rho, y: ((rho - 3.5) / 4.0 * 16, (-1.5 - y) / 8.5 * 16)
    for sign, axis in ((1, "x"), (-1, "x"), (1, "z"), (-1, "z")):
        th = 0.35

        def mk(r0, r1, ya, yb, t, rot_angle=None, rot_center=None, tex="#fin", tipcap=False):
            if axis == "x":
                xa, xb = (8 + r0, 8 + r1) if sign > 0 else (8 - r1, 8 - r0)
                frm, to = (xa, ya, 8 - t), (xb, yb, 8 + t)
                fn = lambda x, y, z: fin_uv(abs(x - 8), y)
                rot = None
                if rot_angle is not None:
                    rot = ("z", (8 + sign * rot_center[0], rot_center[1], 8), rot_angle * sign)
            else:
                za, zb = (8 + r0, 8 + r1) if sign > 0 else (8 - r1, 8 - r0)
                frm, to = (8 - t, ya, za), (8 + t, yb, zb)
                fn = lambda x, y, z: fin_uv(abs(z - 8), y)
                rot = None
                if rot_angle is not None:
                    rot = ("x", (8, rot_center[1], 8 + sign * rot_center[0]), -rot_angle * sign)
            return box(frm, to, tex, fn, rot)

        els.append(mk(3.5, 7.5, -10.0, -5.0, th))
        # swept plate: long axis along the leading edge, rotated 45 degrees
        L = math.hypot(3.5, 3.5)
        cxr, cyr = 4.97, -4.03
        els.append(mk(cxr - L / 2, cxr + L / 2, cyr - 1.1, cyr + 1.1, th * 0.95,
                      rot_angle=-45, rot_center=(cxr, cyr)))
        # red tip cap on the outer trailing corner
        els.append(mk(6.3, 7.62, -10.12, -8.6, th + 0.08, tex="#fin"))
        # fin root fairing
        els.append(mk(3.3, 4.6, -10.4, -4.2, th + 0.25, tex="#metal"))

    # canards (small diagonal fins near the top of the body)
    for ang in (45, -45):
        els.append(box((8 - 5.6, 11.8, 8 - 0.2), (8 + 5.6, 14.6, 8 + 0.2), "#fin",
                       lambda x, y, z: ((abs(x - 8) - 3.5) / 4.0 * 16 * 0.9, (15.6 - y) / 8.5 * 16 * 0.9 + 1),
                       rotation=("y", (8, 13, 8), ang)))

    # conduit raceway along one side, plus a connector box
    cfn = lambda x, y, z: ((z - 7) * 4 % 16, (17 - y) / 25 * 16)
    els.append(box((11.85, -5.4, 7.35), (12.5, 16.2, 8.65), "#metal", cfn))
    els.append(box((11.8, 12.4, 7.0), (12.9, 14.2, 9.0), "#metal",
                   lambda x, y, z: ((z - 7) * 3, (14.2 - y) * 3)))
    # access hatch on the opposite side
    els.append(box((3.75, -2.2, 6.6), (4.1, 1.8, 9.4), "#metal",
                   lambda x, y, z: ((z - 6.6) * 4, (1.8 - y) * 4), faces=("west", "north", "south", "up", "down")))
    return els


NUKE_DISPLAY = {
    "gui": {"rotation": [25, -45, -38], "translation": [0.6, -0.4, 0], "scale": [0.44, 0.44, 0.44]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2.5, 0], "scale": [0.2, 0.2, 0.2]},
    "fixed": {"rotation": [0, 0, -40], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 14, 0], "scale": [0.45, 0.45, 0.45]},
    "thirdperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 5.5, 0.5], "scale": [0.24, 0.24, 0.24]},
    "thirdperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 5.5, 0.5], "scale": [0.24, 0.24, 0.24]},
    "firstperson_righthand": {"rotation": [0, -20, 12], "translation": [1.5, 4.5, 0], "scale": [0.28, 0.28, 0.28]},
    "firstperson_lefthand": {"rotation": [0, -20, 12], "translation": [1.5, 4.5, 0], "scale": [0.28, 0.28, 0.28]},
}


# ---------------------------------------------------------------------------
# Explosion effect models (fireball sphere + puffy smoke cluster)
# ---------------------------------------------------------------------------

def sphere(cx, cy, cz, R, tex, layers, shade, light):
    els = []
    h = 2 * R / layers
    for i in range(layers):
        a = cy - R + i * h
        b = a + h
        mid = (a + b) / 2 - cy
        r = math.sqrt(max(R * R - mid * mid, 0.0)) * 1.04
        r = max(r, R * 0.25)
        v_top = 16 - (b - (cy - R)) / (2 * R) * 16
        v_bot = 16 - (a - (cy - R)) / (2 * R) * 16
        els += cylinder(a, b, r, tex, v_top, v_bot, cap_tex=tex, cap_mode="radial",
                        shade=shade, light=light, cap_eps=0.035, cx=cx, cz=cz)
    return els


def build_fireball():
    return sphere(8, 8, 8, 7.8, "#tex", 11, False, 15)


def build_puff():
    els = sphere(8, 8, 8, 6.4, "#tex", 7, True, None)
    els += sphere(12.2, 9.4, 8.5, 4.3, "#tex", 5, True, None)
    els += sphere(4.6, 9.0, 6.4, 4.0, "#tex", 5, True, None)
    els += sphere(7.4, 11.8, 11.4, 3.8, "#tex", 5, True, None)
    els += sphere(8.8, 5.2, 4.2, 3.6, "#tex", 5, True, None)
    return els


# ---------------------------------------------------------------------------
# Textures
# ---------------------------------------------------------------------------

def value_noise(w, h, cells_x, cells_y, wrap_x=True, wrap_y=True, seed=None):
    g = np.random.default_rng(seed).random((cells_y + 1, cells_x + 1))
    if wrap_x:
        g[:, -1] = g[:, 0]
    if wrap_y:
        g[-1, :] = g[0, :]
    xs = np.linspace(0, cells_x, w, endpoint=False)
    ys = np.linspace(0, cells_y, h, endpoint=False)
    xi = np.floor(xs).astype(int)
    yi = np.floor(ys).astype(int)
    xf = xs - xi
    yf = ys - yi
    xf = xf * xf * (3 - 2 * xf)
    yf = yf * yf * (3 - 2 * yf)
    a = g[yi][:, xi]
    b = g[yi][:, xi + 1]
    c = g[yi + 1][:, xi]
    d = g[yi + 1][:, xi + 1]
    top = a + (b - a) * xf[None, :]
    bot = c + (d - c) * xf[None, :]
    return top + (bot - top) * yf[:, None]


def fbm(w, h, base, octaves=4, seed=0, wrap=True):
    total = np.zeros((h, w))
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        c = base * (2 ** o)
        total += amp * value_noise(w, h, c, c, wrap, wrap, seed + o * 7)
        norm += amp
        amp *= 0.5
    return total / norm


def rgba(arr_rgb, alpha=255):
    h, w, _ = arr_rgb.shape
    out = np.zeros((h, w, 4), dtype=np.uint8)
    out[..., :3] = np.clip(arr_rgb, 0, 255).astype(np.uint8)
    out[..., 3] = alpha
    return out


def lerp_col(c1, c2, t):
    c1 = np.array(c1, dtype=float)
    c2 = np.array(c2, dtype=float)
    t = np.asarray(t, dtype=float)[..., None]
    return c1 + (c2 - c1) * t


FONT = {
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "C": [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
    "D": ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
    "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "G": [".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###."],
    "H": ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "I": [".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."],
    "K": ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
    "L": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
    "M": ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
    "N": ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
    "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "U": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "V": ["#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."],
    "-": [".....", ".....", ".....", ".###.", ".....", ".....", "....."],
    " ": [".....", ".....", ".....", ".....", ".....", ".....", "....."],
}


def draw_text(img, text, cx, top, color):
    w = len(text) * 6 - 1
    x0 = int(round(cx - w / 2))
    H, W = img.shape[:2]
    for i, ch in enumerate(text):
        glyph = FONT[ch]
        for gy, row in enumerate(glyph):
            for gx, c in enumerate(row):
                if c == "#":
                    x = (x0 + i * 6 + gx) % W
                    img[top + gy, x, :3] = color


def draw_trefoil(img, cx, cy, radius, fg, bg):
    H, W = img.shape[:2]
    ys, xs = np.mgrid[0:H, 0:W]
    dx = (xs - cx + W / 2) % W - W / 2
    dy = ys - cy
    d = np.hypot(dx, dy) / radius
    ang = (np.degrees(np.arctan2(-dy, dx)) + 360) % 360
    disc = d <= 1.0
    img[disc, :3] = bg
    blade = np.zeros_like(disc)
    for c in (90, 210, 330):
        diff = np.abs((ang - c + 180) % 360 - 180)
        blade |= (diff <= 30) & (d >= 0.26) & (d <= 0.86)
    core = d <= 0.17
    img[blade | core, :3] = fg


def tex_body():
    W = H = 256
    ppu = H / (BODY_TOP - BODY_BOT)
    yy = lambda y: int(round((BODY_TOP - y) * ppu))
    n = fbm(W, H, 4, 4, seed=11)
    fine = rng.random((H, W))
    base = np.array([226, 229, 231], float)
    img = rgba(base[None, None, :] * (0.93 + 0.07 * n[..., None]) - 6 * fine[..., None])
    # subtle vertical grime streaks
    streak = fbm(W, H, 16, 2, seed=5)
    img[..., :3] = np.clip(img[..., :3] - (streak[..., None] ** 3 * 18), 0, 255)

    def band(y_hi, y_lo, col):
        img[yy(y_hi):yy(y_lo), :, :3] = col

    # hazard stripes at the bottom
    y0, y1 = yy(-5.3), yy(BODY_BOT)
    ys, xs = np.mgrid[y0:y1, 0:W]
    stripe = ((xs + ys) // 8) % 2 == 0
    region = img[y0:y1]
    region[stripe, :3] = (28, 28, 30)
    region[~stripe, :3] = (242, 196, 22)
    band(-5.3, -5.6, (40, 40, 44))
    # panel seams
    for y in (-2.0, 1.8, 12.4, 15.8):
        band(y + 0.08, y - 0.08, (150, 155, 160))
    for u in range(0, W, 64):
        img[yy(4.4):yy(-5.2), u:u + 1, :3] = (150, 155, 160)
        img[yy(17.3):yy(10.2), (u + 32) % W:(u + 32) % W + 1, :3] = (150, 155, 160)
    # rivets
    for y in (-4.7, 4.1, 10.6, 16.9):
        for u in range(2, W, 8):
            r = yy(y)
            img[r:r + 2, u:u + 2, :3] = (120, 124, 130)
            img[r, u, :3] = (250, 250, 250)
    # yellow radiation band
    band(9.5, 5.0, (246, 204, 24))
    band(9.5, 9.25, (30, 30, 30))
    band(5.25, 5.0, (30, 30, 30))
    band_n = fbm(W, yy(5.0) - yy(9.5), 6, 3, seed=3)
    img[yy(9.5):yy(5.0), :, :3] = np.clip(img[yy(9.5):yy(5.0), :, :3] * (0.9 + 0.1 * band_n[..., None]), 0, 255)
    cy = (yy(9.5) + yy(5.0)) / 2
    for cx in (64, 192):
        draw_trefoil(img, cx, cy, 19, (22, 22, 22), (246, 204, 24))
    # small black trefoils between them
    for cx in (0, 128):
        draw_trefoil(img, cx, cy, 10, (22, 22, 22), (246, 204, 24))
    # stencils
    draw_text(img, "DANGER", 64, yy(3.4), (32, 32, 34))
    draw_text(img, "RADIOACTIVE", 192, yy(3.4), (32, 32, 34))
    draw_text(img, "MK-VII", 64, yy(12.2), (32, 32, 34))
    draw_text(img, "THERMONUCLEAR", 192, yy(12.2), (32, 32, 34))
    # red warning stripe near the top
    band(16.5, 15.9, (178, 22, 26))
    # top shadow line
    band(BODY_TOP, 17.25, (70, 72, 76))
    return img


def tex_nose():
    W = H = 128
    ppu = H / (NOSE_TOP - NOSE_BOT)
    yy = lambda y: int(round((NOSE_TOP - y) * ppu))
    n = fbm(W, H, 4, 4, seed=21)
    t = np.linspace(0, 1, H)[:, None]  # 0 top -> 1 bottom
    col = lerp_col((150, 12, 14), (206, 28, 26), t * np.ones((H, W)))
    col = col * (0.9 + 0.1 * n[..., None])
    img = rgba(col)
    img[yy(19.25):yy(NOSE_BOT), :, :3] = (235, 235, 235)
    img[yy(19.45):yy(19.25), :, :3] = (30, 30, 30)
    img[yy(NOSE_TOP):yy(26.9), :, :3] = (160, 164, 170)
    img[yy(26.9):yy(26.7), :, :3] = (40, 40, 40)
    # heat scorch speckles
    speck = rng.random((H, W)) > 0.985
    img[speck, :3] = (110, 10, 10)
    return img


def tex_metal():
    W = H = 64
    n = fbm(W, H, 4, 4, seed=31)
    base = np.array([74, 78, 86], float)
    img = rgba(base * (0.85 + 0.25 * n[..., None]))
    # bolt row in the middle of the collar rows (v 10..16)
    for u in range(1, W, 4):
        img[46, u, :3] = (150, 154, 162)
        img[47, u, :3] = (40, 42, 46)
    img[40, :, :3] = (50, 52, 58)
    img[63, :, :3] = (46, 48, 52)
    return img


def tex_nozzle():
    W = H = 64
    n = fbm(W, H, 4, 3, seed=41)
    t = np.linspace(0, 1, H)[:, None] * np.ones((H, W))
    # heat discolouration: steel -> bronze -> purple -> blue toward the exit
    stops = [(0.0, (92, 94, 100)), (0.4, (128, 98, 62)), (0.7, (92, 60, 110)), (1.0, (46, 62, 120))]
    col = np.zeros((H, W, 3))
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        m = (t >= a) & (t <= b)
        col[m] = lerp_col(ca, cb, ((t - a) / (b - a))[m])
    col *= (0.8 + 0.3 * n[..., None])
    img = rgba(col)
    for u in range(0, W, 4):
        img[:, u, :3] = np.clip(img[:, u, :3].astype(int) - 18, 0, 255)
    return img


def tex_fin():
    W = H = 64
    n = fbm(W, H, 3, 4, seed=51)
    img = rgba(np.array([96, 101, 110], float) * (0.85 + 0.22 * n[..., None]))
    img[:, 0:2, :3] = (60, 63, 70)
    img[0:1, :, :3] = (60, 63, 70)
    # panel line
    img[30:31, 6:60, :3] = (70, 74, 80)
    img[8:58, 34:35, :3] = (70, 74, 80)
    # red tip at the outer trailing edge
    img[53:64, 48:64, :3] = (190, 24, 24)
    img[53:54, 48:64, :3] = (240, 240, 240)
    return img


def tex_glow():
    S = 32
    ys, xs = np.mgrid[0:S, 0:S]
    d = np.hypot(xs - S / 2 + 0.5, ys - S / 2 + 0.5) / (S / 2)
    col = np.zeros((S, S, 3))
    stops = [(0.0, (255, 255, 230)), (0.3, (255, 220, 90)), (0.65, (255, 120, 20)), (0.9, (170, 30, 10)), (1.5, (60, 20, 20))]
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        m = (d >= a) & (d <= b)
        col[m] = lerp_col(ca, cb, ((d - a) / (b - a))[m])
    return rgba(col)


def tex_solid(c):
    return rgba(np.ones((16, 16, 3)) * np.array(c, float))


def tex_fx(stops, seed, contrast=1.0, octaves=5):
    S = 64
    n = fbm(S, S, 3, octaves, seed=seed)
    n = np.clip((n - 0.5) * contrast + 0.5, 0, 1)
    col = np.zeros((S, S, 3))
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        m = (n >= a) & (n <= b)
        col[m] = lerp_col(ca, cb, ((n - a) / (b - a))[m])
    return rgba(col)


FX = {
    "fx_fireball_white": ([(0.0, (255, 214, 120)), (0.5, (255, 246, 200)), (1.0, (255, 255, 255))], 61, 2.2),
    "fx_fireball": ([(0.0, (160, 30, 6)), (0.35, (236, 90, 14)), (0.65, (255, 170, 40)), (1.0, (255, 236, 150))], 62, 2.4),
    "fx_smoke_hot": ([(0.0, (40, 22, 18)), (0.4, (96, 40, 20)), (0.7, (190, 70, 18)), (1.0, (250, 140, 40))], 63, 2.6),
    "fx_smoke": ([(0.0, (48, 46, 46)), (0.5, (92, 88, 86)), (1.0, (150, 146, 142))], 64, 2.0),
    "fx_smoke_light": ([(0.0, (120, 118, 116)), (0.5, (170, 168, 164)), (1.0, (222, 220, 216))], 65, 2.0),
    "fx_smoke_dust": ([(0.0, (70, 56, 42)), (0.5, (120, 100, 76)), (1.0, (168, 146, 116))], 66, 2.0),
}


def tex_flash():
    return rgba(np.ones((64, 256, 3)) * 255)


# ---------------------------------------------------------------------------
# Writing the pack
# ---------------------------------------------------------------------------

def save_png(img, *parts):
    path = os.path.join(OUT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(img, "RGBA").save(path, optimize=True)


def save_json(obj, *parts):
    path = os.path.join(OUT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=1 if parts[-1] == "pack.mcmeta" else None, separators=None if parts[-1] == "pack.mcmeta" else (",", ":"))
        f.write("\n")


def t(name):
    return f"{NS}:item/{name}"


def main():
    preview = "--preview" in sys.argv
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)

    save_json({"pack": {
        "description": "§c☢ MagicNuke §7- 3D nukes & explosion FX",
        "pack_format": PACK_FORMAT,
        "min_format": PACK_FORMAT,
        "max_format": PACK_FORMAT,
    }}, "pack.mcmeta")

    textures = {
        "nuke_body": tex_body(), "nuke_nose": tex_nose(), "nuke_metal": tex_metal(),
        "nuke_nozzle": tex_nozzle(), "nuke_fin": tex_fin(), "nuke_glow": tex_glow(),
        "nuke_dark": tex_solid((52, 54, 60)), "nuke_red": tex_solid((170, 20, 20)),
    }
    for name, (stops, seed, contrast) in FX.items():
        textures[name] = tex_fx(stops, seed, contrast)
    for name, img in textures.items():
        save_png(img, "assets", NS, "textures", "item", name + ".png")

    nuke_tex = {
        "particle": t("nuke_body"), "body": t("nuke_body"), "nose": t("nuke_nose"),
        "metal": t("nuke_metal"), "nozzle": t("nuke_nozzle"), "fin": t("nuke_fin"),
        "glow": t("nuke_glow"), "dark": t("nuke_dark"), "red": t("nuke_red"),
    }
    nuke_els = build_nuke()
    save_json({"texture_size": [16, 16], "textures": nuke_tex, "elements": nuke_els, "display": NUKE_DISPLAY},
              "assets", NS, "models", "item", "nuke.json")
    save_json({"model": {"type": "minecraft:model", "model": f"{NS}:item/nuke"}},
              "assets", NS, "items", "nuke.json")

    save_json({"textures": {"particle": "#tex"}, "elements": build_fireball()},
              "assets", NS, "models", "item", "fx_sphere.json")
    save_json({"textures": {"particle": "#tex"}, "elements": build_puff()},
              "assets", NS, "models", "item", "fx_puff.json")
    for name in FX:
        parent = "fx_sphere" if name.startswith("fx_fireball") else "fx_puff"
        save_json({"parent": f"{NS}:item/{parent}", "textures": {"tex": t(name)}},
                  "assets", NS, "models", "item", name + ".json")
        save_json({"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}},
                  "assets", NS, "items", name + ".json")

    # full-screen flash glyph used in a title
    save_png(tex_flash(), "assets", NS, "textures", "font", "flash.png")
    save_json({"providers": [{"type": "bitmap", "file": f"{NS}:font/flash.png",
                              "height": 128, "ascent": 61, "chars": [""]}]},
              "assets", NS, "font", "flash.json")

    import render_preview
    icon = render_preview.render_model(nuke_els, textures_for(nuke_tex, textures), NUKE_DISPLAY["gui"], 256)
    Image.fromarray(icon).resize((128, 128), Image.LANCZOS).save(os.path.join(OUT, "pack.png"), optimize=True)

    print(f"nuke: {len(nuke_els)} elements, fireball: {len(build_fireball())}, puff: {len(build_puff())}")
    if preview:
        pdir = os.path.join(ROOT, "build", "preview")
        os.makedirs(pdir, exist_ok=True)
        tx = textures_for(nuke_tex, textures)
        views = {
            "nuke_gui": NUKE_DISPLAY["gui"],
            "nuke_side": {"rotation": [10, -30, 0], "scale": [0.36] * 3},
            "nuke_low": {"rotation": [-25, 20, 0], "scale": [0.36] * 3},
            "nuke_top": {"rotation": [70, 30, 0], "scale": [0.36] * 3},
            "nuke_front": {"rotation": [0, 0, 0], "scale": [0.36] * 3},
            "nuke_back": {"rotation": [0, 180, 0], "scale": [0.36] * 3},
        }
        for vn, disp in views.items():
            Image.fromarray(render_preview.render_model(nuke_els, tx, disp, 512)).save(os.path.join(pdir, vn + ".png"))
        for name in ("fx_fireball", "fx_smoke"):
            els = build_fireball() if name == "fx_fireball" else build_puff()
            Image.fromarray(render_preview.render_model(els, {"#tex": textures[name]}, {"rotation": [20, 30, 0], "scale": [0.9] * 3}, 256)).save(os.path.join(pdir, name + ".png"))
        print("previews in", pdir)


def textures_for(model_tex, textures):
    out = {}
    for k, v in model_tex.items():
        out["#" + k] = textures[v.split("/")[-1]]
    return out


if __name__ == "__main__":
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    main()
