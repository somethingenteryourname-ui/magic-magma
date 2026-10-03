"""
Shardwatch pixel-art engine.

Every texture is built from shapes. A shape adds coverage, a material, a surface normal (from a height field or an
explicit facet normal), a model thickness and optional glow. Rendering then:

  1. lights every pixel with ONE light direction (top-left-front) and quantises it onto the material's colour ramp,
     with ordered dithering for soft metals,
  2. adds specular sparkles on shiny materials,
  3. casts a 1-step shadow from shapes in front onto shapes behind (down-right of them),
  4. draws a selective outline: lighter on lit (top/left) edges, darker on shadowed (bottom/right) edges.

Because the same normals and materials drive the colour, the LabPBR normal (_n) and specular (_s) maps match the
albedo exactly. The thickness map is used by generate_pack.py to build 3D extruded Blockbench-style models.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np
from PIL import Image

SIZE = 64

# One light for everything: from the top-left, slightly in front. Image space: +x right, +y down, +z toward viewer.
LIGHT = np.array([-0.55, -0.65, 0.52])
LIGHT = LIGHT / np.linalg.norm(LIGHT)
VIEW = np.array([0.0, 0.0, 1.0])
HALF = (LIGHT + VIEW) / np.linalg.norm(LIGHT + VIEW)

# LabPBR stores normals in DirectX (Y-) form. Flip this to switch to OpenGL (Y+).
NORMAL_Y_DOWN = True


def hexrgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip('#')
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


# ----------------------------------------------------------------------------------------------- palette
# Eight-step ramps, darkest -> lightest. Every albedo pixel comes from one of these.
RAMPS: dict[str, list[str]] = {
    'pink':   ['#3A0F2A', '#6B1A47', '#9E2E68', '#C94A8A', '#E673A8', '#F59AC8', '#FBC3DD', '#FFE9F4'],
    'rose':   ['#2C0A1E', '#56133A', '#851F57', '#B0306F', '#D9468F', '#EE6FAA', '#F8A6CB', '#FFDDEB'],
    'teal':   ['#062A2E', '#0D4A4F', '#14706F', '#1F9A94', '#3FC4BA', '#7FE8E0', '#B8F5EE', '#EAFFFB'],
    'ice':    ['#18263B', '#2B4664', '#43708E', '#6C9EB8', '#9BCCE0', '#C6EEF8', '#E6FAFF', '#FFFFFF'],
    'silver': ['#1D1A29', '#38344E', '#595675', '#807D9E', '#A8A6C3', '#CECEE1', '#ECECF6', '#FFFFFF'],
    'gold':   ['#2D1316', '#592A28', '#8A4A3D', '#B77357', '#D99B78', '#EFC2A0', '#FAE2CC', '#FFF6EC'],
    'plum':   ['#13091A', '#25122F', '#3A1D47', '#55305F', '#71497A', '#8F6596', '#B08AB5', '#D3B6D6'],
}
RAMP_RGB = {k: [hexrgb(c) for c in v] for k, v in RAMPS.items()}
PALETTE = sorted({c for ramp in RAMP_RGB.values() for c in ramp})


@dataclass
class Material:
    ramp: str
    smooth: int          # LabPBR red: perceptual smoothness 0-255
    f0: int              # LabPBR green: 0-229 linear F0, 230-237 hardcoded metals
    sss: int             # LabPBR blue: 0-64 porosity, 65-255 subsurface
    shininess: float     # specular exponent for the sparkle pass
    spec_cut: float      # spec value above which the pixel becomes the top of the ramp
    dither: bool = False
    ambient: float = 0.30


MATERIALS: dict[str, Material] = {
    # Gems: glassy, translucent, very smooth.
    'pink_gem':  Material('pink', 235, 18, 140, 40, 0.55),
    'rose_gem':  Material('rose', 235, 18, 140, 40, 0.55),
    'teal_gem':  Material('teal', 235, 18, 140, 40, 0.55),
    'ice_gem':   Material('ice', 240, 22, 150, 50, 0.50),
    # Metals: LabPBR hardcoded ids (230 iron, 231 gold, 237 silver).
    'silver':    Material('silver', 205, 237, 0, 24, 0.70, dither=True),
    'gold':      Material('gold', 200, 231, 0, 24, 0.70, dither=True),
    'steel':     Material('silver', 170, 230, 0, 16, 0.80, dither=True),
    # Organic / matte.
    'plum_wood': Material('plum', 70, 10, 30, 6, 0.95, dither=True, ambient=0.34),
    'cloth':     Material('rose', 55, 8, 90, 4, 0.98, ambient=0.34),
    'teal_cloth': Material('teal', 55, 8, 90, 4, 0.98, ambient=0.34),
    'paper':     Material('ice', 60, 10, 40, 4, 0.98, ambient=0.40),
}


@dataclass
class Layer:
    mask: np.ndarray
    material: str
    normals: np.ndarray       # H x W x 3
    height: np.ndarray        # H x W, 0..1
    depth: float              # model thickness in model units (1 unit = 4 texels)
    glow: float = 0.0         # 0..1 emissive strength
    order: int = 0


class Canvas:
    """A stack of shapes on a SIZE x SIZE grid (or any w x h)."""

    def __init__(self, w: int = SIZE, h: int = SIZE):
        self.w, self.h = w, h
        self.layers: list[Layer] = []
        self.sparkles: list[tuple[int, int, int, str]] = []
        yy, xx = np.mgrid[0:h, 0:w]
        self.xx = xx + 0.5
        self.yy = yy + 0.5

    # ------------------------------------------------------------------ masks
    def circle(self, cx, cy, r):
        return (self.xx - cx) ** 2 + (self.yy - cy) ** 2 <= r * r

    def ellipse(self, cx, cy, rx, ry):
        return ((self.xx - cx) / rx) ** 2 + ((self.yy - cy) / ry) ** 2 <= 1.0

    def ring(self, cx, cy, r0, r1):
        d2 = (self.xx - cx) ** 2 + (self.yy - cy) ** 2
        return (d2 <= r1 * r1) & (d2 >= r0 * r0)

    def rect(self, x0, y0, x1, y1):
        return (self.xx >= x0) & (self.xx < x1) & (self.yy >= y0) & (self.yy < y1)

    def poly(self, pts):
        """Pixels whose centres are inside the polygon (even-odd rule)."""
        inside = np.zeros((self.h, self.w), bool)
        n = len(pts)
        for i in range(n):
            x0, y0 = pts[i]
            x1, y1 = pts[(i + 1) % n]
            cond = (y0 > self.yy) != (y1 > self.yy)
            with np.errstate(divide='ignore', invalid='ignore'):
                xint = (x1 - x0) * (self.yy - y0) / (y1 - y0 + 1e-12) + x0
            inside ^= cond & (self.xx < xint)
        return inside

    def line(self, x0, y0, x1, y1, width):
        """A capsule from (x0,y0) to (x1,y1)."""
        px, py = self.xx - x0, self.yy - y0
        dx, dy = x1 - x0, y1 - y0
        L2 = dx * dx + dy * dy + 1e-9
        t = np.clip((px * dx + py * dy) / L2, 0, 1)
        ex, ey = px - t * dx, py - t * dy
        return ex * ex + ey * ey <= (width / 2) ** 2

    # ------------------------------------------------------------------ surfaces
    def _flat(self):
        n = np.zeros((self.h, self.w, 3))
        n[..., 2] = 1
        return n

    def _from_height(self, hgt, strength=6.0):
        gy, gx = np.gradient(hgt)
        n = np.dstack([-gx * strength, -gy * strength, np.ones_like(hgt)])
        return n / np.linalg.norm(n, axis=2, keepdims=True)

    def dome(self, mask, cx, cy, r, flatten=1.0):
        """Spherical bulge (lenses, orbs, cabochons)."""
        d = np.sqrt((self.xx - cx) ** 2 + (self.yy - cy) ** 2) / max(r, 1e-6)
        z = np.sqrt(np.clip(1 - d * d, 0, 1)) * flatten
        nx = (self.xx - cx) / r
        ny = (self.yy - cy) / r
        n = np.dstack([nx, ny, np.maximum(z, 0.15)])
        n /= np.linalg.norm(n, axis=2, keepdims=True)
        return n, np.where(mask, z, 0)

    def bevel(self, mask, width=2.0, strength=0.9):
        """Flat top with sloped edges (plates, frames, panels)."""
        dist = _distance_inside(mask)
        hgt = np.clip(dist / width, 0, 1)
        n = self._from_height(hgt, strength * 2.2)
        return n, hgt

    def cylinder(self, mask, x0, y0, x1, y1, radius):
        """Rounded across the axis (x0,y0)->(x1,y1): handles, rods, rims."""
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy) + 1e-9
        ux, uy = dx / L, dy / L
        px, py = -uy, ux  # perpendicular
        s = ((self.xx - x0) * px + (self.yy - y0) * py) / max(radius, 1e-6)
        s = np.clip(s, -1, 1)
        z = np.sqrt(np.clip(1 - s * s, 0, 1))
        n = np.dstack([s * px, s * py, np.maximum(z, 0.2)])
        n /= np.linalg.norm(n, axis=2, keepdims=True)
        return n, np.where(mask, z, 0)

    def facets(self, outline, table_scale=0.45, tilt=0.85, center=None, split=True):
        """
        A cut gemstone: the outline polygon, an inner 'table' polygon scaled toward the centre, and one planar facet per
        edge between them (split into two triangles for extra sparkle). Returns (mask, normals, height).
        """
        if center is None:
            cx = sum(p[0] for p in outline) / len(outline)
            cy = sum(p[1] for p in outline) / len(outline)
        else:
            cx, cy = center
        table = [(cx + (x - cx) * table_scale, cy + (y - cy) * table_scale) for x, y in outline]
        mask = self.poly(outline)
        normals = self._flat()
        hgt = np.zeros((self.h, self.w))
        tmask = self.poly(table)
        hgt[tmask] = 1.0
        n = len(outline)
        for i in range(n):
            a, b = outline[i], outline[(i + 1) % n]
            ta, tb = table[i], table[(i + 1) % n]
            mx, my = (a[0] + b[0]) / 2 - cx, (a[1] + b[1]) / 2 - cy
            ml = math.hypot(mx, my) + 1e-9
            out = np.array([mx / ml, my / ml])
            parts = [[a, b, tb, ta]] if not split else [[a, ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2), tb, ta],
                                                         [((a[0] + b[0]) / 2, (a[1] + b[1]) / 2), b, tb]]
            for k, quad in enumerate(parts):
                m = self.poly(quad) & mask & ~tmask
                # Each half-facet leans slightly differently so neighbouring facets catch light differently.
                ex, ey = b[0] - a[0], b[1] - a[1]
                el = math.hypot(ex, ey) + 1e-9
                twist = (0.22 if k == 0 else -0.22) if split else 0
                vx = out[0] * tilt + ex / el * twist
                vy = out[1] * tilt + ey / el * twist
                nv = np.array([vx, vy, 1.0])
                nv /= np.linalg.norm(nv)
                normals[m] = nv
                hgt[m] = 0.55
        return mask, normals, hgt

    # ------------------------------------------------------------------ adding
    def add(self, mask, material, normals=None, height=None, depth=1.0, glow=0.0):
        if normals is None:
            normals = self._flat()
        if height is None:
            height = np.where(mask, 0.5, 0.0)
        self.layers.append(Layer(mask.copy(), material, normals, height, depth, glow, len(self.layers)))
        return self

    def gem(self, outline, material, depth=2.0, glow=0.0, table_scale=0.45, tilt=0.85, center=None):
        m, n, h = self.facets(outline, table_scale, tilt, center)
        return self.add(m, material, n, h, depth, glow)

    def orb(self, cx, cy, r, material, depth=2.0, glow=0.0, flatten=1.0):
        m = self.circle(cx, cy, r)
        n, h = self.dome(m, cx, cy, r, flatten)
        return self.add(m, material, n, h, depth, glow)

    def plate(self, mask, material, depth=1.0, bevel=2.0, glow=0.0):
        n, h = self.bevel(mask, bevel)
        return self.add(mask, material, n, h, depth, glow)

    def rod(self, x0, y0, x1, y1, width, material, depth=1.0, glow=0.0):
        m = self.line(x0, y0, x1, y1, width)
        n, h = self.cylinder(m, x0, y0, x1, y1, width / 2)
        return self.add(m, material, n, h, depth, glow)

    def sparkle(self, x, y, size=2, color='#FFFFFF'):
        """A four-point star highlight drawn on top of everything."""
        self.sparkles.append((int(x), int(y), int(size), color))
        return self

    # ------------------------------------------------------------------ compose
    def compose(self):
        """Flattens the stack: per-pixel layer index, normals, height, depth, glow."""
        top = np.full((self.h, self.w), -1, int)
        normals = self._flat()
        height = np.zeros((self.h, self.w))
        depth = np.zeros((self.h, self.w))
        glow = np.zeros((self.h, self.w))
        for i, L in enumerate(self.layers):
            m = L.mask
            top[m] = i
            normals[m] = L.normals[m]
            height[m] = L.height[m] * 0.5 + L.depth / 6.0
            depth[m] = L.depth
            glow[m] = L.glow
        return top, normals, np.clip(height, 0, 1), depth, glow

    def render(self, outline=True):
        """Returns (albedo RGBA, normal RGBA, specular RGBA, depth map, glow map) as numpy arrays."""
        top, normals, height, depth, glow = self.compose()
        h, w = self.h, self.w
        albedo = np.zeros((h, w, 4), np.uint8)
        idx_map = np.zeros((h, w), int)
        bayer = np.array([[0, 2], [3, 1]]) / 4.0 - 0.375
        for y in range(h):
            for x in range(w):
                li = top[y, x]
                if li < 0:
                    continue
                L = self.layers[li]
                mat = MATERIALS[L.material]
                ramp = RAMP_RGB[mat.ramp]
                N = normals[y, x]
                diff = max(0.0, float(N @ LIGHT))
                v = mat.ambient + (1 - mat.ambient) * diff
                # A touch of height-based falloff keeps big flat areas from looking dead.
                v *= 0.88 + 0.12 * height[y, x]
                steps = len(ramp) - 1
                f = v * (steps - 1)  # reserve the top step for specular
                if mat.dither:
                    f += bayer[y % 2, x % 2] * 0.6
                i = int(np.clip(round(f), 1, steps - 1))
                spec = max(0.0, float(N @ HALF)) ** mat.shininess
                if spec > mat.spec_cut:
                    i = steps
                elif spec > mat.spec_cut * 0.55:
                    i = min(steps, i + 1)
                idx_map[y, x] = i
                albedo[y, x, :3] = ramp[i]
                albedo[y, x, 3] = 255
        # Cast shadow: a shape drawn in front darkens the pixels just down-right of it.
        for y in range(h):
            for x in range(w):
                li = top[y, x]
                if li < 0:
                    continue
                for dx, dy in ((-1, 0), (0, -1), (-1, -1)):
                    qx, qy = x + dx, y + dy
                    if 0 <= qx < w and 0 <= qy < h and top[qy, qx] > li and depth[qy, qx] > depth[y, x]:
                        ramp = RAMP_RGB[MATERIALS[self.layers[li].material].ramp]
                        i = max(1, idx_map[y, x] - 2)
                        idx_map[y, x] = i
                        albedo[y, x, :3] = ramp[i]
                        break
        # Selective outline on the silhouette.
        occupied = top >= 0
        for y in range(h):
            for x in range(w):
                if not outline or not occupied[y, x]:
                    continue
                edges = []
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    qx, qy = x + dx, y + dy
                    if not (0 <= qx < w and 0 <= qy < h) or not occupied[qy, qx]:
                        edges.append((dx, dy))
                if not edges:
                    continue
                ramp = RAMP_RGB[MATERIALS[self.layers[top[y, x]].material].ramp]
                lit = all(dx < 0 or dy < 0 for dx, dy in edges)
                albedo[y, x, :3] = ramp[1] if lit else ramp[0]
                idx_map[y, x] = 1 if lit else 0
        # Inner contour where two different shapes meet (only on the side away from the light).
        for y in range(h):
            for x in range(w):
                li = top[y, x]
                if li < 0:
                    continue
                for dx, dy in ((1, 0), (0, 1)):
                    qx, qy = x + dx, y + dy
                    if 0 <= qx < w and 0 <= qy < h and top[qy, qx] >= 0 and top[qy, qx] != li \
                            and self.layers[top[qy, qx]].material != self.layers[li].material \
                            and depth[qy, qx] < depth[y, x]:
                        ramp = RAMP_RGB[MATERIALS[self.layers[li].material].ramp]
                        i = max(0, idx_map[y, x] - 3)
                        albedo[y, x, :3] = ramp[i]
                        idx_map[y, x] = i
                        break
        # Sparkles.
        for sx, sy, size, color in self.sparkles:
            rgb = hexrgb(color)
            for k in range(-size, size + 1):
                for px, py in ((sx + k, sy), (sx, sy + k)):
                    if 0 <= px < w and 0 <= py < h and occupied[py, px]:
                        if abs(k) == size and size > 1:
                            continue
                        albedo[py, px, :3] = rgb if abs(k) < max(1, size - 1) or size == 1 else RAMP_RGB['ice'][6]
            idx_map[sy % h, sx % w] = 7

        normal_map = self._normal_map(top, normals, height)
        spec_map = self._spec_map(top, idx_map, glow)
        return albedo, normal_map, spec_map, depth, glow

    def _normal_map(self, top, normals, height):
        h, w = self.h, self.w
        out = np.zeros((h, w, 4), np.uint8)
        occ = top >= 0
        ny = normals[..., 1] if NORMAL_Y_DOWN else -normals[..., 1]
        out[..., 0] = np.clip((normals[..., 0] * 0.5 + 0.5) * 255, 0, 255)
        out[..., 1] = np.clip((ny * 0.5 + 0.5) * 255, 0, 255)
        # Ambient occlusion: darker in creases next to taller neighbours.
        ao = np.ones((h, w))
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            shifted = np.roll(np.roll(height, dy, 0), dx, 1)
            ao -= np.clip(shifted - height, 0, 1) * 0.35
        out[..., 2] = np.clip(ao, 0.3, 1) * 255
        out[..., 3] = np.clip(height * 255, 1, 255)
        out[~occ] = (128, 128, 255, 0)
        return out

    def _spec_map(self, top, idx_map, glow):
        h, w = self.h, self.w
        out = np.zeros((h, w, 4), np.uint8)
        for y in range(h):
            for x in range(w):
                li = top[y, x]
                if li < 0:
                    out[y, x] = (0, 0, 0, 0)
                    continue
                mat = MATERIALS[self.layers[li].material]
                smooth = mat.smooth if idx_map[y, x] > 0 else max(0, mat.smooth - 60)
                g = glow[y, x]
                emit = 255 if g <= 0 else int(np.clip(40 + 214 * g, 1, 254))
                out[y, x] = (smooth, mat.f0, mat.sss, emit)
        return out


def _distance_inside(mask: np.ndarray) -> np.ndarray:
    """Chebyshev-ish distance from each inside pixel to the nearest outside pixel (cheap iterative erosion)."""
    dist = np.zeros(mask.shape)
    cur = mask.copy()
    d = 0
    while cur.any() and d < 32:
        d += 1
        dist[cur] = d
        er = cur.copy()
        er[1:, :] &= cur[:-1, :]
        er[:-1, :] &= cur[1:, :]
        er[:, 1:] &= cur[:, :-1]
        er[:, :-1] &= cur[:, 1:]
        er[0, :] = er[-1, :] = False
        er[:, 0] = er[:, -1] = False
        cur = er
    return dist


def to_image(arr: np.ndarray) -> Image.Image:
    return Image.fromarray(arr, 'RGBA')


def stack_frames(frames: list[np.ndarray]) -> np.ndarray:
    return np.concatenate(frames, axis=0)


def nearest_ramp(rgb, ramp_name):
    """Snap a colour to the nearest step of a ramp (keeps animated glow inside the palette)."""
    ramp = RAMP_RGB[ramp_name]
    best = min(ramp, key=lambda c: sum((a - b) ** 2 for a, b in zip(c, rgb)))
    return best
