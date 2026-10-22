#!/usr/bin/env python3
"""
Arcane Forge procedural asset generator.

Generates every client and data asset used by the mod:
  * GeckoLib .geo.json models (multi-bone, angled cubes, per-face UVs)
  * GeckoLib .animation.json files (smooth sine-sampled keyframes)
  * Vanilla multi-element .json block/item models built from the same geometry
  * Shaded 64x64 material atlases (+ emissive _glowmask layers) and 32x32 item sprites
  * Particle textures/definitions, blockstates, sounds.json, en_us.json
  * Recipes, loot tables and block tags

Run from the repository root:  python3 generate_assets.py
"""

import json
import math
import os
import random
import shutil
from collections import OrderedDict

from PIL import Image, ImageDraw

MOD_ID = "arcaneforge"
ROOT = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", MOD_ID)
DATA = os.path.join(RES, "data", MOD_ID)
DATA_MC = os.path.join(RES, "data", "minecraft")

WRITTEN_JSON = []


# ----------------------------------------------------------------------------
# IO helpers
# ----------------------------------------------------------------------------

def ensure_dir(path):
    os.makedirs(path, exist_ok=True)


def write_json(path, obj):
    ensure_dir(os.path.dirname(path))
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    WRITTEN_JSON.append(path)


def save_png(img, path):
    ensure_dir(os.path.dirname(path))
    img.save(path, "PNG", optimize=True)


def clamp(v, lo=0, hi=255):
    return max(lo, min(hi, int(round(v))))


def mix(c1, c2, t):
    n = min(len(c1), len(c2))
    return tuple(clamp(c1[i] + (c2[i] - c1[i]) * t) for i in range(n))


def shade(c, f):
    return tuple(clamp(ch * f) for ch in c[:3]) + tuple(c[3:])


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


# ----------------------------------------------------------------------------
# 16x16 material swatches
# ----------------------------------------------------------------------------

SWATCH = 16


def bevel(x, y, size=SWATCH):
    """Lighting factor that brightens top/left edges and darkens bottom/right."""
    f = 1.0
    if x == 0 or y == 0:
        f += 0.18
    if x == size - 1 or y == size - 1:
        f -= 0.22
    if x == 1 or y == 1:
        f += 0.06
    return f


def paint_stone_bricks(rng, base=(74, 68, 72)):
    px = {}
    for y in range(SWATCH):
        row = y // 4
        offset = 4 if row % 2 else 0
        for x in range(SWATCH):
            mortar = (y % 4 == 3) or ((x + offset) % 8 == 7)
            n = rng.uniform(0.82, 1.12)
            if mortar:
                c = shade(base, 0.55 * n)
            else:
                local_y = y % 4
                f = 1.12 if local_y == 0 else (0.9 if local_y == 2 else 1.0)
                c = shade(base, f * n)
            px[(x, y)] = rgba(c)
    return px


def paint_brushed_metal(rng, base=(176, 182, 190), streak=0.08):
    px = {}
    for y in range(SWATCH):
        line = rng.uniform(1 - streak, 1 + streak)
        for x in range(SWATCH):
            grad = 1.15 - 0.35 * ((x + y) / (2 * SWATCH))
            n = rng.uniform(0.96, 1.04) * line
            c = shade(base, grad * n * bevel(x, y))
            px[(x, y)] = rgba(c)
    # specular glints
    for _ in range(4):
        gx, gy = rng.randrange(2, 14), rng.randrange(2, 14)
        px[(gx, gy)] = rgba(mix(px[(gx, gy)], (255, 255, 255), 0.6))
    return px


def paint_planks(rng, base=(112, 78, 48)):
    px = {}
    for y in range(SWATCH):
        plank = y // 4
        pf = [1.0, 0.9, 1.06, 0.94][plank]
        for x in range(SWATCH):
            grain = 0.08 * math.sin((x * 0.9) + plank * 2.1 + math.sin(y * 1.7))
            n = rng.uniform(0.94, 1.05)
            f = pf * (1 + grain) * n
            if y % 4 == 3:
                f *= 0.62
            if (x == (plank * 5 + 3) % 16) and (y % 4 == 1):
                f *= 0.7  # nail hole
            px[(x, y)] = rgba(shade(base, f))
    return px


def paint_leather(rng, base=(96, 56, 34)):
    px = {}
    for y in range(SWATCH):
        for x in range(SWATCH):
            wrap = 0.82 if (x + y) % 5 == 0 else 1.0
            n = rng.uniform(0.9, 1.08)
            px[(x, y)] = rgba(shade(base, wrap * n * bevel(x, y)))
    return px


def paint_magma(rng, hot=(255, 170, 40), mid=(230, 70, 10), dark=(70, 18, 8)):
    px = {}
    seeds = [(rng.uniform(0, 16), rng.uniform(0, 16)) for _ in range(6)]
    for y in range(SWATCH):
        for x in range(SWATCH):
            d = min(math.hypot(x - sx, y - sy) for sx, sy in seeds)
            crack = abs(math.sin(x * 0.8 + math.cos(y * 0.6) * 2.0))
            t = max(0.0, 1.0 - d / 6.0) * 0.6 + (1 - crack) * 0.55
            t = min(1.0, t + rng.uniform(-0.08, 0.08))
            if t > 0.7:
                c = mix(mid, hot, (t - 0.7) / 0.3)
            elif t > 0.35:
                c = mix(dark, mid, (t - 0.35) / 0.35)
            else:
                c = shade(dark, 0.7 + t)
            px[(x, y)] = rgba(c)
    return px


def paint_rune_glow(rng, core=(120, 245, 255), edge=(60, 70, 210), bg=(26, 18, 48)):
    px = {}
    glyph = set()
    # procedural glyph strokes
    for _ in range(5):
        x0, y0 = rng.randrange(2, 14), rng.randrange(2, 14)
        dx, dy = rng.choice([(1, 0), (0, 1), (1, 1), (1, -1)])
        for i in range(rng.randrange(3, 8)):
            gx, gy = x0 + dx * i, y0 + dy * i
            if 1 <= gx < 15 and 1 <= gy < 15:
                glyph.add((gx, gy))
    for y in range(SWATCH):
        for x in range(SWATCH):
            if (x, y) in glyph:
                c = core
            elif any((x + ox, y + oy) in glyph for ox in (-1, 0, 1) for oy in (-1, 0, 1)):
                c = edge
            else:
                c = shade(bg, rng.uniform(0.85, 1.2))
            px[(x, y)] = rgba(c)
    return px


def paint_solid_glow(rng, core, edge):
    px = {}
    for y in range(SWATCH):
        for x in range(SWATCH):
            d = math.hypot(x - 7.5, y - 7.5) / 10.6
            c = mix(core, edge, min(1.0, d + rng.uniform(-0.05, 0.05)))
            if (x * 7 + y * 3) % 11 == 0:
                c = mix(c, (255, 255, 255), 0.45)
            px[(x, y)] = rgba(c)
    return px


def paint_obsidian(rng):
    px = {}
    for y in range(SWATCH):
        for x in range(SWATCH):
            n = rng.uniform(0.75, 1.25)
            c = shade((32, 20, 52), n * bevel(x, y))
            if rng.random() < 0.05:
                c = (92, 54, 140)
            px[(x, y)] = rgba(c)
    return px


def paint_water(rng, base=(40, 92, 200)):
    px = {}
    for y in range(SWATCH):
        for x in range(SWATCH):
            wave = 0.12 * math.sin(x * 0.7 + y * 0.4) + 0.08 * math.cos(y * 1.1)
            px[(x, y)] = rgba(shade(base, 1 + wave + rng.uniform(-0.04, 0.04)), 210)
    return px


def paint_ice(rng):
    px = {}
    for y in range(SWATCH):
        for x in range(SWATCH):
            crack = abs((x - y) % 7) == 0 or abs((x + y * 2) % 9) == 0
            c = (220, 245, 255) if crack else shade((140, 196, 240), rng.uniform(0.9, 1.1) * bevel(x, y))
            px[(x, y)] = rgba(c)
    return px


PALETTE_DEFAULT = OrderedDict([
    ("stone", lambda r: paint_stone_bricks(r)),
    ("iron", lambda r: paint_brushed_metal(r)),
    ("dark_iron", lambda r: paint_brushed_metal(r, base=(70, 72, 82), streak=0.12)),
    ("copper", lambda r: paint_brushed_metal(r, base=(204, 112, 70))),
    ("gold", lambda r: paint_brushed_metal(r, base=(240, 196, 72))),
    ("wood", lambda r: paint_planks(r)),
    ("leather", lambda r: paint_leather(r)),
    ("magma", lambda r: paint_magma(r)),
    ("rune", lambda r: paint_rune_glow(r)),
    ("obsidian", lambda r: paint_obsidian(r)),
    ("water", lambda r: paint_water(r)),
    ("ice", lambda r: paint_ice(r)),
    ("blood", lambda r: paint_solid_glow(r, (255, 70, 90), (120, 0, 20))),
    ("fire", lambda r: paint_solid_glow(r, (255, 240, 140), (240, 90, 10))),
    ("storm", lambda r: paint_solid_glow(r, (230, 250, 255), (40, 120, 255))),
    ("shadow", lambda r: paint_solid_glow(r, (220, 140, 255), (60, 10, 110))),
])
MATERIALS = list(PALETTE_DEFAULT.keys())
EMISSIVE = {"magma", "rune", "blood", "fire", "storm", "shadow"}


def material_cell(mat):
    idx = MATERIALS.index(mat)
    return idx % 4, idx // 4


def build_atlas(name, seed, overrides=None, extra_emissive=()):
    """Creates a 64x64 atlas of 16 material swatches and its emissive glowmask."""
    rng = random.Random(seed)
    overrides = overrides or {}
    atlas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    glow = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for mat, painter in PALETTE_DEFAULT.items():
        px = overrides.get(mat, painter)(rng)
        cx, cy = material_cell(mat)
        emissive = mat in EMISSIVE or mat in extra_emissive
        for (x, y), c in px.items():
            atlas.putpixel((cx * 16 + x, cy * 16 + y), c)
            if emissive:
                glow.putpixel((cx * 16 + x, cy * 16 + y), (c[0], c[1], c[2], 255))
    return atlas, glow


# ----------------------------------------------------------------------------
# Geometry builder (GeckoLib geo + vanilla json model from one definition)
# ----------------------------------------------------------------------------

JSON_ANGLES = [-45.0, -22.5, 0.0, 22.5, 45.0]


