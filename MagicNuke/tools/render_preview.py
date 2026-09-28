"""
Tiny software renderer for Minecraft JSON model elements (orthographic, z-buffered
point splatting). Used to preview the generated models and to render pack.png.
"""
import math

import numpy as np

NORMALS = {
    "north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0),
    "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0),
}


def corners(face, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "north": ((x1, y1, z0), (x0, y1, z0), (x1, y0, z0)),
        "south": ((x0, y1, z1), (x1, y1, z1), (x0, y0, z1)),
        "east": ((x1, y1, z1), (x1, y1, z0), (x1, y0, z1)),
        "west": ((x0, y1, z0), (x0, y1, z1), (x0, y0, z0)),
        "up": ((x0, y1, z0), (x1, y1, z0), (x0, y1, z1)),
        "down": ((x0, y0, z1), (x1, y0, z1), (x0, y0, z0)),
    }[face]


def axis_rot(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def render_model(elements, textures, display, size=256, bg=(0, 0, 0, 0)):
    rot = display.get("rotation", [0, 0, 0])
    # rotationXYZ: R = Rx * Ry * Rz
    R = axis_rot("x", rot[0]) @ axis_rot("y", rot[1]) @ axis_rot("z", rot[2])
    S = np.array(display.get("scale", [1, 1, 1]), float)
    T = np.array(display.get("translation", [0, 0, 0]), float)
    ppu = size / 16.0
    step = 0.45 / (ppu * max(S))

    img = np.zeros((size, size, 4), np.uint8)
    img[:] = bg
    zbuf = np.full((size, size), -1e9)
    light = np.array([0.35, 0.85, 0.55])
    light /= np.linalg.norm(light)

    for el in elements:
        f = np.array(el["from"], float)
        t = np.array(el["to"], float)
        er = el.get("rotation")
        M = np.eye(3)
        origin = np.zeros(3)
        if er:
            M = axis_rot(er["axis"], er["angle"])
            origin = np.array(er["origin"], float)
        shade = el.get("shade", True)
        for face, spec in el["faces"].items():
            n = R @ (M @ np.array(NORMALS[face], float))
            if n[2] <= 1e-6:
                continue
            tex = textures[spec["texture"]]
            th, tw = tex.shape[:2]
            lt, rt, lb = (np.array(c, float) for c in corners(face, f, t))
            du = rt - lt
            dv = lb - lt
            nu = max(2, int(np.linalg.norm(du) / step) + 1)
            nv = max(2, int(np.linalg.norm(dv) / step) + 1)
            s = np.linspace(0, 1, nu)
            q = np.linspace(0, 1, nv)
            ss, qq = np.meshgrid(s, q)
            pts = lt[None, None, :] + ss[..., None] * du + qq[..., None] * dv
            u1, v1, u2, v2 = spec["uv"]
            uu = u1 + ss * (u2 - u1)
            vv = v1 + qq * (v2 - v1)
            tx = np.clip((uu / 16 * tw).astype(int), 0, tw - 1)
            ty = np.clip((vv / 16 * th).astype(int), 0, th - 1)
            col = tex[ty, tx].astype(float)
            pts = (pts - origin) @ M.T + origin
            p = (pts - 8.0) * S
            p = p @ R.T + T
            px = (p[..., 0] * ppu + size / 2).astype(int)
            py = (-p[..., 1] * ppu + size / 2).astype(int)
            pz = p[..., 2]
            b = 1.0 if not shade else 0.5 + 0.5 * max(0.0, float(n @ light)) + 0.12 * abs(n[1])
            b = min(b, 1.0)
            ok = (px >= 0) & (px < size) & (py >= 0) & (py < size) & (col[..., 3] > 0)
            px, py, pz, col = px[ok], py[ok], pz[ok], col[ok]
            order = np.argsort(pz)
            px, py, pz, col = px[order], py[order], pz[order], col[order]
            closer = pz > zbuf[py, px]
            px, py, pz, col = px[closer], py[closer], pz[closer], col[closer]
            zbuf[py, px] = pz
            c = col.copy()
            c[:, :3] *= b
            img[py, px] = np.clip(c, 0, 255).astype(np.uint8)
    return img
