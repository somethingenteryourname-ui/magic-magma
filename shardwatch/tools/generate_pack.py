#!/usr/bin/env python3
"""
Generates the Shardwatch resource pack into ../resourcepack and Blockbench projects into ../blockbench.

    pip install pillow numpy soundfile
    python3 tools/generate_pack.py

For every registered asset it writes:
  textures/item/<id>.png            64x64 albedo (pixel art from tools/art/*)
  textures/item/<id>_n.png / _s.png LabPBR normal + specular
  textures/item/<id>_glow.png       animated emissive strip (+ .mcmeta, _n, _s)
  models/item/<id>.json             3D extruded relief model (Blockbench "Java Block/Item" format)
  items/<id>.json                   item model definition (tiers, states, cooldown)
  ../blockbench/<id>.bbmodel        editable Blockbench project with embedded textures
"""
from __future__ import annotations

import base64
import importlib
import io
import json
import math
import shutil
import sys
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Optional

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
from pixelart import MATERIALS, RAMP_RGB, Canvas, to_image  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / 'resourcepack'
NS = PACK / 'assets' / 'shardwatch'
BB = ROOT / 'blockbench'
GLOW_FRAMES = 8
FRAMETIME = 3

# ------------------------------------------------------------------ display presets
HANDHELD = {
    'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 4, 0.5], 'scale': [0.85, 0.85, 0.85]},
    'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 4, 0.5], 'scale': [0.85, 0.85, 0.85]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'gui': {'rotation': [12, -24, 0], 'translation': [0, 0, 0], 'scale': [0.92, 0.92, 0.92]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1, 1, 1]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
}
HELD_ITEM = {  # badges, shards: held like a normal item, not a tool
    **HANDHELD,
    'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.55, 0.55, 0.55]},
    'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.55, 0.55, 0.55]},
}
ICON = {
    **HELD_ITEM,
    'gui': {'rotation': [8, -16, 0], 'translation': [0, 0, 0], 'scale': [0.96, 0.96, 0.96]},
}


@dataclass
class Spec:
    id: str
    kind: str
    draw: Callable[[Canvas, int, Optional[str]], None]
    display: dict
    tiers: int = 1
    states: list[str] = field(default_factory=list)
    # Custom glow animation: fn(canvas_rgba, glow_mask, frame_t, tier, state) -> RGBA frame. None = shimmer sweep.
    glow: Optional[Callable] = None
    frames: int = GLOW_FRAMES
    frametime: int = FRAMETIME
    # Item definition extras: 'cooldown' -> range_dispatch on cooldown to the first state, 'flag' -> condition on flag 0.
    state_rule: Optional[str] = None


REGISTRY: dict[str, Spec] = {}


def register(spec: Spec):
    REGISTRY[spec.id] = spec
    return spec