class Model:
    def __init__(self, name, kind, texture_ref, bounds=(3, 3)):
        self.name = name
        self.kind = kind  # "block" or "item"
        self.texture_ref = texture_ref
        self.bounds = bounds
        self.bones = OrderedDict()

    def bone(self, name, parent=None, pivot=(0, 0, 0)):
        self.bones[name] = {"name": name, "parent": parent, "pivot": list(pivot), "cubes": []}
        return name

    def cube(self, bone, origin, size, mat, rotation=None, pivot=None, inflate=0.0, in_json=True):
        self.bones[bone]["cubes"].append({
            "origin": [float(v) for v in origin],
            "size": [float(v) for v in size],
            "mat": mat,
            "rotation": rotation,
            "pivot": pivot,
            "inflate": inflate,
            "json": in_json,
        })

    # --- GeckoLib ---------------------------------------------------------

    @staticmethod
    def _face_uv(mat, w, h):
        cx, cy = material_cell(mat)
        uw = max(1.0, min(16.0, abs(w)))
        uh = max(1.0, min(16.0, abs(h)))
        return {"uv": [cx * 16, cy * 16], "uv_size": [round(uw, 3), round(uh, 3)]}

    def to_geo(self):
        bones = []
        for b in self.bones.values():
            entry = OrderedDict()
            entry["name"] = b["name"]
            if b["parent"]:
                entry["parent"] = b["parent"]
            entry["pivot"] = b["pivot"]
            cubes = []
            for c in b["cubes"]:
                sx, sy, sz = c["size"]
                m = c["mat"]
                cube = OrderedDict()
                cube["origin"] = c["origin"]
                cube["size"] = c["size"]
                if c["inflate"]:
                    cube["inflate"] = c["inflate"]
                if c["rotation"]:
                    cube["pivot"] = c["pivot"] or [c["origin"][i] + c["size"][i] / 2 for i in range(3)]
                    cube["rotation"] = c["rotation"]
                cube["uv"] = OrderedDict([
                    ("north", self._face_uv(m, sx, sy)),
                    ("east", self._face_uv(m, sz, sy)),
                    ("south", self._face_uv(m, sx, sy)),
                    ("west", self._face_uv(m, sz, sy)),
                    ("up", self._face_uv(m, sx, sz)),
                    ("down", self._face_uv(m, sx, sz)),
                ])
                cubes.append(cube)
            if cubes:
                entry["cubes"] = cubes
            bones.append(entry)
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": "geometry.%s.%s" % (MOD_ID, self.name),
                    "texture_width": 64,
                    "texture_height": 64,
                    "visible_bounds_width": self.bounds[0],
                    "visible_bounds_height": self.bounds[1],
                    "visible_bounds_offset": [0, self.bounds[1] / 2.0 - 0.5, 0],
                },
                "bones": bones,
            }],
        }

    # --- Vanilla JSON model -----------------------------------------------

    @staticmethod
    def _json_uv(mat, w, h):
        cx, cy = material_cell(mat)
        uw = max(0.25, min(4.0, abs(w) / 4.0))
        uh = max(0.25, min(4.0, abs(h) / 4.0))
        return [cx * 4, cy * 4, round(cx * 4 + uw, 3), round(cy * 4 + uh, 3)]

    def _bone_offset(self, bone_name):
        return [0.0, 0.0, 0.0]

    def to_json_model(self, display=None):
        elements = []
        for b in self.bones.values():
            for c in b["cubes"]:
                if not c["json"]:
                    continue
                ox, oy, oz = c["origin"]
                sx, sy, sz = c["size"]
                inf = c["inflate"]
                # Bedrock space is mirrored on X relative to Java block space.
                fx = 8 - (ox + sx) - inf
                tx = 8 - ox + inf
                fy = oy - inf
                ty = oy + sy + inf
                fz = oz + 8 - inf
                tz = oz + sz + 8 + inf
                frm = [round(max(-16, min(32, v)), 3) for v in (fx, fy, fz)]
                to = [round(max(-16, min(32, v)), 3) for v in (tx, ty, tz)]
                el = OrderedDict()
                el["from"] = frm
                el["to"] = to
                if c["rotation"]:
                    rot = c["rotation"]
                    axis_idx = max(range(3), key=lambda i: abs(rot[i]))
                    angle = rot[axis_idx]
                    if axis_idx in (1, 2):
                        angle = -angle
                    snapped = min(JSON_ANGLES, key=lambda a: abs(a - angle))
                    if snapped != 0.0:
                        pv = c["pivot"] or [ox + sx / 2, oy + sy / 2, oz + sz / 2]
                        el["rotation"] = {
                            "angle": snapped,
                            "axis": "xyz"[axis_idx],
                            "origin": [round(8 - pv[0], 3), round(pv[1], 3), round(pv[2] + 8, 3)],
                        }
                m = c["mat"]
                el["faces"] = OrderedDict([
                    ("north", {"uv": self._json_uv(m, sx, sy), "texture": "#atlas"}),
                    ("east", {"uv": self._json_uv(m, sz, sy), "texture": "#atlas"}),
                    ("south", {"uv": self._json_uv(m, sx, sy), "texture": "#atlas"}),
                    ("west", {"uv": self._json_uv(m, sz, sy), "texture": "#atlas"}),
                    ("up", {"uv": self._json_uv(m, sx, sz), "texture": "#atlas"}),
                    ("down", {"uv": self._json_uv(m, sx, sz), "texture": "#atlas"}),
                ])
                elements.append(el)
        model = OrderedDict()
        model["credit"] = "Arcane Forge procedural model generator"
        model["texture_size"] = [64, 64]
        model["textures"] = {"atlas": self.texture_ref, "particle": self.texture_ref + "_particle"}
        model["elements"] = elements
        if display:
            model["display"] = display
        return model


def ring_points(radius, count, start=0.0):
    for i in range(count):
        a = start + 2 * math.pi * i / count
        yield i, a, math.cos(a) * radius, math.sin(a) * radius


# ---------------------------- Block models ----------------------------------

def model_smeltery():
    m = Model("smeltery", "block", "%s:block/smeltery" % MOD_ID)
    m.bone("root")
    m.cube("root", [-8, 0, -8], [16, 3, 16], "stone")
    m.cube("root", [-7, 3, -7], [14, 9, 2], "stone")
    m.cube("root", [-7, 3, 5], [14, 9, 2], "stone")
    m.cube("root", [-7, 3, -5], [2, 9, 10], "stone")
    m.cube("root", [5, 3, -5], [2, 9, 10], "stone")
    m.cube("root", [-5, 3, -5], [10, 1, 10], "magma")
    for x, z in [(-8, -8), (6, -8), (-8, 6), (6, 6)]:
        m.cube("root", [x, 0, z], [2, 13, 2], "dark_iron", inflate=0.1)
        m.cube("root", [x - 0.25, 13, z - 0.25], [2.5, 1, 2.5], "gold")
    # iron rim bands
    m.cube("root", [-7.5, 12, -7.5], [15, 1.5, 1.5], "iron")
    m.cube("root", [-7.5, 12, 6], [15, 1.5, 1.5], "iron")
    m.cube("root", [-7.5, 12, -6], [1.5, 1.5, 12], "iron")
    m.cube("root", [6, 12, -6], [1.5, 1.5, 12], "iron")
    m.cube("root", [-7.5, 6, -7.6], [15, 1, 0.6], "dark_iron")
    # glowing firebox with grate bars
    m.cube("root", [-3.5, 3.5, -7.6], [7, 4.5, 0.4], "magma")
    for gx in (-2.6, -0.4, 1.8):
        m.cube("root", [gx, 3.3, -8.0], [0.8, 4.9, 0.6], "dark_iron")
    m.cube("root", [-4, 8, -8.0], [8, 0.8, 1], "iron")
    # angled spout
    m.bone("spout", "root", [0, 11, -7])
    m.cube("spout", [-1, 10.5, -10.5], [2, 1, 4], "copper", rotation=[-20, 0, 0], pivot=[0, 11, -7])
    m.cube("spout", [-1.5, 11.0, -10.5], [0.5, 1, 4], "copper", rotation=[-20, 0, 0], pivot=[0, 11, -7])
    m.cube("spout", [1.0, 11.0, -10.5], [0.5, 1, 4], "copper", rotation=[-20, 0, 0], pivot=[0, 11, -7])
    # chimney
    m.cube("root", [3.5, 13, 3.5], [3, 4, 3], "dark_iron")
    m.cube("root", [3.2, 17, 3.2], [3.6, 0.8, 3.6], "iron")
    # bellows (left)
    m.bone("bellows", "root", [-8, 4, 0])
    m.cube("bellows", [-12.5, 3, -3], [4.5, 0.8, 6], "wood")
    m.cube("bellows", [-12.0, 3.8, -2.5], [3.8, 2.4, 5], "leather")
    m.cube("bellows", [-9.0, 4.3, -0.75], [1.5, 1.5, 1.5], "copper")
    m.bone("bellows_top", "bellows", [-8.5, 6.2, 0])
    m.cube("bellows_top", [-12.5, 6.2, -3], [4.5, 0.8, 6], "wood")
    m.cube("bellows_top", [-14.5, 6.4, -0.5], [2.2, 0.6, 1], "wood")
    # lever (right)
    m.bone("lever", "root", [8, 6, 0])
    m.cube("root", [7, 4, -1.5], [1.2, 4, 3], "iron")
    m.cube("lever", [8, 6, -0.5], [1, 7, 1], "wood", rotation=[0, 0, -15], pivot=[8.5, 6, 0])
    m.cube("lever", [8.6, 12.2, -1], [2, 2, 2], "copper", rotation=[0, 0, -15], pivot=[8.5, 6, 0])
    # floating embers
    m.bone("embers", "root", [0, 14, 0])
    for i, a, x, z in ring_points(3.0, 4, 0.4):
        m.cube("embers", [x - 0.4, 14 + (i % 2), z - 0.4], [0.8, 0.8, 0.8], "fire", in_json=False)
    return m


