#!/usr/bin/env python3
"""
Synthesises every Shardwatch sound effect (no samples, no licences) and writes sounds.json + subtitles.

Building blocks:
  bell()     inharmonic crystal partials with exponential decay (amethyst-like chimes)
  shimmer()  scattered high grains (sparkle, glint)
  whoosh()   band-limited noise with a moving filter (veil, warp, rewind)
  thud()     pitched-down sine + click (gavel, encase)
Everything is mixed at 44.1 kHz mono and encoded as Ogg Vorbis with soundfile.

    python3 tools/synth_sounds.py      (also run by generate_pack.py)
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np

SR = 44100
RNG = np.random.default_rng(1729)  # deterministic output


def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def env(n, attack=0.004, decay=0.4, dur=None):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * np.exp(-t / max(decay, 1e-4))


def bell(freq, dur=1.2, decay=0.45, partials=((1, 1), (2.76, 0.45), (5.4, 0.25), (8.93, 0.12)), detune=0.003):
    t = t_axis(dur)
    out = np.zeros_like(t)
    for ratio, amp in partials:
        f = freq * ratio * (1 + RNG.uniform(-detune, detune))
        out += amp * np.sin(2 * np.pi * f * t) * np.exp(-t / (decay / (ratio ** 0.35)))
    return out * env(len(t), 0.002, 10)


def shimmer(dur=0.8, grains=40, lo=2400, hi=7000, decay=0.05):
    t = t_axis(dur)
    out = np.zeros_like(t)
    for _ in range(grains):
        start = RNG.uniform(0, dur * 0.8)
        f = RNG.uniform(lo, hi)
        g = (t >= start) * np.sin(2 * np.pi * f * (t - start)) * np.exp(-np.clip(t - start, 0, None) / decay)
        out += g * RNG.uniform(0.3, 1.0) * np.exp(-start / (dur * 0.6))
    return out / max(1, grains ** 0.5)


def lowpass(x, cutoff):
    """One-pole low-pass with a per-sample cutoff array or scalar."""
    c = np.broadcast_to(np.asarray(cutoff, float), x.shape)
    a = np.exp(-2 * np.pi * c / SR)
    y = np.zeros_like(x)
    prev = 0.0
    for i in range(len(x)):
        prev = (1 - a[i]) * x[i] + a[i] * prev
        y[i] = prev
    return y


def whoosh(dur=0.7, f0=300, f1=4000, peak=0.5):
    t = t_axis(dur)
    noise = RNG.normal(0, 1, len(t))
    cutoff = np.geomspace(f0, f1, len(t))
    hp = noise - lowpass(noise, cutoff * 0.25)
    y = lowpass(hp, cutoff)
    shape = np.exp(-((t / dur - peak) ** 2) / 0.045)
    return y * shape


def thud(freq=90, dur=0.5, drop=0.5):
    t = t_axis(dur)
    f = freq * (1 + drop * np.exp(-t / 0.04))
    phase = 2 * np.pi * np.cumsum(f) / SR
    body = np.sin(phase) * np.exp(-t / 0.12)
    click = RNG.normal(0, 1, len(t)) * np.exp(-t / 0.004) * 0.6
    return body + lowpass(click, 3000)


def arp(freqs, step=0.07, **kw):
    parts = [bell(f, **kw) for f in freqs]
    n = max(len(p) for p in parts) + int(step * SR * len(freqs))
    out = np.zeros(n)
    for i, p in enumerate(parts):
        s = int(i * step * SR)
        out[s:s + len(p)] += p
    return out


def mix(*layers):
    n = max(len(l) for l in layers)
    out = np.zeros(n)
    for l in layers:
        out[:len(l)] += l
    return out


def pad(x, seconds):
    return np.concatenate([np.zeros(int(seconds * SR)), x])


def finish(x, gain=0.8):
    x = x - np.mean(x)
    peak = np.max(np.abs(x)) or 1.0
    x = x / peak * gain
    fade = min(len(x), int(0.02 * SR))
    x[-fade:] *= np.linspace(1, 0, fade)
    return x.astype(np.float32)


# Note frequencies (A4 = 440)
def n(semitones_from_a4):
    return 440.0 * 2 ** (semitones_from_a4 / 12)


C6, E6, G6, A5, B5, D6, F6, C5, E5, G5, A4 = (n(15), n(19), n(22), n(12), n(14), n(17), n(20), n(3), n(7), n(10), n(0))


SOUNDS = {
    # UI
    'ui/click': lambda: bell(C6 * 2, 0.18, 0.04),
    'ui/open': lambda: arp([E6, G6, C6 * 2], 0.045, dur=0.6, decay=0.25),
    'ui/page': lambda: mix(whoosh(0.22, 1500, 6000, 0.4) * 0.5, bell(G6, 0.3, 0.06) * 0.4),
    'ui/deny': lambda: mix(bell(A4 * 1.06, 0.35, 0.12), bell(A4, 0.35, 0.12)),
    'ui/confirm': lambda: arp([C6, G6], 0.06, dur=0.5, decay=0.2),
    # Flares
    'flare/send': lambda: mix(whoosh(0.6, 400, 6000, 0.35) * 0.6, pad(arp([C6, E6, G6], 0.05, dur=0.8, decay=0.3), 0.15)),
    'flare/alert': lambda: arp([G6, C6 * 2, G6, C6 * 2], 0.09, dur=0.7, decay=0.22),
    'flare/claim': lambda: mix(bell(E6, 0.6, 0.18), shimmer(0.4, 12) * 0.5),
    'flare/resolve': lambda: arp([C6, E6, G6, C6 * 2], 0.07, dur=1.0, decay=0.4),
    # Verdicts
    'verdict/chip': lambda: mix(bell(B5 * 2, 0.4, 0.08), RNG.normal(0, 1, int(0.03 * SR)) * 0.3),
    'verdict/hush': lambda: mix(bell(D6, 1.0, 0.5) * np.exp(-t_axis(1.0) / 0.3), whoosh(0.8, 3000, 300, 0.3) * 0.5),
    'verdict/eject': lambda: mix(whoosh(0.5, 500, 5000, 0.6), pad(bell(A5, 0.5, 0.15), 0.2)),
    'verdict/encase': lambda: mix(thud(70, 0.8, 0.8), pad(arp([A4, E5, A5, C6], 0.06, dur=1.4, decay=0.6), 0.05),
                                  pad(shimmer(1.2, 50) * 0.6, 0.3)),
    'verdict/petrify': lambda: mix(arp([F6, D6, B5, G5], 0.05, dur=0.9, decay=0.35), shimmer(0.9, 60, 4000, 9000, 0.03) * 0.8),
    'verdict/release': lambda: arp([G5, B5, D6, F6], 0.05, dur=0.9, decay=0.35),
    'gavel/hit': lambda: mix(thud(110, 0.45, 0.6), bell(E6, 0.6, 0.12) * 0.5),
    # Echoes & Rewind
    'echo/inspect': lambda: mix(bell(G6, 0.5, 0.12), pad(bell(G6, 0.4, 0.1) * 0.35, 0.12), pad(bell(G6, 0.3, 0.08) * 0.15, 0.24)),
    'rewind/start': lambda: whoosh(1.0, 6000, 300, 0.7)[::-1].copy() * 0.8 + mix(bell(C5, 1.0, 0.5)) * 0.2,
    'rewind/tick': lambda: bell(C6 * 2, 0.12, 0.03),
    'rewind/done': lambda: mix(arp([C5, G5, C6, E6, G6], 0.06, dur=1.4, decay=0.6), pad(shimmer(1.0, 40) * 0.5, 0.25)),
    # Glint
    'glint/alert': lambda: mix(shimmer(0.7, 30, 5000, 10000, 0.03), arp([E6 * 2, B5 * 2], 0.08, dur=0.5, decay=0.15) * 0.6),
    'monocle/focus': lambda: mix(whoosh(0.35, 2000, 8000, 0.5) * 0.4, pad(bell(B5 * 2, 0.4, 0.1), 0.1)),
    # Veil & travel
    'veil/on': lambda: mix(whoosh(0.9, 6000, 400, 0.4), pad(bell(A5, 0.7, 0.3) * 0.4, 0.2)),
    'veil/off': lambda: mix(whoosh(0.9, 400, 6000, 0.6), bell(E6, 0.7, 0.3) * 0.4),
    'compass/warp': lambda: mix(whoosh(0.6, 300, 7000, 0.5), pad(arp([E6, B5 * 2], 0.05, dur=0.5, decay=0.2), 0.3)),
    # Lustre & progression
    'lustre/pickup': lambda: arp([C6 * 2, E6 * 2], 0.04, dur=0.4, decay=0.12),
    'lustre/levelup': lambda: mix(arp([C5, E5, G5, C6, E6, G6, C6 * 2], 0.07, dur=1.6, decay=0.7), pad(shimmer(1.4, 70) * 0.6, 0.4)),
    'refine/upgrade': lambda: mix(thud(160, 0.3, 0.3) * 0.6, pad(arp([G5, D6, G6], 0.06, dur=1.0, decay=0.4), 0.08)),
    'keepsake/claim': lambda: mix(arp([E6, G6, B5 * 2, E6 * 2], 0.05, dur=1.0, decay=0.35), shimmer(0.8, 30) * 0.4),
    'facet/promote': lambda: mix(arp([C5, G5, C6, E6, G6, C6 * 2], 0.09, dur=1.8, decay=0.8), pad(thud(80, 0.6) * 0.4, 0.0)),
    # Misc
    'tool/equip': lambda: mix(bell(G6, 0.4, 0.1), shimmer(0.3, 10) * 0.4),
    'hum/ping': lambda: bell(A5 * 2, 0.35, 0.1) * 0.6,
}

# Event name -> (sound files, subtitle)
EVENTS = {
    'ui.click': (['ui/click'], 'Crystal clicks'), 'ui.open': (['ui/open'], 'Menu chimes open'),
    'ui.page': (['ui/page'], 'Page turns'), 'ui.deny': (['ui/deny'], 'Crystal refuses'),
    'ui.confirm': (['ui/confirm'], 'Crystal confirms'),
    'flare.send': (['flare/send'], 'Flare launched'), 'flare.alert': (['flare/alert'], 'Flare alert'),
    'flare.claim': (['flare/claim'], 'Flare claimed'), 'flare.resolve': (['flare/resolve'], 'Flare resolved'),
    'verdict.chip': (['verdict/chip'], 'Crystal chips'), 'verdict.hush': (['verdict/hush'], 'Voice hushed'),
    'verdict.eject': (['verdict/eject'], 'Player ejected'), 'verdict.encase': (['verdict/encase'], 'Crystal encases'),
    'verdict.petrify': (['verdict/petrify'], 'Crystal petrifies'), 'verdict.release': (['verdict/release'], 'Crystal shell shatters'),
    'gavel.hit': (['gavel/hit'], 'Gavel strikes'), 'echo.inspect': (['echo/inspect'], 'Echoes answer'),
    'rewind.start': (['rewind/start'], 'Time pours back'), 'rewind.tick': (['rewind/tick'], 'Block restored'),
    'rewind.done': (['rewind/done'], 'Rewind complete'), 'glint.alert': (['glint/alert'], 'Something glints'),
    'veil.on': (['veil/on'], 'Veil falls'), 'veil.off': (['veil/off'], 'Veil lifts'),
    'compass.warp': (['compass/warp'], 'Compass warps'), 'monocle.focus': (['monocle/focus'], 'Monocle focuses'),
    'lustre.pickup': (['lustre/pickup'], 'Lustre absorbed'), 'lustre.levelup': (['lustre/levelup'], 'Clarity rises'),
    'refine.upgrade': (['refine/upgrade'], 'Tool refined'), 'keepsake.claim': (['keepsake/claim'], 'Keepsake claimed'),
    'facet.promote': (['facet/promote'], 'Facet granted'), 'tool.equip': (['tool/equip'], 'Staff tool hums'),
    'hum.ping': (['hum/ping'], 'Staff hum'),
}


def build(ns: Path):
    import soundfile as sf
    sounds_dir = ns / 'sounds'
    for name, fn in SOUNDS.items():
        path = sounds_dir / f'{name}.ogg'
        path.parent.mkdir(parents=True, exist_ok=True)
        sf.write(str(path), finish(fn()), SR, format='OGG', subtype='VORBIS')
    sounds_json = {}
    subtitles = {}
    for event, (files, subtitle) in EVENTS.items():
        key = f'subtitles.shardwatch.{event}'
        sounds_json[event] = {'subtitle': key, 'sounds': [{'name': f'shardwatch:{f}', 'volume': 1.0} for f in files]}
        subtitles[key] = subtitle
    (ns / 'sounds.json').write_text(json.dumps(sounds_json, indent=2) + '\n')
    (ns / 'lang').mkdir(parents=True, exist_ok=True)
    (ns / 'lang' / 'en_us.json').write_text(json.dumps(subtitles, indent=2, ensure_ascii=False) + '\n')
    print(f'  synthesised {len(SOUNDS)} sounds for {len(EVENTS)} events')


if __name__ == '__main__':
    build(Path(__file__).resolve().parent.parent / 'resourcepack' / 'assets' / 'shardwatch')
