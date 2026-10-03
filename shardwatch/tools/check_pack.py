#!/usr/bin/env python3
"""
Checks every texture, model, sound and particle in resourcepack/ against the "Look & textures" list.

    python3 tools/check_pack.py --stage 2 --write   # writes docs/stage-reports/stage-2.md

Each requirement is a separate column so the report shows exactly what does not match yet.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
import assets as A  # noqa: E402
from pixelart import PALETTE, RAMP_RGB, hexrgb  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / 'resourcepack'
NS = PACK / 'assets' / 'shardwatch'
BB = ROOT / 'blockbench'

PAL = np.array(PALETTE, float)
DARK = {c for r in RAMP_RGB.values() for c in r[:2]}
TOP = {c for r in RAMP_RGB.values() for c in r[6:]} | {(255, 255, 255)}


def tex_path(name: str) -> Path:
    return NS / 'textures' / 'item' / f'{name}.png'


def load(p: Path):
    return np.array(Image.open(p).convert('RGBA'))


# ------------------------------------------------------------------ texture checks
def check_size(img, frames_ok=True):
    h, w = img.shape[:2]
    if w != 64:
        return f'width {w} ≠ 64'
    if h % 64 or (h != 64 and not frames_ok):
        return f'height {h} not a multiple of 64'
    return None


def palette_share(img):
    px = img[..., :3][img[..., 3] > 0].astype(float)
    if len(px) == 0:
        return 0.0
    d = np.min(((px[:, None, :] - PAL[None, :, :]) ** 2).sum(-1), axis=1)
    return float((d <= 12 ** 2).mean())


def has_pink_and_teal(img):
    px = {tuple(c) for c in img[..., :3][img[..., 3] > 0]}
    pink = set(RAMP_RGB['pink']) | set(RAMP_RGB['rose'])
    teal = set(RAMP_RGB['teal']) | set(RAMP_RGB['ice'])
    return bool(px & pink), bool(px & teal)


def outline_share(img):
    a = img[..., 3] > 0
    edge = a & ~(np.roll(a, 1, 0) & np.roll(a, -1, 0) & np.roll(a, 1, 1) & np.roll(a, -1, 1))
    cols = img[..., :3][edge]
    if len(cols) == 0:
        return 0.0
    return sum(tuple(c) in DARK for c in cols) / len(cols)


def highlight_count(img):
    cols = img[..., :3][img[..., 3] > 0]
    return sum(tuple(c) in TOP for c in cols)


def light_direction_ok(img, normal_img):
    """
    The albedo must be shaded by ONE light from the top-left: brightness has to correlate with how much each pixel's
    normal (from the _n map) faces that light, and more strongly than with the opposite (bottom-right) light.
    """
    occ = (img[..., 3] > 0) & (normal_img[..., 3] > 0)
    if occ.sum() < 20:
        return True
    lum = (img[..., :3].astype(float) @ np.array([0.2126, 0.7152, 0.0722]))[occ]
    nx = normal_img[..., 0][occ] / 255 * 2 - 1
    ny = normal_img[..., 1][occ] / 255 * 2 - 1
    nz = np.sqrt(np.clip(1 - nx * nx - ny * ny, 0, 1))
    L = np.array([-0.55, -0.65, 0.52])
    L /= np.linalg.norm(L)
    lit = nx * L[0] + ny * L[1] + nz * L[2]
    opp = -nx * L[0] - ny * L[1] + nz * L[2]
    if lit.std() < 1e-6:
        return True
    c_lit = np.corrcoef(lum, lit)[0, 1]
    c_opp = np.corrcoef(lum, opp)[0, 1] if opp.std() > 1e-6 else -1
    return c_lit > 0.1 and c_lit > c_opp


def check_labpbr(base: Path, frames: int):
    errs = []
    for suffix in ('_n', '_s'):
        p = base.with_name(base.stem + suffix + '.png')
        if not p.exists():
            errs.append(f'missing {p.name}')
            continue
        img = load(p)
        if img.shape[0] != 64 * frames or img.shape[1] != 64:
            errs.append(f'{p.name} size {img.shape[1]}x{img.shape[0]}')
        if suffix == '_n':
            occ = img[..., 3] > 0
            if occ.any():
                nx = img[..., 0][occ] / 255 * 2 - 1
                ny = img[..., 1][occ] / 255 * 2 - 1
                if (nx * nx + ny * ny > 1.05).mean() > 0.02:
                    errs.append('_n has invalid (non-unit) normals')
        else:
            g = img[..., 1][img[..., 3] > 0]
            if len(g) and g.max() > 237:
                errs.append('_s green beyond LabPBR metal ids')
        if frames > 1:
            mc = p.with_name(p.name + '.mcmeta')
            if not mc.exists():
                errs.append(f'missing {mc.name}')
    return errs


# ------------------------------------------------------------------ model checks
def model_extent(model):
    els = model.get('elements', [])
    if not els:
        return 0, 0, 0
    mins = np.min([e['from'] for e in els], axis=0)
    maxs = np.max([e['to'] for e in els], axis=0)
    return tuple(maxs - mins)


def check_model(name: str, glow: bool):
    p = NS / 'models' / 'item' / f'{name}.json'
    if not p.exists():
        return [f'missing models/item/{name}.json']
    m = json.loads(p.read_text())
    errs = []
    els = m.get('elements', [])
    if len(els) < 3:
        errs.append(f'only {len(els)} elements (flat)')
    ex = model_extent(m)
    if min(ex) <= 0.25:
        errs.append(f'not 3D (extent {ex[0]:.2f}×{ex[1]:.2f}×{ex[2]:.2f})')
    disp = m.get('display', {})
    missing = [s for s in A.DISPLAY_SLOTS if s not in disp]
    if missing:
        errs.append('display missing ' + ', '.join(missing))
    if glow and not any(e.get('light_emission', 0) > 0 for e in els):
        errs.append('no emissive (light_emission) element')
    for key, ref in m.get('textures', {}).items():
        ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
        if ns == 'shardwatch' and not (NS / 'textures' / f'{path}.png').exists():
            errs.append(f'texture {ref} missing')
    return errs


def check_item_def(a):
    p = NS / 'items' / f'{a.id}.json'
    if not p.exists():
        return [f'missing items/{a.id}.json']
    text = p.read_text()
    errs = []
    if f'shardwatch:item/{a.id}' not in text:
        errs.append('item definition does not point at its model')
    for t in range(2, a.tiers + 1):
        if f'"t{t}"' not in text:
            errs.append(f'no tier {t} case')
    for s in a.states:
        if f'shardwatch:item/{a.id}_{s}' not in text:
            errs.append(f'no {s} state')
    return errs


# ------------------------------------------------------------------ asset check
COLUMNS = ['64×64', 'Palette', 'Shading', '3D model', 'Display', 'Animated', 'Emissive', 'LabPBR', 'item_model', 'Blockbench']


def check_asset(a):
    """Returns {column: None (ok) | str (problem) | '—' (not applicable)} for one asset."""
    res = {c: '—' for c in COLUMNS}
    if a.kind == 'font':
        p = NS / 'textures' / f'{a.id}.png'
        res['64×64'] = None if p.exists() else 'missing'
        if p.exists():
            img = load(p)
            share = palette_share(img)
            res['Palette'] = None if share >= 0.85 else f'{share:.0%} in palette'
        return res
    if a.kind == 'sprite':
        p = NS / 'textures' / 'gui' / 'sprites' / f'{a.id}.png'
        if not p.exists():
            res['64×64'] = 'missing'
            return res
        img = load(p)
        res['64×64'] = check_size(img, frames_ok=False)
        share = palette_share(img)
        res['Palette'] = None if share >= 0.85 else f'{share:.0%} in palette'
        mc = p.with_name(p.name + '.mcmeta')
        res['Display'] = None if mc.exists() and 'nine_slice' in mc.read_text() else 'no nine_slice .mcmeta'
        return res

    names = [a.id] + [f'{a.id}_t{t}' for t in range(2, a.tiers + 1)] + [f'{a.id}_{s}' for s in a.states]
    tex_errs, pal_errs, shade_errs, model_errs, disp_errs, anim_errs, emis_errs, pbr_errs = ([] for _ in range(8))
    for n in names:
        tp = tex_path(n)
        if not tp.exists():
            tex_errs.append(f'{n}.png missing')
            continue
        img = load(tp)
        e = check_size(img, frames_ok=False)
        if e:
            tex_errs.append(f'{n}: {e}')
        share = palette_share(img)
        if share < 0.85:
            pal_errs.append(f'{n}: {share:.0%} in palette')
        pink, teal = has_pink_and_teal(img)
        if not (pink and teal):
            pal_errs.append(f'{n}: needs both pink and teal')
        if outline_share(img) < 0.7:
            shade_errs.append(f'{n}: outline {outline_share(img):.0%}')
        if highlight_count(img) < 3:
            shade_errs.append(f'{n}: no faceted highlights')
        np_ = tp.with_name(n + '_n.png')
        if np_.exists() and not light_direction_ok(img, load(np_)):
            shade_errs.append(f'{n}: light not from top-left')
        pbr_errs += check_labpbr(tp, 1)
        gp = tex_path(n + '_glow')
        if not gp.exists():
            anim_errs.append(f'{n}_glow.png missing')
            emis_errs.append(f'{n}: no glow layer')
        else:
            gimg = load(gp)
            frames = gimg.shape[0] // 64
            if gimg.shape[1] != 64 or gimg.shape[0] % 64:
                anim_errs.append(f'{n}_glow: frames not 64×64')
            mc = gp.with_name(gp.name + '.mcmeta')
            if frames < 2 or not mc.exists() or 'animation' not in mc.read_text():
                anim_errs.append(f'{n}_glow: not animated')
            pbr_errs += check_labpbr(gp, frames)
        for err in check_model(n, glow=True):
            (disp_errs if err.startswith('display') else emis_errs if 'emissive' in err else model_errs).append(f'{n}: {err}')
    res['64×64'] = '; '.join(tex_errs) or None
    res['Palette'] = '; '.join(pal_errs) or (None if not tex_errs else 'texture missing')
    res['Shading'] = '; '.join(shade_errs) or (None if not tex_errs else 'texture missing')
    res['3D model'] = '; '.join(model_errs) or None
    res['Display'] = '; '.join(disp_errs) or None
    res['Animated'] = '; '.join(anim_errs) or None
    res['Emissive'] = '; '.join(emis_errs) or None
    res['LabPBR'] = '; '.join(pbr_errs) or None
    res['item_model'] = '; '.join(check_item_def(a)) or None
    res['Blockbench'] = None if (BB / f'{a.id}.bbmodel').exists() else 'no .bbmodel'
    return res


# ------------------------------------------------------------------ global checks
def check_sounds():
    problems = []
    sj = NS / 'sounds.json'
    events = json.loads(sj.read_text()) if sj.exists() else {}
    for stage, names in A.SOUNDS.items():
        for name in names:
            if name not in events:
                problems.append(f'sound event shardwatch:{name} missing (stage {stage})')
                continue
            for s in events[name].get('sounds', []):
                sid = s if isinstance(s, str) else s['name']
                path = sid.split(':', 1)[1]
                if not (NS / 'sounds' / f'{path}.ogg').exists():
                    problems.append(f'{name}: {path}.ogg missing')
    cfg = (ROOT / 'src' / 'main' / 'resources' / 'config.yml').read_text()
    for key in sorted(set(re.findall(r'shardwatch:([a-z0-9_.]+)', cfg))):
        if key.startswith(('item/', 'particle/', 'crystal')):
            continue
        if key not in events:
            problems.append(f'config.yml uses sound shardwatch:{key}, not in sounds.json')
    return problems


def check_particles():
    problems = []
    cfg = (ROOT / 'src' / 'main' / 'resources' / 'config.yml').read_text()
    used = set(re.findall(r'ITEM:shardwatch:([a-z_/]+)', cfg))
    for p in A.PARTICLES:
        if p.id not in {u.split('/')[-1] for u in used}:
            problems.append(f'{p.id} is not used by any fx preset yet')
    for u in used:
        if not (NS / 'items' / f'{u.split("/")[-1]}.json').exists():
            problems.append(f'config uses ITEM:shardwatch:{u} but items/{u.split("/")[-1]}.json is missing')
    return problems


def check_namespace():
    bad = []
    mc = PACK / 'assets' / 'minecraft'
    if mc.exists():
        for f in mc.rglob('*'):
            if f.is_file():
                bad.append(str(f.relative_to(PACK)))
    return bad


def check_mcmeta():
    p = PACK / 'pack.mcmeta'
    if not p.exists():
        return ['pack.mcmeta missing']
    m = json.loads(p.read_text())['pack']
    errs = []
    if m.get('min_format', m.get('pack_format')) is None:
        errs.append('no pack format')
    fmt = m.get('min_format')
    fmt = fmt[0] if isinstance(fmt, list) else fmt
    if fmt is not None and fmt > 75:
        errs.append(f'min_format {fmt} is newer than 1.21.11 (75)')
    if not (PACK / 'pack.png').exists():
        errs.append('pack.png missing')
    return errs


# ------------------------------------------------------------------ report
def run(stage: int) -> tuple[str, int]:
    lines = [f'# Stage {stage} — pack check', '',
             'Generated by `tools/check_pack.py`. ✅ matches the Look & textures list, ❌ does not yet, — not applicable.',
             'Assets planned for a later stage are listed separately, so you can see what is still to come.', '']
    due = [a for a in A.ALL if a.stage <= stage]
    later = [a for a in A.ALL if a.stage > stage]
    failures = 0
    lines.append('| Asset | ' + ' | '.join(COLUMNS) + ' |')
    lines.append('|---|' + '---|' * len(COLUMNS))
    details = []
    for a in due:
        r = check_asset(a)
        cells = []
        for c in COLUMNS:
            v = r[c]
            if v == '—':
                cells.append('—')
            elif v is None:
                cells.append('✅')
            else:
                cells.append('❌')
                failures += 1
                details.append(f'- **{a.id}** · {c}: {v}')
        lines.append(f'| `{a.id}` | ' + ' | '.join(cells) + ' |')
    lines.append('')
    if details:
        lines += ['## Does not match yet', ''] + details + ['']
    glob = []
    glob += [f'pack: {e}' for e in check_mcmeta()]
    glob += [f'namespace: assets/{f} overrides vanilla' for f in check_namespace()]
    if stage >= 5:
        glob += [f'sounds: {e}' for e in check_sounds()]
        glob += [f'particles: {e}' for e in check_particles()]
    lines += ['## Pack-wide checks', '']
    if glob:
        lines += [f'- ❌ {g}' for g in glob]
        failures += len(glob)
    else:
        lines.append('- ✅ pack.mcmeta / pack.png valid, nothing under assets/minecraft'
                     + (', every sound and particle present' if stage >= 5 else ''))
    lines.append('')
    if later:
        lines += ['## Not built yet (later stages)', '']
        by_stage = {}
        for a in later:
            by_stage.setdefault(a.stage, []).append(a.id)
        for s in sorted(by_stage):
            lines.append(f'- Stage {s}: ' + ', '.join(f'`{i}`' for i in by_stage[s]))
        if stage < 5:
            lines.append(f'- Stage 5: {sum(len(v) for v in A.SOUNDS.values())} custom sound events, particle presets')
        lines.append('')
    lines.append(f'**Result: {failures} mismatch(es) in assets due by stage {stage}.**')
    return '\n'.join(lines) + '\n', failures


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--stage', type=int, default=6)
    ap.add_argument('--write', action='store_true')
    args = ap.parse_args()
    report, failures = run(args.stage)
    print(report)
    if args.write:
        out = ROOT / 'docs' / 'stage-reports' / f'stage-{args.stage}.md'
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(report)
        print(f'wrote {out.relative_to(ROOT)}')
    sys.exit(1 if failures and os.environ.get('STRICT') else 0)


if __name__ == '__main__':
    main()