def model_mold_table():
    m = Model("mold_table", "block", "%s:block/mold_table" % MOD_ID)
    m.bone("root")
    for x, z in [(-7, -7), (5, -7), (-7, 5), (5, 5)]:
        m.cube("root", [x, 0, z], [2, 8, 2], "wood")
    m.cube("root", [-6, 3, -6], [12, 1, 12], "wood")
    m.cube("root", [-8, 8, -8], [16, 3, 16], "stone")
    m.cube("root", [-7, 11, -7], [14, 1, 14], "dark_iron")
    m.cube("root", [-8, 11, -8], [16, 1.5, 1], "iron")
    m.cube("root", [-8, 11, 7], [16, 1.5, 1], "iron")
    m.cube("root", [-8, 11, -7], [1, 1.5, 14], "iron")
    m.cube("root", [7, 11, -7], [1, 1.5, 14], "iron")
    m.cube("root", [-6.5, 11.6, -1], [13, 0.5, 2], "obsidian")
    m.cube("root", [-7.5, 9, -8.4], [3, 1, 0.5], "rune")
    m.cube("root", [4.5, 9, -8.4], [3, 1, 0.5], "rune")
    # side clamps (angled)
    m.cube("root", [-9, 9, -2], [1.5, 3, 4], "copper", rotation=[0, 0, 15], pivot=[-8, 10, 0])
    m.cube("root", [7.5, 9, -2], [1.5, 3, 4], "copper", rotation=[0, 0, -15], pivot=[8, 10, 0])
    m.bone("vents", "root", [0, 12, 0])
    m.cube("vents", [-6, 12.2, 5.5], [1, 1, 1], "dark_iron")
    m.cube("vents", [5, 12.2, 5.5], [1, 1, 1], "dark_iron")
    m.bone("steam", "root", [0, 13, 0])
    for i, a, x, z in ring_points(3.5, 3, 1.0):
        m.cube("steam", [x - 0.5, 13, z - 0.5], [1, 1, 1], "water", in_json=False)
    return m


def model_forge_anvil():
    m = Model("forge_anvil", "block", "%s:block/forge_anvil" % MOD_ID)
    m.bone("root")
    m.cube("root", [-7, 0, -7], [14, 2, 14], "stone")
    m.cube("root", [-6, 2, -5], [12, 2, 10], "dark_iron")
    m.cube("root", [-3, 4, -3.5], [6, 4, 7], "dark_iron")
    m.cube("root", [-4.5, 7, -6.5], [9, 4, 13], "dark_iron")
    m.cube("root", [-4.5, 10.8, -6.5], [9, 0.6, 13], "iron")
    # horn (tapered, angled cubes)
    m.cube("root", [-2.5, 8, -10], [5, 3, 3.5], "dark_iron", rotation=[8, 0, 0], pivot=[0, 9.5, -6.5])
    m.cube("root", [-1.5, 8.6, -12.5], [3, 2, 3], "dark_iron", rotation=[14, 0, 0], pivot=[0, 9.5, -9.5])
    m.cube("root", [-0.75, 9.2, -14.3], [1.5, 1.2, 2.2], "iron", rotation=[22, 0, 0], pivot=[0, 9.8, -12.5])
    # heel
    m.cube("root", [-3.5, 8.5, 6.5], [7, 2.5, 3], "dark_iron")
    m.cube("root", [-1, 10.9, 4], [2, 0.3, 2], "obsidian")
    # gold reinforcing straps + glowing forge marks
    m.cube("root", [-4.7, 8, -2], [9.4, 1, 0.6], "gold")
    m.cube("root", [-4.7, 8, 1.4], [9.4, 1, 0.6], "gold")
    m.cube("root", [-4.8, 4.5, -1], [0.4, 2, 2], "rune")
    m.cube("root", [4.4, 4.5, -1], [0.4, 2, 2], "rune")
    # hammer rack on the side
    m.cube("root", [6, 2, 3], [1, 7, 1], "wood", rotation=[0, 0, -10], pivot=[6.5, 2, 3.5])
    m.cube("root", [5, 8, 2], [3, 2, 3], "iron", rotation=[0, 0, -10], pivot=[6.5, 2, 3.5])
    m.bone("rune_ring", "root", [0, 15, 0])
    for i, a, x, z in ring_points(7.5, 8):
        m.cube("rune_ring", [x - 0.5, 15, z - 0.5], [1, 1.5, 0.4], "rune",
               rotation=[0, -math.degrees(a), 0], pivot=[x, 15.75, z], in_json=False)
    return m


def model_quenching_barrel():
    m = Model("quenching_barrel", "block", "%s:block/quenching_barrel" % MOD_ID)
    m.bone("root")
    m.cube("root", [-6, 0, -6], [12, 1, 12], "wood")
    # staves (slightly bulged with angled cubes)
    m.cube("root", [-6.5, 1, -6.5], [13, 12, 1.5], "wood")
    m.cube("root", [-6.5, 1, 5], [13, 12, 1.5], "wood")
    m.cube("root", [-6.5, 1, -5], [1.5, 12, 10], "wood")
    m.cube("root", [5, 1, -5], [1.5, 12, 10], "wood")
    for y in (2.5, 10):
        m.cube("root", [-7, y, -7], [14, 1, 0.6], "dark_iron")
        m.cube("root", [-7, y, 6.4], [14, 1, 0.6], "dark_iron")
        m.cube("root", [-7, y, -6.4], [0.6, 1, 12.8], "dark_iron")
        m.cube("root", [6.4, y, -6.4], [0.6, 1, 12.8], "dark_iron")
    m.cube("root", [-5, 1, -5], [10, 1, 10], "obsidian")
    # hanging tongs
    m.cube("root", [6.8, 6, -2], [0.6, 8, 0.6], "iron", rotation=[0, 0, 12], pivot=[7, 13, -1.7])
    m.cube("root", [6.8, 6, -0.8], [0.6, 8, 0.6], "iron", rotation=[0, 0, 18], pivot=[7, 13, -0.5])
    m.cube("root", [6.6, 13, -2.2], [1, 1, 2], "copper")
    m.bone("bubbles", "root", [0, 12, 0])
    for i, a, x, z in ring_points(2.5, 5, 0.3):
        m.cube("bubbles", [x - 0.4, 11, z - 0.4], [0.8, 0.8, 0.8], "water", in_json=False)
    return m


def model_rune_altar():
    m = Model("rune_altar", "block", "%s:block/rune_altar" % MOD_ID, bounds=(3, 4))
    m.bone("root")
    m.cube("root", [-8, 0, -8], [16, 2, 16], "obsidian")
    m.cube("root", [-6.5, 2, -6.5], [13, 2, 13], "stone")
    m.cube("root", [-3.5, 4, -3.5], [7, 6, 7], "obsidian")
    m.cube("root", [-3.6, 5, -3.7], [7.2, 4, 0.3], "rune")
    m.cube("root", [-3.6, 5, 3.4], [7.2, 4, 0.3], "rune")
    m.cube("root", [-5.5, 10, -5.5], [11, 1.5, 11], "gold")
    m.cube("root", [-5, 11.5, -5], [10, 0.5, 10], "obsidian")
    for i, a, x, z in ring_points(6.0, 4, math.pi / 4):
        rx = 15 if z > 0 else -15
        rz = -15 if x > 0 else 15
        m.cube("root", [x - 0.75, 2, z - 0.75], [1.5, 7, 1.5], "rune",
               rotation=[rx, 0, rz], pivot=[x, 2, z])
        m.cube("root", [x - 1, 1.5, z - 1], [2, 1, 2], "gold")
    m.bone("floating_runes", "root", [0, 18, 0])
    for i, a, x, z in ring_points(4.5, 4):
        m.cube("floating_runes", [x - 0.6, 17, z - 0.6], [1.2, 2, 0.5], "rune",
               rotation=[0, -math.degrees(a), 0], pivot=[x, 18, z], in_json=False)
    m.bone("core", "root", [0, 15, 0])
    m.cube("core", [-1, 14, -1], [2, 2, 2], "shadow", rotation=[45, 0, 45], pivot=[0, 15, 0], in_json=False)
    return m


# ---------------------------- Weapon models ---------------------------------

def model_greatsword():
    m = Model("solaris_greatsword", "item", "")
    m.bone("root")
    m.bone("blade", "root", [0, 8, 0])
    m.cube("blade", [-2, 8, -0.5], [4, 21, 1], "iron")
    m.cube("blade", [-2.4, 8, -0.3], [0.4, 21, 0.6], "gold")
    m.cube("blade", [2.0, 8, -0.3], [0.4, 21, 0.6], "gold")
    m.cube("blade", [-0.5, 9.5, -0.6], [1, 17, 1.2], "fire")
    m.cube("blade", [-1.45, 27.5, -0.5], [2.9, 2.9, 1], "iron", rotation=[0, 0, 45], pivot=[0, 29, 0])
    m.bone("guard", "root", [0, 7, 0])
    m.cube("guard", [-6, 6.3, -1], [12, 1.7, 2], "gold")
    m.cube("guard", [-8, 6.6, -0.75], [2.5, 1.2, 1.5], "gold", rotation=[0, 0, 25], pivot=[-6, 7, 0])
    m.cube("guard", [5.5, 6.6, -0.75], [2.5, 1.2, 1.5], "gold", rotation=[0, 0, -25], pivot=[6, 7, 0])
    m.cube("guard", [-2, 5.5, -1.2], [4, 1, 2.4], "dark_iron")
    m.bone("gem", "guard", [0, 7.2, 0])
    m.cube("gem", [-1, 6.2, -1.3], [2, 2, 2.6], "fire", rotation=[0, 0, 45], pivot=[0, 7.2, 0])
    m.bone("grip", "root", [0, 2, 0])
    m.cube("grip", [-0.75, -1, -0.75], [1.5, 6.5, 1.5], "leather")
    m.cube("grip", [-1.25, -2.8, -1.25], [2.5, 2, 2.5], "gold")
    m.cube("grip", [-0.6, -3.6, -0.6], [1.2, 1, 1.2], "fire")
    m.bone("embers", "root", [0, 18, 0])
    for i in range(5):
        m.cube("embers", [2.8 if i % 2 else -3.6, 10 + i * 4, -0.4], [0.8, 0.8, 0.8], "fire")
    return m


