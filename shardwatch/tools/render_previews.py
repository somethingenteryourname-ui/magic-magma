#!/usr/bin/env python3
"""
Renders preview images of the generated models and textures into ../docs/previews.

  * a software renderer draws every model JSON from its real `display.gui` angle plus a 3/4 "held" angle,
  * the 64x64 texture, the animated glow strip and the LabPBR normal/specular maps are shown next to it,
  * one contact sheet per kind (tools, badges, icons, particles).

    python3 tools/render_previews.py
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).parent))
import assets as A  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
NS = ROOT / 'resourcepack' / 'assets' / 'shardwatch'
OUT = ROOT / 'docs' / 'previews'
BG = (34, 22, 40)
BG2 = (44, 30, 52)
INK = (245, 230, 240)
MUTED = (160, 150, 175)


def rot(rx, ry, rz):
    rx, ry, rz = map(math.radians, (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rx @ Ry @ Rz


def face_quad(side, f, t):
    """Corners (top-left, top-right, bottom-right, bottom-left) of a face as seen from outside."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[side]


NORMALS = {'south': (0, 0, 1), 'north': (0, 0, -1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0),
           'down': (0, -1, 0)}


def load_tex(ref):
    path = NS / 'textures' / (ref.split(':', 1)[1] + '.png')
    img = np.array(Image.open(path).convert('RGBA'))
    return img[:img.shape[1]]  # first animation frame


def render_model(name, transform, size=256, frame=0):
    img = _render_model(name, transform, size, frame)
    if name.startswith('flare_compass') and 'needle' not in name:
        # The item definition is a composite: body + a needle aimed by the compass property. Show it pointing NE.
        img.alpha_composite(_render_model('flare_compass_needle_02', transform, size, frame))
    return img


def _render_model(name, transform, size=256, frame=0):
    model = json.loads((NS / 'models' / 'item' / f'{name}.json').read_text())
    texs = {}
    for k, ref in model['textures'].items():
        path = NS / 'textures' / (ref.split(':', 1)[1] + '.png')
        img = np.array(Image.open(path).convert('RGBA'))
        frames = img.shape[0] // img.shape[1]
        texs[k] = img[(frame % frames) * img.shape[1]:(frame % frames + 1) * img.shape[1]]
    R = rot(*transform.get('rotation', [0, 0, 0]))
    S = np.array(transform.get('scale', [1, 1, 1]))
    T = np.array(transform.get('translation', [0, 0, 0]))
    img = np.zeros((size, size, 4), float)
    zbuf = np.full((size, size), -1e9)
    scale = size / 16.0
    yy, xx = np.mgrid[0:size, 0:size]
    for e in model['elements']:
        for side, face in e['faces'].items():
            tex = texs[face['texture'].lstrip('#')]
            th, tw = tex.shape[:2]
            quad = np.array(face_quad(side, e['from'], e['to']), float) - 8
            pts = (R @ (quad * S).T).T + T
            n = R @ np.array(NORMALS[side], float)
            if n[2] < -1e-6 and e.get('light_emission', 0) == 0:
                continue  # back-face cull (glow planes are single-sided too)
            if n[2] < -1e-6:
                continue
            u0, v0, u1, v1 = face['uv']
            uvs = np.array([(u0, v0), (u1, v0), (u1, v1), (u0, v1)]) / 16.0
            sx = pts[:, 0] * scale + size / 2
            sy = -pts[:, 1] * scale + size / 2
            sz = pts[:, 2]
            light = 1.0 if e.get('shade', True) is False else 0.62 + 0.38 * max(0, n[2]) + 0.12 * max(0, n[1])
            for tri in ((0, 1, 2), (0, 2, 3)):
                ax, ay = sx[list(tri)], sy[list(tri)]
                xmin, xmax = int(max(0, math.floor(ax.min()))), int(min(size - 1, math.ceil(ax.max())))
                ymin, ymax = int(max(0, math.floor(ay.min()))), int(min(size - 1, math.ceil(ay.max())))
                if xmax < xmin or ymax < ymin:
                    continue
                px = xx[ymin:ymax + 1, xmin:xmax + 1] + 0.5
                py = yy[ymin:ymax + 1, xmin:xmax + 1] + 0.5
                (x1, x2, x3), (y1, y2, y3) = ax, ay
                den = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3)
                if abs(den) < 1e-9:
                    continue
                w1 = ((y2 - y3) * (px - x3) + (x3 - x2) * (py - y3)) / den
                w2 = ((y3 - y1) * (px - x3) + (x1 - x3) * (py - y3)) / den
                w3 = 1 - w1 - w2
                inside = (w1 >= -1e-6) & (w2 >= -1e-6) & (w3 >= -1e-6)
                if not inside.any():
                    continue
                tu = w1 * uvs[tri[0], 0] + w2 * uvs[tri[1], 0] + w3 * uvs[tri[2], 0]
                tv = w1 * uvs[tri[0], 1] + w2 * uvs[tri[1], 1] + w3 * uvs[tri[2], 1]
                z = w1 * sz[tri[0]] + w2 * sz[tri[1]] + w3 * sz[tri[2]] + (0.01 if e.get('light_emission') else 0)
                ix = np.clip((tu * tw).astype(int), 0, tw - 1)
                iy = np.clip((tv * th).astype(int), 0, th - 1)
                col = tex[iy, ix]
                ok = inside & (col[..., 3] > 0)
                region_z = zbuf[ymin:ymax + 1, xmin:xmax + 1]
                ok &= z >= region_z
                region_z[ok] = z[ok]
                region = img[ymin:ymax + 1, xmin:xmax + 1]
                region[ok, :3] = np.clip(col[ok, :3] * light, 0, 255)
                region[ok, 3] = 255
    return Image.fromarray(img.astype(np.uint8), 'RGBA')