# ------------------------------------------------------------------ writing helpers
def save_png(arr: np.ndarray, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    to_image(arr).save(path, optimize=True)


def save_json(obj, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def anim_meta(frametime: int, interpolate=True):
    return {'animation': {'frametime': frametime, 'interpolate': interpolate}}


# ------------------------------------------------------------------ glow animation
def shimmer_frames(canvas: Canvas, albedo: np.ndarray, glow: np.ndarray, frames: int):
    """Default emissive animation: a bright band sweeps diagonally over the glowing pixels."""
    top, *_ = canvas.compose()
    out = []
    h, w = glow.shape
    for k in range(frames):
        f = np.zeros((h, w, 4), np.uint8)
        phase = k / frames
        for y in range(h):
            for x in range(w):
                g = glow[y, x]
                if g <= 0:
                    continue
                ramp = RAMP_RGB[MATERIALS[canvas.layers[top[y, x]].material].ramp]
                rgb = tuple(albedo[y, x, :3])
                idx = ramp.index(rgb) if rgb in ramp else 5
                wave = 0.5 + 0.5 * math.cos(2 * math.pi * (phase - (x + y) / 96.0))
                boost = int(round(wave * 2 * g))
                f[y, x, :3] = ramp[min(7, max(3, idx) + boost)]
                f[y, x, 3] = 255
        out.append(f)
    return out


def labpbr_for_glow(frames: list[np.ndarray], spec_base: np.ndarray, normal_base: np.ndarray, glow: np.ndarray):
    """Normal/specular strips for a glow texture: same normals, emissive alpha follows the frame brightness."""
    ns, ss = [], []
    for f in frames:
        occ = f[..., 3] > 0
        n = normal_base.copy()
        n[~occ] = (128, 128, 255, 0)
        s = spec_base.copy()
        lum = f[..., :3].astype(float).mean(axis=2) / 255.0
        s[..., 3] = np.where(occ, np.clip(60 + 194 * lum * np.maximum(glow, 0.4), 1, 254), 0).astype(np.uint8)
        s[~occ] = 0
        ns.append(n)
        ss.append(s)
    return np.concatenate(ns, 0), np.concatenate(ss, 0)


# ------------------------------------------------------------------ model building
def greedy_rects(grid: np.ndarray):
    """Greedy-merges equal non-zero cells into rectangles. Returns [(x0, y0, x1, y1, value)]."""
    h, w = grid.shape
    used = np.zeros_like(grid, bool)
    rects = []
    for y in range(h):
        x = 0
        while x < w:
            v = grid[y, x]
            if v == 0 or used[y, x]:
                x += 1
                continue
            x1 = x
            while x1 < w and grid[y, x1] == v and not used[y, x1]:
                x1 += 1
            y1 = y + 1
            while y1 < h and all(grid[y1, xx] == v and not used[y1, xx] for xx in range(x, x1)):
                y1 += 1
            used[y:y1, x:x1] = True
            rects.append((x, y, x1, y1, float(v)))
            x = x1
    return rects


def r4(v):
    return round(v / 4.0, 4)


def build_elements(depth: np.ndarray, glow_union: np.ndarray, base_tex='#0', glow_tex='#glow'):
    h, w = depth.shape
    # Quantise depth so neighbouring pixels of one shape merge.
    q = np.round(depth * 4) / 4
    elements = []
    for x0, y0, x1, y1, d in greedy_rects(q):
        z0, z1 = 8 - d / 2, 8 + d / 2
        faces = {
            'south': {'uv': [r4(x0), r4(y0), r4(x1), r4(y1)], 'texture': base_tex},
            'north': {'uv': [r4(x1), r4(y0), r4(x0), r4(y1)], 'texture': base_tex},
        }

        def exposed(cells):
            return any(not (0 <= cx < w and 0 <= cy < h) or q[cy, cx] < d for cx, cy in cells)

        if exposed([(x1, yy) for yy in range(y0, y1)]):
            faces['east'] = {'uv': [r4(x1 - 1), r4(y0), r4(x1), r4(y1)], 'texture': base_tex}
        if exposed([(x0 - 1, yy) for yy in range(y0, y1)]):
            faces['west'] = {'uv': [r4(x0), r4(y0), r4(x0 + 1), r4(y1)], 'texture': base_tex}
        if exposed([(xx, y0 - 1) for xx in range(x0, x1)]):
            faces['up'] = {'uv': [r4(x0), r4(y0), r4(x1), r4(y0 + 1)], 'texture': base_tex}
        if exposed([(xx, y1) for xx in range(x0, x1)]):
            faces['down'] = {'uv': [r4(x0), r4(y1 - 1), r4(x1), r4(y1)], 'texture': base_tex}
        elements.append({
            'name': f'relief_{x0}_{y0}',
            'from': [r4(x0), round(16 - y1 / 4, 4), round(z0, 4)],
            'to': [r4(x1), round(16 - y0 / 4, 4), round(z1, 4)],
            'faces': faces,
        })
    # Emissive overlay planes just in front of and behind the glowing pixels.
    gq = np.where(glow_union, q, 0)
    for x0, y0, x1, y1, d in greedy_rects(gq):
        for side, z in (('south', 8 + d / 2 + 0.02), ('north', 8 - d / 2 - 0.02)):
            uv = [r4(x0), r4(y0), r4(x1), r4(y1)] if side == 'south' else [r4(x1), r4(y0), r4(x0), r4(y1)]
            elements.append({
                'name': f'glow_{side}_{x0}_{y0}',
                'from': [r4(x0), round(16 - y1 / 4, 4), round(z, 4)],
                'to': [r4(x1), round(16 - y0 / 4, 4), round(z, 4)],
                'shade': False,
                'light_emission': 15,
                'faces': {side: {'uv': uv, 'texture': glow_tex}},
            })
    return elements


def write_model(name: str, elements, display, has_glow: bool):
    textures = {'0': f'shardwatch:item/{name}', 'particle': f'shardwatch:item/{name}'}
    if has_glow:
        textures['glow'] = f'shardwatch:item/{name}_glow'
    model = {
        'credit': 'Shardwatch — generated by tools/generate_pack.py (Blockbench Java Block/Item format)',
        'texture_size': [64, 64],
        'gui_light': 'front',
        'textures': textures,
        'elements': elements,
        'display': display,
    }
    save_json(model, NS / 'models' / 'item' / f'{name}.json')
    return model


def write_bbmodel(name: str, model: dict, tex_paths: dict[str, Path], frametime: int):
    """Writes an editable Blockbench project (java_block format) with the textures embedded."""
    tex_ids = {}
    textures = []
    for i, (key, path) in enumerate(tex_paths.items()):
        data = base64.b64encode(path.read_bytes()).decode()
        img = Image.open(path)
        tex_ids[key] = i
        entry = {
            'path': '', 'name': path.name, 'folder': 'item', 'namespace': 'shardwatch', 'id': str(i),
            'width': img.width, 'height': img.height, 'uv_width': 64, 'uv_height': 64,
            'particle': key == '0', 'render_mode': 'emissive' if key == 'glow' else 'default',
            'frame_time': frametime if img.height > img.width else 1, 'frame_interpolate': True,
            'uuid': str(uuid.uuid4()), 'relative_path': f'../resourcepack/assets/shardwatch/textures/item/{path.name}',
            'source': 'data:image/png;base64,' + data,
        }
        textures.append(entry)
    elements = []
    outliner = []
    for e in model['elements']:
        uid = str(uuid.uuid4())
        faces = {}
        for side in ('north', 'east', 'south', 'west', 'up', 'down'):
            f = e['faces'].get(side)
            if f:
                faces[side] = {'uv': [v * 4 for v in f['uv']], 'texture': tex_ids[f['texture'].lstrip('#')]}
            else:
                faces[side] = {'uv': [0, 0, 0, 0], 'texture': None}
        el = {
            'name': e['name'], 'box_uv': False, 'rescale': False, 'locked': False, 'render_order': 'default',
            'allow_mirror_modeling': True, 'from': e['from'], 'to': e['to'], 'autouv': 0, 'color': 0,
            'origin': [8, 8, 8], 'faces': faces, 'type': 'cube', 'uuid': uid,
            'shade': e.get('shade', True), 'light_emission': e.get('light_emission', 0),
        }
        elements.append(el)
        outliner.append(uid)
    project = {
        'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False},
        'name': name, 'parent': '', 'ambientocclusion': True, 'front_gui_light': True,
        'visible_box': [1, 1, 0], 'variable_placeholders': '', 'variable_placeholder_buttons': [],
        'unhandled_root_fields': {}, 'resolution': {'width': 64, 'height': 64},
        'elements': elements, 'outliner': [{'name': name, 'origin': [8, 8, 8], 'color': 0, 'uuid': str(uuid.uuid4()),
                                            'export': True, 'mirror_uv': False, 'isOpen': True, 'locked': False,
                                            'visibility': True, 'autouv': 0, 'children': outliner}],
        'textures': textures, 'display': model['display'],
    }
    BB.mkdir(parents=True, exist_ok=True)
    (BB / f'{name}.bbmodel').write_text(json.dumps(project))


# ------------------------------------------------------------------ one asset
def build_variant(spec: Spec, name: str, tier: int, state: Optional[str]):
    c = Canvas()
    spec.draw(c, tier, state)
    albedo, normal, specular, depth, glow = c.render()
    tdir = NS / 'textures' / 'item'
    save_png(albedo, tdir / f'{name}.png')
    save_png(normal, tdir / f'{name}_n.png')
    save_png(specular, tdir / f'{name}_s.png')
    has_glow = bool((glow > 0).any())
    tex_paths = {'0': tdir / f'{name}.png'}
    glow_union = glow > 0
    if has_glow:
        if spec.glow:
            frames = [spec.glow(c, albedo, glow, k / spec.frames, tier, state) for k in range(spec.frames)]
        else:
            frames = shimmer_frames(c, albedo, glow, spec.frames)
        glow_union = np.zeros_like(glow_union)
        for f in frames:
            glow_union |= f[..., 3] > 0
        strip = np.concatenate(frames, 0)
        save_png(strip, tdir / f'{name}_glow.png')
        g_n, g_s = labpbr_for_glow(frames, specular, normal, np.maximum(glow, 0.5 * glow_union))
        save_png(g_n, tdir / f'{name}_glow_n.png')
        save_png(g_s, tdir / f'{name}_glow_s.png')
        for suffix in ('', '_n', '_s'):
            save_json(anim_meta(spec.frametime), tdir / f'{name}_glow{suffix}.png.mcmeta')
        tex_paths['glow'] = tdir / f'{name}_glow.png'
    elements = build_elements(depth, glow_union)
    model = write_model(name, elements, spec.display, has_glow)
    return model, tex_paths, albedo


def item_definition(spec: Spec) -> dict:
    base = {'type': 'minecraft:model', 'model': f'shardwatch:item/{spec.id}'}
    tiered = base
    if spec.tiers > 1:
        tiered = {
            'type': 'minecraft:select', 'property': 'minecraft:custom_model_data', 'index': 0,
            'cases': [{'when': f't{t}', 'model': {'type': 'minecraft:model', 'model': f'shardwatch:item/{spec.id}_t{t}'}}
                      for t in range(2, spec.tiers + 1)],
            'fallback': base,
        }
    if spec.states and spec.state_rule == 'cooldown':
        return {'model': {
            'type': 'minecraft:range_dispatch', 'property': 'minecraft:cooldown',
            'entries': [{'threshold': 0.01, 'model': {'type': 'minecraft:model',
                                                      'model': f'shardwatch:item/{spec.id}_{spec.states[0]}'}}],
            'fallback': tiered}}
    if spec.states and spec.state_rule == 'flag':
        return {'model': {
            'type': 'minecraft:condition', 'property': 'minecraft:custom_model_data', 'index': 0,
            'on_true': {'type': 'minecraft:model', 'model': f'shardwatch:item/{spec.id}_{spec.states[0]}'},
            'on_false': tiered}}
    return {'model': tiered}


def build(spec: Spec):
    variants = [(spec.id, 1, None)] + [(f'{spec.id}_t{t}', t, None) for t in range(2, spec.tiers + 1)] \
        + [(f'{spec.id}_{s}', 1, s) for s in spec.states]
    albedos = {}
    for name, tier, state in variants:
        model, tex_paths, albedo = build_variant(spec, name, tier, state)
        write_bbmodel(name, model, tex_paths, spec.frametime)
        albedos[name] = albedo
    save_json(item_definition(spec), NS / 'items' / f'{spec.id}.json')
    return albedos


# ------------------------------------------------------------------ pack skeleton
def pack_meta():
    meta = {
        'pack': {
            'description': [
                {'text': '✦ Shardwatch ', 'color': '#F59AC8', 'bold': True},
                {'text': 'crystal staff suite', 'color': '#7FE8E0'},
            ],
            'min_format': 75,
            'max_format': 75,
        }
    }
    save_json(meta, PACK / 'pack.mcmeta')


def pack_icon():
    c = Canvas()
    c.plate(c.ring(32, 32, 22, 29), 'silver', depth=1.0, bevel=2.5)
    pts = [(32, 9), (49, 22), (44, 47), (20, 47), (15, 22)]
    c.gem(pts, 'pink_gem', depth=2, table_scale=0.42)
    c.gem([(32, 22), (40, 29), (37, 40), (27, 40), (24, 29)], 'teal_gem', depth=3, table_scale=0.4)
    for sx, sy in ((25, 18), (41, 31), (30, 27)):
        c.sparkle(sx, sy, 2)
    albedo, *_ = c.render()
    save_png(albedo, PACK / 'pack.png')


def main():
    if NS.exists():
        shutil.rmtree(NS)
    for stale in BB.glob('*.bbmodel'):
        stale.unlink()
    pack_meta()
    pack_icon()
    # Art modules register their assets when imported. Missing modules = stages not built yet.
    built = {}
    for module in ('art.tools', 'art.badges', 'art.icons', 'art.particles', 'art.gui', 'art.fonts'):
        try:
            mod = importlib.import_module(module)
        except ModuleNotFoundError as e:
            if e.name and e.name.startswith('art'):
                continue
            raise
        if hasattr(mod, 'build_extra'):
            mod.build_extra(NS)
    for spec in REGISTRY.values():
        built.update(build(spec))
        print(f'  built {spec.id} ({spec.kind})')
    try:
        sounds = importlib.import_module('synth_sounds')
        sounds.build(NS)
    except ModuleNotFoundError:
        pass
    print(f'Generated {len(REGISTRY)} model assets into {PACK.relative_to(ROOT)}')
    return built


if __name__ == '__main__':
    # Art modules do `from generate_pack import register`; make that resolve to this running module.
    sys.modules.setdefault('generate_pack', sys.modules['__main__'])
    main()