def model_katana():
    m = Model("voidstep_katana", "item", "")
    m.bone("root")
    m.bone("blade", "root", [0, 8, 0])
    angle = 0.0
    x, y = 0.0, 8.0
    for i in range(6):
        seg = 4.0
        m.cube("blade", [x - 0.6, y, -0.25], [1.4, seg + 0.2, 0.5], "iron",
               rotation=[0, 0, -angle], pivot=[x, y, 0])
        m.cube("blade", [x + 0.55, y, -0.15], [0.3, seg + 0.2, 0.3], "shadow",
               rotation=[0, 0, -angle], pivot=[x, y, 0])
        x += math.sin(math.radians(angle)) * seg
        y += math.cos(math.radians(angle)) * seg
        angle += 3.0
    m.bone("guard", "root", [0, 7.5, 0])
    m.cube("guard", [-2, 7, -2], [4, 0.6, 4], "dark_iron")
    m.cube("guard", [-1.5, 7.05, -2.3], [3, 0.5, 4.6], "gold")
    m.bone("gem", "guard", [0, 7.3, 0])
    m.cube("gem", [-0.6, 6.7, -0.6], [1.2, 1.2, 1.2], "shadow", rotation=[45, 45, 0], pivot=[0, 7.3, 0])
    m.bone("grip", "root", [0, 3, 0])
    m.cube("grip", [-0.6, -1, -0.6], [1.2, 8, 1.2], "leather")
    for i in range(4):
        m.cube("grip", [-0.65, -0.5 + i * 2, -0.65], [1.3, 0.6, 1.3], "shadow",
               rotation=[0, 45, 0], pivot=[0, -0.2 + i * 2, 0])
    m.cube("grip", [-0.8, -1.8, -0.8], [1.6, 0.8, 1.6], "gold")
    m.bone("afterimage", "root", [0, 16, 0])
    for i in range(3):
        m.cube("afterimage", [-2.5 - i, 11 + i * 5, -0.1], [0.4, 3, 0.2], "shadow")
    return m


def model_warhammer():
    m = Model("thunderlord_warhammer", "item", "")
    m.bone("root")
    m.bone("handle", "root", [0, 0, 0])
    m.cube("handle", [-0.75, -4, -0.75], [1.5, 26, 1.5], "wood")
    for i in range(5):
        m.cube("handle", [-0.9, -3 + i * 1.6, -0.9], [1.8, 0.7, 1.8], "leather")
    m.cube("handle", [-1.2, -5.5, -1.2], [2.4, 1.6, 2.4], "dark_iron")
    m.bone("head", "root", [0, 23, 0])
    m.cube("head", [-5.5, 20, -3], [11, 6, 6], "dark_iron")
    m.cube("head", [-6.2, 19.5, -3.5], [1.5, 7, 7], "iron")
    m.cube("head", [4.7, 19.5, -3.5], [1.5, 7, 7], "iron")
    m.cube("head", [-3, 19.6, -3.3], [0.8, 6.8, 6.6], "gold")
    m.cube("head", [2.2, 19.6, -3.3], [0.8, 6.8, 6.6], "gold")
    m.cube("head", [-1, 26, -1], [2, 3, 2], "iron", rotation=[0, 45, 0], pivot=[0, 26, 0])
    m.cube("head", [-0.5, 28.5, -0.5], [1, 1.5, 1], "storm")
    m.bone("gem", "head", [0, 23, 0])
    m.cube("gem", [-1.2, 21.8, -3.4], [2.4, 2.4, 0.6], "storm", rotation=[0, 0, 45], pivot=[0, 23, -3.1])
    m.cube("gem", [-1.2, 21.8, 2.8], [2.4, 2.4, 0.6], "storm", rotation=[0, 0, 45], pivot=[0, 23, 3.1])
    m.bone("sparks", "root", [0, 23, 0])
    for i, a, x, z in ring_points(7.5, 4):
        m.cube("sparks", [x - 0.3, 22.5, z - 0.3], [0.6, 1.4, 0.6], "storm", rotation=[0, 0, 30], pivot=[x, 23, z])
    return m


def model_warscythe():
    m = Model("frostbite_warscythe", "item", "")
    m.bone("root")
    m.bone("shaft", "root", [0, 0, 0])
    m.cube("shaft", [-0.6, -6, -0.6], [1.2, 34, 1.2], "dark_iron")
    for i in range(6):
        m.cube("shaft", [-0.75, -4 + i * 5.5, -0.75], [1.5, 0.8, 1.5], "ice")
    m.cube("shaft", [-1, -7.5, -1], [2, 1.8, 2], "iron")
    m.bone("blade", "root", [0, 27, 0])
    m.cube("blade", [-1.5, 25.5, -1.2], [3, 3, 2.4], "iron")
    angle = 80.0
    x, y = 1.0, 27.5
    for i in range(6):
        seg = 3.2
        w = 2.4 - i * 0.3
        m.cube("blade", [x, y - w / 2, -0.3], [seg + 0.2, w, 0.6], "ice",
               rotation=[0, 0, -(90 - angle)], pivot=[x, y, 0])
        m.cube("blade", [x, y - w / 2 - 0.35, -0.15], [seg + 0.2, 0.4, 0.3], "iron",
               rotation=[0, 0, -(90 - angle)], pivot=[x, y, 0])
        x += math.sin(math.radians(angle)) * seg
        y -= math.cos(math.radians(angle)) * seg
        angle += 9.0
    m.bone("gem", "blade", [0, 27, 0])
    m.cube("gem", [-0.8, 26.2, -1.5], [1.6, 1.6, 3.0], "storm", rotation=[0, 0, 45], pivot=[0, 27, 0])
    m.bone("crystals", "root", [0, 20, 0])
    for i, a, cx, cz in ring_points(4.0, 3, 0.5):
        m.cube("crystals", [cx - 0.4, 18 + i * 2, cz - 0.4], [0.8, 1.6, 0.8], "ice", rotation=[0, 45, 20], pivot=[cx, 19, cz])
    return m


def model_daggers():
    m = Model("bloodmoon_daggers", "item", "")
    m.bone("root")
    for side, sx in (("left", -3.2), ("right", 3.2)):
        b = m.bone("dagger_" + side, "root", [sx, 3, 0])
        tilt = 8 if side == "left" else -8
        m.cube(b, [sx - 0.7, 4, -0.2], [1.4, 8, 0.4], "iron", rotation=[0, 0, tilt], pivot=[sx, 3, 0])
        m.cube(b, [sx - 0.2, 4.5, -0.3], [0.4, 7, 0.6], "blood", rotation=[0, 0, tilt], pivot=[sx, 3, 0])
        m.cube(b, [sx - 0.5, 11.6, -0.2], [1.0, 1.0, 0.4], "iron", rotation=[0, 0, 45 + tilt], pivot=[sx, 12.1, 0])
        m.cube(b, [sx - 2, 3.3, -0.6], [4, 0.8, 1.2], "dark_iron", rotation=[0, 0, tilt], pivot=[sx, 3, 0])
        m.cube(b, [sx - 0.5, -0.5, -0.5], [1, 3.8, 1], "leather", rotation=[0, 0, tilt], pivot=[sx, 3, 0])
        m.cube(b, [sx - 0.7, -1.4, -0.7], [1.4, 1, 1.4], "blood", rotation=[0, 0, tilt], pivot=[sx, 3, 0])
    m.bone("gem", "root", [0, 3.7, 0])
    m.cube("gem", [-0.6, 3.1, -0.6], [1.2, 1.2, 1.2], "blood", rotation=[45, 0, 45], pivot=[0, 3.7, 0])
    return m


# ----------------------------------------------------------------------------
# Animations
# ----------------------------------------------------------------------------

def sample(length, fn, step=0.1):
    frames = OrderedDict()
    n = int(round(length / step))
    for i in range(n + 1):
        t = round(i * step, 3)
        frames["%.2f" % t] = [round(v, 4) for v in fn(t)]
    return frames


def anim(length, bones, loop=True):
    return {"loop": loop, "animation_length": length, "bones": bones}


def write_animation(kind, name, animations):
    data = {"format_version": "1.8.0", "animations": OrderedDict()}
    for key, value in animations.items():
        data["animations"]["animation.%s.%s" % (name, key)] = value
    write_json(os.path.join(ASSETS, "animations", kind, name + ".animation.json"), data)


def tau(t, length):
    return 2 * math.pi * t / length