def font(size):
    for p in ('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'):
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def checker(w, h, s=8):
    img = Image.new('RGBA', (w, h), BG)
    d = ImageDraw.Draw(img)
    for y in range(0, h, s):
        for x in range(0, w, s):
            if (x // s + y // s) % 2:
                d.rectangle([x, y, x + s - 1, y + s - 1], fill=BG2)
    return img


def up(path_or_img, scale):
    im = path_or_img if isinstance(path_or_img, Image.Image) else Image.open(path_or_img).convert('RGBA')
    return im.resize((im.width * scale, im.height * scale), Image.NEAREST)


def asset_card(a, variant=None):
    """One preview card: GUI render, held render, texture, LabPBR maps and every glow frame."""
    name = variant or a.id
    model = json.loads((NS / 'models' / 'item' / f'{name}.json').read_text())
    card = checker(1060, 330)
    d = ImageDraw.Draw(card)
    f = font(14)
    card.alpha_composite(render_model(name, model['display'].get('gui', {}), 256), (12, 36))
    held = render_model(name, {'rotation': [20, -55, 20], 'scale': [0.9, 0.9, 0.9], 'translation': [0, 0, 0]}, 200)
    card.alpha_composite(held, (278, 70))
    tdir = NS / 'textures' / 'item'
    x0 = 520
    for i, (suffix, label) in enumerate((('', 'albedo 64×64'), ('_n', '_n LabPBR normal'), ('_s', '_s LabPBR specular'))):
        card.alpha_composite(up(tdir / f'{name}{suffix}.png', 2), (x0 + i * 176, 36))
        d.text((x0 + i * 176, 168), label, fill=MUTED, font=f)
    glow = tdir / f'{name}_glow.png'
    if glow.exists():
        g = Image.open(glow).convert('RGBA')
        frames = g.height // 64
        for i in range(min(frames, 8)):
            card.alpha_composite(g.crop((0, i * 64, 64, (i + 1) * 64)), (x0 + i * 66, 196))
        d.text((x0, 264), f'emissive glow layer · {frames} frames (.mcmeta animated)', fill=MUTED, font=f)
    emis = sum(1 for e in model['elements'] if e.get('light_emission'))
    d.text((12, 8), name, fill=INK, font=font(17))
    d.text((220, 10), a.title, fill=MUTED, font=f)
    d.text((40, 300), 'GUI view (display.gui)', fill=MUTED, font=f)
    d.text((300, 300), '3/4 held view', fill=MUTED, font=f)
    d.text((x0, 300), f'{len(model["elements"]) - emis} relief elements · {emis} emissive planes (light_emission 15)',
           fill=MUTED, font=f)
    return card


def sheet(assets, title, cols=6, cell=200):
    items = []
    for a in assets:
        names = [a.id] + [f'{a.id}_t{t}' for t in range(2, a.tiers + 1)] + [f'{a.id}_{s}' for s in a.states]
        for n in names:
            if (NS / 'models' / 'item' / f'{n}.json').exists():
                items.append(n)
    if not items:
        return None
    rows = math.ceil(len(items) / cols)
    img = checker(cols * cell, rows * (cell + 24) + 50)
    d = ImageDraw.Draw(img)
    d.text((12, 12), title, fill=INK, font=font(22))
    for i, n in enumerate(items):
        model = json.loads((NS / 'models' / 'item' / f'{n}.json').read_text())
        r = render_model(n, model['display'].get('gui', {}), cell - 16)
        x, y = (i % cols) * cell, 50 + (i // cols) * (cell + 24)
        img.alpha_composite(r, (x + 8, y))
        d.text((x + 10, y + cell - 12), n, fill=MUTED, font=font(13))
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob('*.png'):
        old.unlink()
    groups = [('tools', A.TOOLS, 'Staff tools (Stage 2) — GUI angle, every tier/state'),
              ('badges', A.BADGES, 'Facet sigils (Stage 2)'),
              ('icons', A.ICONS, 'GUI icons (Stage 3)'),
              ('particles', A.PARTICLES, 'Particle sprites (Stage 5)')]
    made = []
    for key, group, title in groups:
        s = sheet(group, title, cols=6 if key != 'icons' else 8, cell=200 if key != 'icons' else 150)
        if s:
            s.save(OUT / f'sheet-{key}.png')
            made.append(f'sheet-{key}.png')
        for a in group:
            if (NS / 'models' / 'item' / f'{a.id}.json').exists() and key in ('tools', 'badges'):
                asset_card(a).save(OUT / f'{a.id}.png')
                made.append(f'{a.id}.png')
    print('previews:', ', '.join(made))


if __name__ == '__main__':
    main()