def generate_animations():
    # Smeltery: idle embers, bellows pumping when active, lever pull when pouring
    write_animation("block", "smeltery", {
        "idle": anim(4.0, {
            "embers": {
                "rotation": sample(4.0, lambda t: [0, 90 * t, 0]),
                "position": sample(4.0, lambda t: [0, math.sin(tau(t, 4.0)) * 0.6, 0]),
            },
            "bellows_top": {"rotation": sample(4.0, lambda t: [0, 0, -2 - 2 * math.sin(tau(t, 4.0))])},
        }),
        "active": anim(1.0, {
            "bellows_top": {"rotation": sample(1.0, lambda t: [0, 0, -4 - 14 * (0.5 + 0.5 * math.sin(tau(t, 1.0)))], 0.05)},
            "bellows": {"scale": sample(1.0, lambda t: [1, 1 + 0.06 * math.sin(tau(t, 1.0)), 1], 0.05)},
            "embers": {
                "rotation": sample(1.0, lambda t: [0, 360 * t, 0], 0.05),
                "position": sample(1.0, lambda t: [0, 1.5 + math.sin(tau(t, 1.0) * 2) * 0.8, 0], 0.05),
                "scale": sample(1.0, lambda t: [1.2 + 0.3 * math.sin(tau(t, 1.0))] * 3, 0.05),
            },
        }),
        "pour": anim(1.0, {
            "lever": {"rotation": sample(1.0, lambda t: [0, 0, -50 * math.sin(math.pi * min(1.0, t * 1.4))], 0.05)},
            "spout": {"rotation": sample(1.0, lambda t: [-12 * math.sin(math.pi * t), 0, 0], 0.05)},
            "bellows_top": {"rotation": sample(1.0, lambda t: [0, 0, -4 - 10 * (0.5 + 0.5 * math.sin(tau(t, 0.5)))], 0.05)},
            "embers": {"position": sample(1.0, lambda t: [0, 2 + math.sin(tau(t, 1.0)), 0], 0.05)},
        }),
    })
    write_animation("block", "mold_table", {
        "idle": anim(3.0, {
            "steam": {"scale": sample(3.0, lambda t: [0.001, 0.001, 0.001])},
            "vents": {"position": sample(3.0, lambda t: [0, 0.1 * math.sin(tau(t, 3.0)), 0])},
        }),
        "cooling": anim(1.5, {
            "steam": {
                "position": sample(1.5, lambda t: [0, 4 * (t / 1.5), 0]),
                "scale": sample(1.5, lambda t: [1 - 0.6 * (t / 1.5)] * 3),
                "rotation": sample(1.5, lambda t: [0, 120 * t, 0]),
            },
            "vents": {"position": sample(1.5, lambda t: [0, 0.3 * abs(math.sin(tau(t, 0.75))), 0])},
        }),
    })
    write_animation("block", "forge_anvil", {
        "idle": anim(8.0, {
            "rune_ring": {
                "rotation": sample(8.0, lambda t: [0, 45 * t, 0], 0.2),
                "position": sample(8.0, lambda t: [0, 0.5 * math.sin(tau(t, 8.0) * 2), 0], 0.2),
                "scale": sample(8.0, lambda t: [0.85] * 3, 0.2),
            },
        }),
        "hot": anim(2.0, {
            "rune_ring": {
                "rotation": sample(2.0, lambda t: [0, 180 * t, 0], 0.05),
                "position": sample(2.0, lambda t: [0, 1.0 + 0.6 * math.sin(tau(t, 2.0) * 2), 0], 0.05),
                "scale": sample(2.0, lambda t: [1.0 + 0.08 * math.sin(tau(t, 2.0) * 4)] * 3, 0.05),
            },
        }),
    })
    write_animation("block", "quenching_barrel", {
        "idle": anim(3.0, {
            "bubbles": {
                "position": sample(3.0, lambda t: [0, 0.2 * math.sin(tau(t, 3.0)), 0]),
                "scale": sample(3.0, lambda t: [0.001] * 3),
            },
        }),
        "steam": anim(0.8, {
            "bubbles": {
                "position": sample(0.8, lambda t: [0, 3.5 * (t / 0.8), 0], 0.05),
                "scale": sample(0.8, lambda t: [1.2 - t] * 3, 0.05),
                "rotation": sample(0.8, lambda t: [0, 360 * t / 0.8, 0], 0.05),
            },
        }),
    })
    write_animation("block", "rune_altar", {
        "idle": anim(6.0, {
            "floating_runes": {
                "rotation": sample(6.0, lambda t: [0, 60 * t, 0], 0.2),
                "position": sample(6.0, lambda t: [0, 0.8 * math.sin(tau(t, 6.0) * 2), 0], 0.2),
            },
            "core": {
                "rotation": sample(6.0, lambda t: [0, 120 * t, 0], 0.2),
                "scale": sample(6.0, lambda t: [0.8 + 0.15 * math.sin(tau(t, 6.0) * 3)] * 3, 0.2),
            },
        }),
        "ritual": anim(1.0, {
            "floating_runes": {
                "rotation": sample(1.0, lambda t: [0, 720 * t, 0], 0.05),
                "position": sample(1.0, lambda t: [0, 3 + math.sin(tau(t, 1.0) * 2), 0], 0.05),
                "scale": sample(1.0, lambda t: [0.7 + 0.2 * math.sin(tau(t, 1.0))] * 3, 0.05),
            },
            "core": {
                "rotation": sample(1.0, lambda t: [360 * t, 720 * t, 0], 0.05),
                "scale": sample(1.0, lambda t: [1.6 + 0.4 * math.sin(tau(t, 1.0) * 2)] * 3, 0.05),
            },
        }),
    })
    # Weapons: idle pulses
    write_animation("item", "solaris_greatsword", {"idle": anim(2.0, {
        "gem": {"scale": sample(2.0, lambda t: [1 + 0.25 * math.sin(tau(t, 2.0))] * 3)},
        "embers": {"position": sample(2.0, lambda t: [0.4 * math.sin(tau(t, 2.0) * 2), 2.0 * (t / 2.0), 0]),
                   "scale": sample(2.0, lambda t: [max(0.001, 1 - t / 2.0)] * 3)},
    })})
    write_animation("item", "voidstep_katana", {"idle": anim(2.4, {
        "gem": {"rotation": sample(2.4, lambda t: [0, 150 * t, 0]),
                "scale": sample(2.4, lambda t: [1 + 0.2 * math.sin(tau(t, 2.4))] * 3)},
        "afterimage": {"position": sample(2.4, lambda t: [-1.5 * (t / 2.4), 0, 0]),
                       "scale": sample(2.4, lambda t: [max(0.001, 1 - t / 2.4)] * 3)},
    })})
    write_animation("item", "thunderlord_warhammer", {"idle": anim(1.6, {
        "gem": {"scale": sample(1.6, lambda t: [1 + 0.3 * abs(math.sin(tau(t, 1.6) * 2))] * 3, 0.05)},
        "sparks": {"rotation": sample(1.6, lambda t: [0, 225 * t, 0], 0.05),
                   "scale": sample(1.6, lambda t: [0.8 + 0.3 * math.sin(tau(t, 1.6) * 3)] * 3, 0.05)},
    })})
    write_animation("item", "frostbite_warscythe", {"idle": anim(3.0, {
        "gem": {"scale": sample(3.0, lambda t: [1 + 0.2 * math.sin(tau(t, 3.0))] * 3)},
        "crystals": {"rotation": sample(3.0, lambda t: [0, 120 * t, 0]),
                     "position": sample(3.0, lambda t: [0, 1.2 * math.sin(tau(t, 3.0)), 0])},
    })})
    write_animation("item", "bloodmoon_daggers", {"idle": anim(1.2, {
        "gem": {"scale": sample(1.2, lambda t: [1 + 0.35 * max(0.0, math.sin(tau(t, 1.2) * 2))] * 3, 0.05)},
        "dagger_left": {"rotation": sample(1.2, lambda t: [0, 0, 2 * math.sin(tau(t, 1.2))], 0.05)},
        "dagger_right": {"rotation": sample(1.2, lambda t: [0, 0, -2 * math.sin(tau(t, 1.2))], 0.05)},
    })})


# ----------------------------------------------------------------------------
# 32x32 item sprites
# ----------------------------------------------------------------------------

S = 32


def new_sprite():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def outline(img, color=(20, 14, 18, 255)):
    src = img.copy()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3] == 0:
                for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + ox, y + oy
                    if 0 <= nx < w and 0 <= ny < h and src.getpixel((nx, ny))[3] > 0:
                        img.putpixel((x, y), color)
                        break
    return img


def diag_line(img, x0, y0, x1, y1, width, color_fn):
    """Draws a thick diagonal stroke; color_fn(t, across) -> rgba."""
    length = math.hypot(x1 - x0, y1 - y0)
    steps = int(length * 3) + 1
    nx, ny = -(y1 - y0) / length, (x1 - x0) / length
    for i in range(steps + 1):
        t = i / steps
        cx = x0 + (x1 - x0) * t
        cy = y0 + (y1 - y0) * t
        half = width(t) / 2.0 if callable(width) else width / 2.0
        k = -half
        while k <= half:
            px, py = int(round(cx + nx * k)), int(round(cy + ny * k))
            if 0 <= px < S and 0 <= py < S:
                across = (k + half) / (2 * half) if half > 0 else 0.5
                img.putpixel((px, py), color_fn(t, across))
            k += 0.5


def blade_shape(kind):
    """Returns (stroke list) describing the blade silhouette for each weapon type."""
    if kind == "greatsword":
        return dict(x0=7, y0=24, x1=28, y1=3, width=lambda t: 5.0 if t < 0.88 else 5.0 * (1 - (t - 0.88) / 0.12) + 0.6)
    if kind == "katana":
        return dict(x0=6, y0=25, x1=29, y1=3, width=lambda t: 2.6 if t < 0.92 else 1.0)
    if kind == "warhammer":
        return dict(x0=9, y0=22, x1=24, y1=7, width=lambda t: 9.0 if t > 0.62 else 2.0)
    if kind == "warscythe":
        return dict(x0=10, y0=24, x1=26, y1=5, width=lambda t: 3.0 + 3.5 * math.sin(math.pi * t))
    return dict(x0=10, y0=22, x1=25, y1=7, width=lambda t: 3.0 if t < 0.85 else 3.0 * (1 - (t - 0.85) / 0.15) + 0.5)


def draw_blade(kind, hot):
    img = new_sprite()
    shape = blade_shape(kind)

    def color(t, across):
        edge = abs(across - 0.5) * 2
        if hot:
            core = mix((255, 250, 190), (255, 150, 30), edge)
            c = mix(core, (200, 40, 10), max(0.0, t - 0.75) * 1.5)
            return rgba(c)
        light = 1.2 - 0.5 * edge + (0.25 if across < 0.3 else 0)
        return rgba(shade((200, 200, 205), light * (0.92 + 0.15 * t)))

    diag_line(img, shape["x0"], shape["y0"], shape["x1"], shape["y1"], shape["width"], color)
    # tang
    tang = (120, 50, 20, 255) if hot else (90, 90, 96, 255)
    diag_line(img, 3, 28, shape["x0"] + 1, shape["y0"] - 1, 1.6, lambda t, a: tang)
    if hot:
        rng = random.Random(kind)
        for _ in range(10):
            x, y = rng.randrange(6, 30), rng.randrange(2, 26)
            if img.getpixel((x, y))[3] > 0:
                img.putpixel((x, y), (255, 255, 230, 255))
    return outline(img, (60, 10, 0, 255) if hot else (24, 24, 30, 255))


def draw_hammer():
    img = new_sprite()
    diag_line(img, 4, 28, 20, 12, 2.4, lambda t, a: rgba(shade((126, 86, 52), 1.15 - 0.4 * a)))
    for i in range(3):
        diag_line(img, 5 + i * 3, 27 - i * 3, 6 + i * 3, 26 - i * 3, 2.8, lambda t, a: (70, 40, 26, 255))
    # head
    draw = ImageDraw.Draw(img)
    head = [(14, 8), (22, 2), (29, 9), (21, 17)]
    draw.polygon(head, fill=(80, 82, 94, 255))
    for y in range(S):
        for x in range(S):
            p = img.getpixel((x, y))
            if p == (80, 82, 94, 255):
                f = 1.35 - 0.035 * (x + y - 16)
                img.putpixel((x, y), rgba(shade((92, 94, 108), f)))
    draw.line([(15, 8), (22, 3)], fill=(220, 225, 235, 255))
    draw.line([(20, 14), (28, 9)], fill=(255, 150, 40, 255))
    draw.point([(18, 9), (21, 7)], fill=(255, 210, 90, 255))
    return outline(img)


def draw_hilt():
    img = new_sprite()
    draw = ImageDraw.Draw(img)
    draw.rectangle([6, 12, 25, 15], fill=(196, 160, 60, 255))
    draw.line([(6, 12), (25, 12)], fill=(255, 230, 140, 255))
    draw.line([(6, 15), (25, 15)], fill=(120, 90, 30, 255))
    for y in range(16, 29):
        for x in range(14, 18):
            band = (y // 2) % 2
            c = (110, 64, 36) if band else (82, 46, 26)
            img.putpixel((x, y), rgba(shade(c, 1.2 if x == 14 else (0.8 if x == 17 else 1.0))))
    draw.rectangle([14, 4, 17, 11], fill=(150, 150, 160, 255))
    draw.line([(14, 4), (14, 11)], fill=(210, 210, 220, 255))
    return outline(img)


def draw_pommel():
    img = new_sprite()
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 15.5, y - 17.5)
            if d < 9:
                f = 1.3 - d / 12 - 0.02 * (x - 15 + y - 17)
                img.putpixel((x, y), rgba(shade((210, 170, 60), f)))
    for y in range(S):
        for x in range(S):
            if math.hypot(x - 15.5, y - 17.5) < 3.2:
                img.putpixel((x, y), (255, 80, 60, 255) if (x + y) % 3 else (255, 200, 180, 255))
    draw = ImageDraw.Draw(img)
    draw.rectangle([14, 4, 17, 9], fill=(150, 150, 160, 255))
    return outline(img)


RUNE_COLORS = {
    "fire": ((255, 230, 120), (230, 70, 10)),
    "shadow": ((220, 150, 255), (70, 10, 120)),
    "storm": ((230, 250, 255), (40, 120, 255)),
    "ice": ((235, 255, 255), (90, 180, 240)),
    "blood": ((255, 120, 130), (140, 0, 25)),
}


def draw_rune(kind):
    img = new_sprite()
    light, dark = RUNE_COLORS[kind]
    for y in range(S):
        for x in range(S):
            d = abs(x - 15.5) / 12 + abs(y - 15.5) / 14
            if d < 1:
                f = (x + y) / 62
                img.putpixel((x, y), rgba(mix(light, dark, min(1.0, d * 0.6 + f * 0.6))))
    draw = ImageDraw.Draw(img)
    glyph = {
        "fire": [(15, 8), (11, 18), (15, 15), (13, 24), (20, 12), (16, 14), (18, 8)],
        "shadow": [(10, 12), (21, 12), (15, 22), (10, 12)],
        "storm": [(17, 6), (11, 17), (16, 17), (13, 26), (21, 13), (16, 13), (19, 6)],
        "ice": [(15, 7), (15, 25)],
        "blood": [(15, 8), (11, 17), (15, 23), (19, 17), (15, 8)],
    }[kind]
    draw.line(glyph, fill=(255, 255, 255, 255), width=1)
    if kind == "ice":
        draw.line([(9, 12), (21, 20)], fill=(255, 255, 255, 255))
        draw.line([(9, 20), (21, 12)], fill=(255, 255, 255, 255))
    if kind == "shadow":
        draw.ellipse([13, 13, 17, 17], fill=(30, 0, 50, 255))
    img.putpixel((12, 7), (255, 255, 255, 255))
    img.putpixel((11, 8), (255, 255, 255, 220))
    return outline(img, (16, 8, 24, 255))


def draw_oil():
    img = new_sprite()
    draw = ImageDraw.Draw(img)
    draw.ellipse([7, 11, 25, 29], fill=(200, 220, 230, 160))
    draw.rectangle([13, 4, 19, 12], fill=(200, 220, 230, 160))
    draw.rectangle([12, 2, 20, 5], fill=(120, 80, 40, 255))
    for y in range(16, 29):
        for x in range(8, 25):
            if img.getpixel((x, y))[3] > 0:
                f = 1.2 - (y - 16) / 26 - (x - 8) / 60
                img.putpixel((x, y), rgba(shade((170, 110, 20), f), 240))
    draw.line([(10, 14), (10, 22)], fill=(255, 255, 255, 200))
    draw.point([(18, 20), (14, 24)], fill=(255, 220, 120, 255))
    return outline(img)


def draw_icon():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    for y in range(128):
        for x in range(128):
            d = math.hypot(x - 63.5, y - 63.5)
            if d < 62:
                t = d / 62
                img.putpixel((x, y), rgba(mix((60, 30, 90), (14, 8, 22), t)))
            if 58 < d < 62:
                img.putpixel((x, y), (240, 196, 72, 255))
    blade = draw_blade("greatsword", True).resize((96, 96), Image.NEAREST)
    hammer = draw_hammer().resize((64, 64), Image.NEAREST)
    img.alpha_composite(blade, (16, 12))
    img.alpha_composite(hammer, (8, 56))
    return img


# ----------------------------------------------------------------------------
# Particle and BER helper textures
# ----------------------------------------------------------------------------

def particle_frames(kind):
    frames = []
    for f in range(4):
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        for y in range(8):
            for x in range(8):
                d = math.hypot(x - 3.5, y - 3.5)
                if kind == "forge_spark":
                    r = 3.5 - f * 0.8
                    if d < r:
                        t = d / max(r, 0.1)
                        img.putpixel((x, y), rgba(mix((255, 255, 220), (255, 120, 20), t), 255 - int(t * 120)))
                elif kind == "steam_puff":
                    r = 2.0 + f * 0.6
                    if d < r:
                        img.putpixel((x, y), (235, 240, 245, int(220 * (1 - d / r) * (1 - f * 0.15))))
                elif kind == "rune_glyph":
                    on = ((x == 3 or x == 4) and 1 <= y <= 6) or (y == 1 + f and 1 <= x <= 6) or \
                         (x == y and f % 2 == 0) or (x == 7 - y and f % 2 == 1)
                    if on:
                        img.putpixel((x, y), (140, 240, 255, 255))
                    elif d < 3.6:
                        img.putpixel((x, y), (60, 70, 200, 70))
        frames.append(img)
    return frames


def liquid_texture(seed, glow):
    rng = random.Random(seed)
    img = Image.new("RGBA", (32, 32))
    for y in range(32):
        for x in range(32):
            v = 0.7 + 0.15 * math.sin(x * 0.4 + math.sin(y * 0.3) * 2) + 0.1 * math.cos(y * 0.5 + x * 0.1)
            v += rng.uniform(-0.05, 0.05)
            if glow and ((x * 5 + y * 3) % 17 == 0):
                v = 1.0
            g = clamp(255 * min(1.0, v))
            img.putpixel((x, y), (g, g, g, 255 if glow else 220))
    return img


# ----------------------------------------------------------------------------
# Writers
# ----------------------------------------------------------------------------

BLOCKS = ["smeltery", "mold_table", "forge_anvil", "quenching_barrel", "rune_altar"]
WEAPONS = OrderedDict([
    ("solaris_greatsword", ("greatsword", model_greatsword, {"iron": lambda r: paint_brushed_metal(r, base=(236, 196, 150))})),
    ("voidstep_katana", ("katana", model_katana, {"iron": lambda r: paint_brushed_metal(r, base=(120, 100, 150), streak=0.14)})),
    ("thunderlord_warhammer", ("warhammer", model_warhammer, {})),
    ("frostbite_warscythe", ("warscythe", model_warscythe, {"iron": lambda r: paint_brushed_metal(r, base=(196, 228, 246))})),
    ("bloodmoon_daggers", ("daggers", model_daggers, {"iron": lambda r: paint_brushed_metal(r, base=(176, 60, 70), streak=0.1)})),
])
WEAPON_TYPES = ["greatsword", "katana", "warhammer", "warscythe", "daggers"]
RUNES = ["fire", "shadow", "storm", "ice", "blood"]

BLOCK_MODEL_FUNCS = {
    "smeltery": model_smeltery,
    "mold_table": model_mold_table,
    "forge_anvil": model_forge_anvil,
    "quenching_barrel": model_quenching_barrel,
    "rune_altar": model_rune_altar,
}

BLOCK_ITEM_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.55, 0.55, 0.55]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}


def weapon_display(model):
    ys = []
    for b in model.bones.values():
        for c in b["cubes"]:
            ys.append(c["origin"][1])
            ys.append(c["origin"][1] + c["size"][1])
    lo, hi = min(ys), max(ys)
    height = hi - lo
    center = (lo + hi) / 2.0
    gui_scale = round(min(0.9, 15.0 / height), 3)
    off = -0.7071 * gui_scale * center
    hand_scale = round(min(0.85, 22.0 / height), 3)
    return {
        "gui": {"rotation": [0, 0, -45], "translation": [round(off, 3), round(off, 3), 0], "scale": [gui_scale] * 3},
        "fixed": {"rotation": [0, 0, -45], "translation": [round(off, 3), round(off, 3), 0], "scale": [gui_scale] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [round(gui_scale * 0.6, 3)] * 3},
        "head": {"rotation": [0, 0, 0], "translation": [0, 8, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [-20, 90, 0], "translation": [0, round(-1.5 * hand_scale, 3), 1], "scale": [hand_scale] * 3},
        "thirdperson_lefthand": {"rotation": [-20, -90, 0], "translation": [0, round(-1.5 * hand_scale, 3), 1], "scale": [hand_scale] * 3},
        "firstperson_righthand": {"rotation": [0, 90, -20], "translation": [1.5, 0, 0], "scale": [round(hand_scale * 0.75, 3)] * 3},
        "firstperson_lefthand": {"rotation": [0, -90, 20], "translation": [1.5, 0, 0], "scale": [round(hand_scale * 0.75, 3)] * 3},
    }


def write_blocks():
    for i, name in enumerate(BLOCKS):
        model = BLOCK_MODEL_FUNCS[name]()
        write_json(os.path.join(ASSETS, "geo", "block", name + ".geo.json"), model.to_geo())
        atlas, glow = build_atlas(name, 1000 + i)
        save_png(atlas, os.path.join(ASSETS, "textures", "block", name + ".png"))
        save_png(glow, os.path.join(ASSETS, "textures", "block", name + "_glowmask.png"))
        cx, cy = material_cell("stone" if name != "rune_altar" else "obsidian")
        particle = atlas.crop((cx * 16, cy * 16, cx * 16 + 16, cy * 16 + 16))
        save_png(particle, os.path.join(ASSETS, "textures", "block", name + "_particle.png"))
        # world block model only defines the particle sprite (geometry rendered by GeckoLib)
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"),
                   {"textures": {"particle": "%s:block/%s_particle" % (MOD_ID, name)}})
        # full multi-element model for the inventory item
        write_json(os.path.join(ASSETS, "models", "block", name + "_item.json"), model.to_json_model(BLOCK_ITEM_DISPLAY))
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "%s:block/%s_item" % (MOD_ID, name)})
        variants = OrderedDict()
        for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            variants["facing=%s" % facing] = {"model": "%s:block/%s" % (MOD_ID, name), "y": rot}
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})


def write_weapons():
    for i, (name, (wtype, fn, overrides)) in enumerate(WEAPONS.items()):
        model = fn()
        write_json(os.path.join(ASSETS, "geo", "item", name + ".geo.json"), model.to_geo())
        atlas, glow = build_atlas(name, 2000 + i, overrides)
        save_png(atlas, os.path.join(ASSETS, "textures", "item", name + ".png"))
        save_png(glow, os.path.join(ASSETS, "textures", "item", name + "_glowmask.png"))
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {
            "parent": "builtin/entity",
            "gui_light": "front",
            "textures": {"particle": "%s:item/%s_blade" % (MOD_ID, wtype)},
            "display": weapon_display(model),
        })


def write_items():
    tex = os.path.join(ASSETS, "textures", "item")
    models = os.path.join(ASSETS, "models", "item")
    for wtype in WEAPON_TYPES:
        save_png(draw_blade(wtype, True), os.path.join(tex, "hot_%s_blade.png" % wtype))
        save_png(draw_blade(wtype, False), os.path.join(tex, "%s_blade.png" % wtype))
        write_json(os.path.join(models, "hot_%s_blade.json" % wtype),
                   {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/hot_%s_blade" % (MOD_ID, wtype)}})
        write_json(os.path.join(models, "%s_blade.json" % wtype),
                   {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/%s_blade" % (MOD_ID, wtype)}})
    for item, blade_prefix in (("hot_blade", "hot_%s_blade"), ("cooled_blade", "%s_blade")):
        overrides = []
        for idx, wtype in enumerate(WEAPON_TYPES):
            if idx == 0:
                continue
            overrides.append({"predicate": {"%s:weapon_type" % MOD_ID: round(idx / 4.0, 3)},
                              "model": "%s:item/%s" % (MOD_ID, blade_prefix % wtype)})
        write_json(os.path.join(models, item + ".json"), {
            "parent": "minecraft:item/handheld",
            "textures": {"layer0": "%s:item/%s" % (MOD_ID, blade_prefix % WEAPON_TYPES[0])},
            "overrides": overrides,
        })
    sprites = {
        "forging_hammer": draw_hammer(),
        "hilt": draw_hilt(),
        "pommel": draw_pommel(),
        "quenching_oil": draw_oil(),
    }
    for rune in RUNES:
        sprites["%s_rune" % rune] = draw_rune(rune)
    for name, img in sprites.items():
        save_png(img, os.path.join(tex, name + ".png"))
        parent = "minecraft:item/handheld" if name == "forging_hammer" else "minecraft:item/generated"
        write_json(os.path.join(models, name + ".json"), {"parent": parent, "textures": {"layer0": "%s:item/%s" % (MOD_ID, name)}})
    save_png(draw_icon(), os.path.join(ASSETS, "icon.png"))


def write_particles():
    for kind in ("forge_spark", "steam_puff", "rune_glyph"):
        names = []
        for i, frame in enumerate(particle_frames(kind)):
            save_png(frame, os.path.join(ASSETS, "textures", "particle", "%s_%d.png" % (kind, i)))
            names.append("%s:%s_%d" % (MOD_ID, kind, i))
        write_json(os.path.join(ASSETS, "particles", kind + ".json"), {"textures": names})
    save_png(liquid_texture(11, True), os.path.join(ASSETS, "textures", "misc", "molten_metal.png"))
    save_png(liquid_texture(12, False), os.path.join(ASSETS, "textures", "misc", "liquid_surface.png"))


SOUNDS = OrderedDict([
    ("forge.hammer_strike", (["minecraft:random/anvil_use", "minecraft:random/anvil_land"], 1.0, 1.0)),
    ("forge.hammer_perfect", (["minecraft:random/anvil_land", "minecraft:block/amethyst/shimmer1"], 1.0, 1.2)),
    ("forge.hammer_miss", (["minecraft:block/chain/break1", "minecraft:block/chain/break2"], 0.9, 0.8)),
    ("smeltery.insert", (["minecraft:liquid/lavapop"], 0.8, 1.0)),
    ("smeltery.bellows", (["minecraft:block/furnace/fire_crackle1", "minecraft:block/furnace/fire_crackle2"], 0.7, 0.9)),
    ("smeltery.pour", (["minecraft:item/bucket/empty_lava1", "minecraft:item/bucket/empty_lava2", "minecraft:item/bucket/empty_lava3"], 0.9, 0.9)),
    ("quench.hiss", (["minecraft:random/fizz"], 1.0, 0.8)),
    ("altar.charge", (["minecraft:block/beacon/ambient", "minecraft:block/enchantment_table/enchant1"], 1.0, 1.0)),
    ("altar.awaken", (["minecraft:block/beacon/activate", "minecraft:item/totem/use_totem"], 1.0, 1.0)),
    ("weapon.fire_crescent", (["minecraft:mob/ghast/fireball4", "minecraft:fire/ignite"], 1.0, 0.9)),
    ("weapon.void_dash", (["minecraft:mob/endermen/portal", "minecraft:mob/endermen/portal2"], 1.0, 1.3)),
    ("weapon.thunder_slam", (["minecraft:random/explode1", "minecraft:random/explode2"], 1.0, 0.7)),
    ("weapon.frost_vortex", (["minecraft:random/glass1", "minecraft:item/elytra/elytra_loop"], 1.0, 1.2)),
    ("weapon.blood_drain", (["minecraft:mob/warden/heartbeat_1", "minecraft:mob/warden/heartbeat_2"], 1.0, 1.4)),
])


def write_sounds():
    data = OrderedDict()
    for key, (files, volume, pitch) in SOUNDS.items():
        data[key] = {
            "subtitle": "subtitles.%s.%s" % (MOD_ID, key),
            "sounds": [{"name": f, "volume": volume, "pitch": pitch} for f in files],
        }
    write_json(os.path.join(ASSETS, "sounds.json"), data)


def write_lang():
    L = OrderedDict()
    L["itemGroup.arcaneforge.main"] = "Arcane Forge"
    L["block.arcaneforge.smeltery"] = "Smeltery Furnace"
    L["block.arcaneforge.mold_table"] = "Casting Mold Table"
    L["block.arcaneforge.forge_anvil"] = "Forge Anvil"
    L["block.arcaneforge.quenching_barrel"] = "Quenching Barrel"
    L["block.arcaneforge.rune_altar"] = "Rune Awakening Altar"
    L["item.arcaneforge.forging_hammer"] = "Forging Hammer"
    L["item.arcaneforge.hot_blade"] = "Glowing Blade"
    L["item.arcaneforge.hot_blade.blank"] = "Glowing %s Blank"
    L["item.arcaneforge.hot_blade.forged"] = "Hot Forged %s Blade"
    L["item.arcaneforge.cooled_blade"] = "Forged Blade"
    L["item.arcaneforge.cooled_blade.typed"] = "%s %s Blade"
    L["item.arcaneforge.hilt"] = "Wrapped Hilt"
    L["item.arcaneforge.pommel"] = "Gilded Pommel"
    L["item.arcaneforge.quenching_oil"] = "Quenching Oil"
    L["item.arcaneforge.fire_rune"] = "Fire Rune"
    L["item.arcaneforge.shadow_rune"] = "Shadow Rune"
    L["item.arcaneforge.storm_rune"] = "Storm Rune"
    L["item.arcaneforge.ice_rune"] = "Ice Rune"
    L["item.arcaneforge.blood_rune"] = "Blood Rune"
    L["item.arcaneforge.solaris_greatsword"] = "Solaris Greatsword"
    L["item.arcaneforge.voidstep_katana"] = "Voidstep Katana"
    L["item.arcaneforge.thunderlord_warhammer"] = "Thunderlord Warhammer"
    L["item.arcaneforge.frostbite_warscythe"] = "Frostbite Warscythe"
    L["item.arcaneforge.bloodmoon_daggers"] = "Bloodmoon Daggers"
    L["item.arcaneforge.weapon.variant"] = "%s %s"
    for wtype, name in zip(WEAPON_TYPES, ["Greatsword", "Katana", "Warhammer", "Warscythe", "Twin Daggers"]):
        L["arcaneforge.weapon_type.%s" % wtype] = name
    for rune, name, adj in zip(RUNES, ["Fire", "Shadow", "Storm", "Ice", "Blood"],
                               ["Flame-Runed", "Shadow-Runed", "Storm-Runed", "Frost-Runed", "Blood-Runed"]):
        L["arcaneforge.rune.%s" % rune] = name
        L["arcaneforge.rune.%s.adjective" % rune] = adj
    for metal, name in (("iron", "Iron"), ("gold", "Golden"), ("copper", "Copper"), ("netherite", "Netherite")):
        L["arcaneforge.metal.%s" % metal] = name
    L["tooltip.arcaneforge.quality"] = "Quality: %s"
    L["tooltip.arcaneforge.metal"] = "Metal: %s"
    L["tooltip.arcaneforge.rune"] = "Rune: %s"
    L["tooltip.arcaneforge.type"] = "Form: %s"
    L["tooltip.arcaneforge.tempered"] = "Oil-Tempered (+%s damage)"
    L["tooltip.arcaneforge.resonance"] = "Rune Resonance: signature skill empowered"
    L["tooltip.arcaneforge.cooldown"] = "Cooldown: %ss"
    L["tooltip.arcaneforge.unworked"] = "Unworked - place on a Forge Anvil and hammer it"
    L["tooltip.arcaneforge.needs_quench"] = "Still glowing - plunge it into a Quenching Barrel"
    L["tooltip.arcaneforge.ready_for_altar"] = "Combine with a Hilt, Pommel and Rune at a Rune Altar"
    L["tooltip.arcaneforge.hammer"] = "Right-click a Forge Anvil when the ring meets the target"
    L["tooltip.arcaneforge.rune_item"] = "Socket into a weapon at a Rune Awakening Altar"
    L["tooltip.arcaneforge.oil"] = "Pour into a Quenching Barrel for tempered blades"
    L["tooltip.arcaneforge.smeltery"] = "Insert ores/ingots and fuel. Empty hand: pull lever to pour"
    L["tooltip.arcaneforge.mold_table"] = "Empty hand: cycle mold. Receives molten metal"
    L["tooltip.arcaneforge.forge_anvil"] = "Place a glowing blank, then strike in rhythm"
    L["tooltip.arcaneforge.quenching_barrel"] = "Fill with water or quenching oil"
    L["tooltip.arcaneforge.rune_altar"] = "Blade + Hilt + Pommel + Rune = awakened weapon"
    abilities = {
        "greatsword": "Solar Crescent: sweeping fire wave that launches and ignites",
        "katana": "Voidstep: lightning dash that slashes everything you pass",
        "warhammer": "Thunderfall: leap and slam, summoning a lightning shockwave",
        "warscythe": "Glacial Vortex: drag enemies in and encase them in ice",
        "daggers": "Crimson Frenzy: sacrifice health for speed, strength and double lifesteal",
    }
    for k, v in abilities.items():
        L["tooltip.arcaneforge.ability.%s" % k] = "Right-click - " + v
    L["tooltip.arcaneforge.passive.daggers"] = "Passive: Lifesteal on every hit"
    effects = {
        "fire": "On hit: sets targets ablaze",
        "shadow": "On hit: blinds and weakens",
        "storm": "On hit: chance to call lightning",
        "ice": "On hit: chills and slows",
        "blood": "On hit: drains life",
    }
    for k, v in effects.items():
        L["tooltip.arcaneforge.rune_effect.%s" % k] = v
    L["hologram.arcaneforge.smeltery.empty"] = "Smeltery: Empty"
    L["hologram.arcaneforge.smeltery.molten"] = "Molten %s: %s / %s"
    L["hologram.arcaneforge.smeltery.melting"] = "Melting: %s queued"
    L["hologram.arcaneforge.smeltery.fuel"] = "Fuel: %ss"
    L["hologram.arcaneforge.smeltery.no_fuel"] = "No fuel!"
    L["hologram.arcaneforge.smeltery.pouring"] = "Pouring..."
    L["hologram.arcaneforge.mold.type"] = "Mold: %s"
    L["hologram.arcaneforge.mold.fill"] = "%s: %s / %s"
    L["hologram.arcaneforge.mold.cooling"] = "Solidifying %s%%"
    L["hologram.arcaneforge.mold.ready"] = "Ready - take the glowing blank"
    L["hologram.arcaneforge.anvil.empty"] = "Place a glowing blank"
    L["hologram.arcaneforge.anvil.progress"] = "Strikes: %s / %s"
    L["hologram.arcaneforge.anvil.done"] = "Forged!"
    L["hologram.arcaneforge.rating.perfect"] = "PERFECT!"
    L["hologram.arcaneforge.rating.good"] = "GOOD"
    L["hologram.arcaneforge.rating.ok"] = "OK"
    L["hologram.arcaneforge.rating.miss"] = "MISS"
    L["hologram.arcaneforge.barrel.water"] = "Water: %s / %s"
    L["hologram.arcaneforge.barrel.oil"] = "Quenching Oil: %s / %s"
    L["hologram.arcaneforge.barrel.empty"] = "Empty barrel"
    L["hologram.arcaneforge.altar.missing"] = "Needs: %s"
    L["hologram.arcaneforge.altar.ready"] = "Right-click with empty hand to awaken"
    L["hologram.arcaneforge.altar.ritual"] = "Awakening... %s%%"
    L["hologram.arcaneforge.altar.done"] = "Take your weapon!"
    L["hologram.arcaneforge.slot.blade"] = "Blade"
    L["hologram.arcaneforge.slot.hilt"] = "Hilt"
    L["hologram.arcaneforge.slot.pommel"] = "Pommel"
    L["hologram.arcaneforge.slot.rune"] = "Rune"
    for key in SOUNDS.keys():
        L["subtitles.%s.%s" % (MOD_ID, key)] = {
            "forge.hammer_strike": "Hammer strikes metal",
            "forge.hammer_perfect": "Perfect hammer strike",
            "forge.hammer_miss": "Hammer glances off",
            "smeltery.insert": "Metal plops into smeltery",
            "smeltery.bellows": "Bellows roar",
            "smeltery.pour": "Molten metal pours",
            "quench.hiss": "Blade hisses",
            "altar.charge": "Altar hums",
            "altar.awaken": "Weapon awakens",
            "weapon.fire_crescent": "Solar crescent roars",
            "weapon.void_dash": "Voidstep dash",
            "weapon.thunder_slam": "Thunder slam",
            "weapon.frost_vortex": "Frost vortex howls",
            "weapon.blood_drain": "Blood frenzy",
        }[key]
    write_json(os.path.join(ASSETS, "lang", "en_us.json"), L)


def shaped(name, pattern, key, result, count=1, category="equipment"):
    k = OrderedDict()
    for ch, ing in key.items():
        k[ch] = {"tag": ing[1:]} if ing.startswith("#") else {"item": ing}
    write_json(os.path.join(DATA, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shaped",
        "category": category,
        "pattern": pattern,
        "key": k,
        "result": {"id": result, "count": count},
    })


def shapeless(name, ingredients, result, count=1, category="misc"):
    write_json(os.path.join(DATA, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shapeless",
        "category": category,
        "ingredients": [{"tag": i[1:]} if i.startswith("#") else {"item": i} for i in ingredients],
        "result": {"id": result, "count": count},
    })


def write_data():
    A = MOD_ID + ":"
    shaped("smeltery", ["BIB", "BFB", "BBB"], {"B": "minecraft:bricks", "I": "minecraft:iron_ingot", "F": "minecraft:blast_furnace"}, A + "smeltery", category="misc")
    shaped("mold_table", [" C ", "SSS", "P P"], {"C": "minecraft:clay_ball", "S": "minecraft:smooth_stone", "P": "#minecraft:planks"}, A + "mold_table", category="misc")
    shaped("forge_anvil", ["IGI", " A ", "SSS"], {"I": "minecraft:iron_ingot", "G": "minecraft:gold_ingot", "A": "minecraft:anvil", "S": "minecraft:smooth_stone"}, A + "forge_anvil", category="misc")
    shaped("quenching_barrel", ["IBI", " C "], {"I": "minecraft:iron_ingot", "B": "minecraft:barrel", "C": "minecraft:cauldron"}, A + "quenching_barrel", category="misc")
    shaped("rune_altar", ["ADA", "OLO", "OOO"], {"A": "minecraft:amethyst_shard", "D": "minecraft:diamond", "O": "minecraft:obsidian", "L": "minecraft:lapis_block"}, A + "rune_altar", category="misc")
    shaped("forging_hammer", ["III", "ISI", " S "], {"I": "minecraft:iron_ingot", "S": "minecraft:stick"}, A + "forging_hammer")
    shaped("hilt", ["L", "S", "L"], {"L": "minecraft:leather", "S": "minecraft:stick"}, A + "hilt")
    shaped("pommel", ["NGN"], {"N": "minecraft:iron_nugget", "G": "minecraft:gold_ingot"}, A + "pommel")
    shapeless("quenching_oil", ["minecraft:glass_bottle", "minecraft:coal", "minecraft:dried_kelp"], A + "quenching_oil")
    rune_core = {"fire": "minecraft:blaze_powder", "shadow": "minecraft:ender_pearl", "storm": "minecraft:lightning_rod",
                 "ice": "minecraft:blue_ice", "blood": "minecraft:fermented_spider_eye"}
    for rune, core in rune_core.items():
        shaped("%s_rune" % rune, ["ALA", "LCL", "ALA"], {"A": "minecraft:amethyst_shard", "L": "minecraft:lapis_lazuli", "C": core}, A + "%s_rune" % rune)
    for name in BLOCKS:
        write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": A + name}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
    write_json(os.path.join(DATA_MC, "tags", "block", "mineable", "pickaxe.json"),
               {"replace": False, "values": [A + b for b in BLOCKS if b != "quenching_barrel"]})
    write_json(os.path.join(DATA_MC, "tags", "block", "mineable", "axe.json"),
               {"replace": False, "values": [A + "quenching_barrel"]})
    weapons = [A + w for w in WEAPONS.keys()]
    for tag in ("swords", "enchantable/sword", "enchantable/sharp_weapon", "enchantable/weapon",
                "enchantable/fire_aspect", "enchantable/durability", "enchantable/vanishing"):
        write_json(os.path.join(DATA_MC, "tags", "item", tag + ".json"), {"replace": False, "values": weapons})
    write_json(os.path.join(DATA_MC, "tags", "item", "enchantable", "durability.json"),
               {"replace": False, "values": weapons + [A + "forging_hammer"]})


def validate():
    errors = 0
    for path in WRITTEN_JSON:
        try:
            with open(path, encoding="utf-8") as fh:
                json.load(fh)
        except ValueError as exc:
            errors += 1
            print("INVALID JSON:", path, exc)
    # every geo model must reference only bones that exist and animations must target real bones
    for kind in ("block", "item"):
        geo_dir = os.path.join(ASSETS, "geo", kind)
        for fn in os.listdir(geo_dir):
            name = fn[:-len(".geo.json")]
            with open(os.path.join(geo_dir, fn)) as fh:
                geo = json.load(fh)
            bones = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
            for b in geo["minecraft:geometry"][0]["bones"]:
                if b.get("parent") and b["parent"] not in bones:
                    errors += 1
                    print("Missing parent bone", b["parent"], "in", fn)
            anim_path = os.path.join(ASSETS, "animations", kind, name + ".animation.json")
            if not os.path.exists(anim_path):
                errors += 1
                print("Missing animation file for", name)
                continue
            with open(anim_path) as fh:
                anims = json.load(fh)
            for aname, a in anims["animations"].items():
                for bone in a["bones"]:
                    if bone not in bones:
                        errors += 1
                        print("Animation", aname, "targets unknown bone", bone)
            for tex in (name + ".png", name + "_glowmask.png"):
                if not os.path.exists(os.path.join(ASSETS, "textures", kind, tex)):
                    errors += 1
                    print("Missing texture", tex)
    if errors:
        raise SystemExit("Asset validation failed with %d error(s)" % errors)
    print("Validated %d JSON files - all OK." % len(WRITTEN_JSON))


def clean():
    for sub in ("geo", "animations", "models", "blockstates", "textures", "particles", "lang"):
        shutil.rmtree(os.path.join(ASSETS, sub), ignore_errors=True)
    for sub in ("recipe", "loot_table"):
        shutil.rmtree(os.path.join(DATA, sub), ignore_errors=True)
    shutil.rmtree(os.path.join(DATA_MC, "tags"), ignore_errors=True)


def main():
    clean()
    write_blocks()
    write_weapons()
    write_items()
    write_particles()
    generate_animations()
    write_sounds()
    write_lang()
    write_data()
    validate()


if __name__ == "__main__":
    main()
