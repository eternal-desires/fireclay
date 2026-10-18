#!/usr/bin/env python3
"""Fireclay asset generator: items group.

Writes item textures (textures/item/<id>.png), item models (models/item/<id>.json,
parent minecraft:item/generated) and item model definitions (items/<id>.json)
under src/main/resources/assets/fireclay/. Deterministic (fixed seeds).
Run from the fireclay/ project root:  PYTHONPATH=tools python3 tools/gen_items.py
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image

from palette import (RAW_CLAY, FIRECLAY, TERRACOTTA, GLAZES, COPPER, GLASS, HEAT, OUTLINE, TRANSPARENT, rgba)

ROOT = os.path.join("src", "main", "resources", "assets", "fireclay")
TEX = os.path.join(ROOT, "textures", "item")
MODELS = os.path.join(ROOT, "models", "item")
ITEMS = os.path.join(ROOT, "items")
NS = "fireclay"

WRITTEN = []

# Local accent colours from design.md §3.3 (not in the shared palette).
SENTINEL_GLAZE = rgba("#E8902A")
SENTINEL_GLAZE_LIGHT = rgba("#F5B25A")
SENTINEL_GLAZE_DARK = rgba("#B8661A")
SENTINEL_CORE = rgba("#FFC24A")
NEEDLE = rgba("#D02020")
NEEDLE_DARK = rgba("#8E1414")
GLASS_LIGHT = rgba("#F2FBFF")
GLASS_SHADE = rgba("#A9C8D4")
COPPER_DEEP = rgba("#6A3519")


# ---------------------------------------------------------------- helpers
def shade(c, f):
	return (int(max(8, min(255, round(c[0] * f)))), int(max(8, min(255, round(c[1] * f)))),
		int(max(8, min(255, round(c[2] * f)))), c[3])


class Sprite:
	"""16x16 grid of RGBA tuples plus a per-pixel 'noise allowed' flag."""

	def __init__(self):
		self.px = [[TRANSPARENT] * 16 for _ in range(16)]
		self.noisy = [[False] * 16 for _ in range(16)]

	def set(self, x, y, color, noisy=True):
		if 0 <= x < 16 and 0 <= y < 16:
			self.px[y][x] = color
			self.noisy[y][x] = noisy

	def get(self, x, y):
		if 0 <= x < 16 and 0 <= y < 16:
			return self.px[y][x]
		return TRANSPARENT

	def filled(self, x, y):
		return self.get(x, y)[3] > 0


def from_ascii(rows, legend, quiet=""):
	"""rows: 16 strings of 16 chars; legend maps char -> RGBA; '.' is transparent.
	Characters in `quiet` are excluded from noise (glows, speculars, accents)."""
	assert len(rows) == 16, len(rows)
	s = Sprite()
	for y, row in enumerate(rows):
		assert len(row) == 16, (y, row, len(row))
		for x, ch in enumerate(row):
			if ch == ".":
				continue
			s.set(x, y, legend[ch], ch not in quiet)
	return s


def add_noise(s, seed, amount=0.05, chance=0.22):
	rng = random.Random(seed)
	for y in range(16):
		for x in range(16):
			r = rng.random()
			f = 1.0 + rng.uniform(-amount, amount)
			if s.noisy[y][x] and s.filled(x, y) and r < chance:
				s.px[y][x] = shade(s.px[y][x], f)


def add_outline(s, color=OUTLINE):
	"""1px outline on every transparent pixel 4-adjacent to the shape."""
	edge = []
	for y in range(16):
		for x in range(16):
			if s.filled(x, y):
				continue
			if any(s.filled(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
				edge.append((x, y))
	for x, y in edge:
		s.set(x, y, color, False)


def speckle(s, color, seed, chance, allowed):
	"""Scatter single specks on pixels whose colour is in `allowed`."""
	rng = random.Random(seed)
	for y in range(16):
		for x in range(16):
			r = rng.random()
			if s.get(x, y) in allowed and r < chance:
				s.set(x, y, color, False)


def to_image(s):
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	for y in range(16):
		for x in range(16):
			c = s.px[y][x]
			if c[3] == 0:
				continue
			img.putpixel((x, y), (max(c[0], 8), max(c[1], 8), max(c[2], 8), 255))
	return img


def write_json(path, data):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(data, f, indent=2)
		f.write("\n")
	WRITTEN.append(path)


def emit(item_id, sprite, seed, noise=True):
	if noise:
		add_noise(sprite, seed)
	add_outline(sprite)
	img = to_image(sprite)
	path = os.path.join(TEX, item_id + ".png")
	os.makedirs(os.path.dirname(path), exist_ok=True)
	img.save(path)
	WRITTEN.append(path)
	write_json(os.path.join(MODELS, item_id + ".json"), {
		"parent": "minecraft:item/generated",
		"textures": {"layer0": f"{NS}:item/{item_id}"},
	})
	write_json(os.path.join(ITEMS, item_id + ".json"), {
		"model": {"type": "minecraft:model", "model": f"{NS}:item/{item_id}"},
	})
	return img


def clay_legend(p):
	return {"W": p.get("sheen", p["highlight"]), "H": p["highlight"], "b": p["base"], "s": p["shadow"], "d": p["deep"]}


# ---------------------------------------------------------------- greenware
def unfired_tile():
	s = Sprite()
	p = RAW_CLAY
	for y in range(2, 14):
		for x in range(2, 14):
			if y >= 12:  # front edge of the slab (thickness)
				c = p["shadow"] if y == 12 else p["deep"]
				if x == 2 and y == 12:
					c = p["base"]
			elif y == 2 or x == 2:
				c = p["highlight"]
			elif x == 13:
				c = p["shadow"]
			else:
				c = p["base"]
			s.set(x, y, c)
	s.set(3, 3, p["sheen"], False)
	s.set(4, 3, p["sheen"], False)
	s.set(3, 4, p["sheen"], False)
	# thumbprint (#7E8496): concave press, dark upper-left wall, lit lower-right lip, ridges inside
	thumb = [
		"..sss..",
		".s...s.",
		"s.sss.H",
		"s.s..sH",
		"s..ss.H",
		".H...H.",
		"..HHH..",
	]
	ox, oy = 5, 4
	for j, row in enumerate(thumb):
		for i, ch in enumerate(row):
			if ch == "s":
				s.set(ox + i, oy + j, p["shadow"], False)
			elif ch == "H":
				s.set(ox + i, oy + j, p["highlight"], False)
	return s


BELL = [
	"................",
	"................",
	".......Hs.......",
	"......HWbs......",
	".....HWbbbs.....",
	"....HWbbbbbs....",
	"....HWbbbbbs....",
	"....HWbbbbbs....",
	"....HWbbbbss....",
	"....HWbbbbss....",
	"...HWbbbbbbss...",
	"..HWbbbbbbbbss..",
	"..HHHHHHHHHHHs..",
	"...ssssddddd....",
	".......dd.......",
	"................",
]

JAR = [
	"................",
	"................",
	".....HHHHHHs....",
	".....sbbbbsd....",
	"......sbbs......",
	".....HWbbbs.....",
	"....HWbbbbbs....",
	"...HWbbbbbbbs...",
	"...HHHHHHHHss...",
	"...Wbbbbbbbbs...",
	"...HHHHHHHHss...",
	"...Hbbbbbbbss...",
	"....sbbbbbsd....",
	".....ssssdd.....",
	"................",
	"................",
]

URN = [
	"................",
	"....HHHHHHHs....",
	".....sbbbbsd....",
	"......Hbbs......",
	"..HbbHHbbsbbbs..",
	"..H..HWbbbs..s..",
	"..H.HWbbbbbs.s..",
	"..HHWbbbbbbbss..",
	"..HWbbbbbbbbbs..",
	"..HWsssssssssd..",
	"...HWbbbbbbbs...",
	"...Hbbbbbbbss...",
	"....Hbbbbbss....",
	".....sbbbsd.....",
	"....HHbbbsdd....",
	"................",
]

# Shared figurine template for the effigy (greenware) and the sentinel effigy (fired).
# O/o = shoulders (glazed on the sentinel), C = chest pixel, E = eyes.
EFFIGY = [
	"................",
	".......Hs.......",
	"......HHbs......",
	"......Hbbs......",
	"......HEbE......",
	".......bs.......",
	"..PPOHbbbbsOOo..",
	"..Hb.HbCbbs.bs..",
	"..Hb.Hbbbbs.bs..",
	"..ss.Hbbbbs.sd..",
	".....Hbbbbs.....",
	".....Hbbbss.....",
	".....Hb..bs.....",
	".....Hb..bs.....",
	"....Hbs..bss....",
	"................",
]


def raw_greenware(rows):
	return from_ascii(rows, clay_legend(RAW_CLAY), quiet="W")


def unfired_effigy():
	p = FIRECLAY
	leg = clay_legend(p)
	leg.update({"O": p["base"], "P": p["highlight"], "o": p["shadow"], "C": p["base"], "E": p["deep"]})
	s = from_ascii(EFFIGY, leg)
	speckle(s, p["grog"], 9101, 0.14, {p["base"], p["shadow"]})
	return s


def sentinel_effigy():
	p = TERRACOTTA
	leg = clay_legend(p)
	leg.update({"O": SENTINEL_GLAZE, "P": SENTINEL_GLAZE_LIGHT, "o": SENTINEL_GLAZE_DARK, "C": SENTINEL_CORE, "E": p["deep"]})
	s = from_ascii(EFFIGY, leg, quiet="OPoC")
	# glaze drips running down the arm tops from the shoulders
	s.set(2, 7, SENTINEL_GLAZE_LIGHT, False)
	s.set(3, 7, SENTINEL_GLAZE, False)
	s.set(12, 7, SENTINEL_GLAZE, False)
	s.set(13, 7, SENTINEL_GLAZE_DARK, False)
	# crest glazed too, so the head reads as part of the same fired piece
	s.set(7, 1, SENTINEL_GLAZE_LIGHT, False)
	s.set(8, 1, SENTINEL_GLAZE, False)
	return s


# ---------------------------------------------------------------- slips
SLIP_BOWL = [
	"................",
	"................",
	"................",
	"................",
	"....RRRRRRRR....",
	"..RRGGGGGGGGRR..",
	".RGGLLGGGGGGGGR.",
	".RGLAAGGGGGGgGR.",
	".RGGLGGGGGGgggR.",
	".RgGGGGGGGggggR.",
	".HHHHHHHHHHHHHs.",
	"..Hbbbbbbbbbbs..",
	"...Hbbbbbbbbs...",
	"....sssssssd....",
	".....dddddd.....",
	"................",
]


def slip(glaze):
	g = GLAZES[glaze]
	p = RAW_CLAY
	rows = list(SLIP_BOWL)
	leg = {"R": p["shadow"], "H": p["highlight"], "b": p["base"], "s": p["shadow"], "d": p["deep"],
		"G": g["base"], "L": g["highlight"], "A": g["accent"], "g": g["shadow"]}
	s = from_ascii(rows, leg, quiet="AL")
	if glaze == "echo":
		s.set(9, 6, g["accent"], False)
		s.set(11, 8, g["accent"], False)
	if glaze == "glowink":
		s.set(10, 6, g["highlight"], False)
	return s


# ---------------------------------------------------------------- materials
def kiln_shard():
	t = TERRACOTTA
	leg = {"H": t["highlight"], "b": t["base"], "s": t["shadow"], "d": t["deep"],
		"L": GLAZES["lapis"]["base"], "l": GLAZES["lapis"]["highlight"],
		"M": GLAZES["amethyst"]["base"], "m": GLAZES["amethyst"]["highlight"],
		"Z": GLAZES["blaze"]["base"], "z": GLAZES["blaze"]["highlight"]}
	rows = [
		"................",
		".HHH............",
		".HbbHH..........",
		".HlLbbs.....HH..",
		"..LLbss....HbbH.",
		"..Hbbs....HbmMs.",
		"...bs.....HbMbs.",
		"...s......Hbbs..",
		"...........bs...",
		"................",
		".....HH.........",
		"...HHbbH........",
		"..HzZbbbbH......",
		"..HbZbbbbbbs....",
		"...sbbssssdd....",
		"................",
	]
	return from_ascii(rows, leg, quiet="LlMmZz")


def grog():
	"""Granule pile: each pixel is a grain; lit grains upper-left, dark gaps lower-right."""
	t = TERRACOTTA
	cream = FIRECLAY["highlight"]
	rng = random.Random(4242)
	s = Sprite()
	cx, base_row, hw, ht = 7.5, 13, 6.6, 7.5
	tops = {}
	for x in range(1, 15):
		dx = (x + 0.5 - cx) / hw
		if abs(dx) >= 1:
			continue
		tops[x] = int(round(base_row - ht * (1 - dx * dx) ** 0.75)) + rng.choice((0, 0, 0, 1))
	for x, top in tops.items():
		for y in range(top, base_row + 1):
			depth = y - top
			light = (cx - (x + 0.5)) / hw * 0.5 + (0.6 if depth == 0 else 0.25 if depth == 1 else -0.1) - (y - 9) * 0.05
			v = light + rng.uniform(-0.45, 0.45)
			if rng.random() < 0.09 and y < base_row:
				c = cream
			elif v > 0.45:
				c = t["highlight"]
			elif v > 0.0:
				c = t["base"]
			elif v > -0.4:
				c = t["shadow"]
			else:
				c = t["deep"]
			s.set(x, y, c, False)
	return s


def fireclay_ball():
	p = FIRECLAY
	s = Sprite()
	cx, cy, r = 8.0, 8.4, 5.6
	lx, ly, lz = -0.55, -0.6, 0.58
	for y in range(16):
		for x in range(16):
			dx, dy = x + 0.5 - cx, y + 0.5 - cy
			ang = math.atan2(dy, dx)
			rr = r + 0.45 * math.sin(3 * ang + 2.2) + 0.2 * math.sin(5 * ang + 0.4)
			d = math.hypot(dx, dy)
			if d > rr:
				continue
			nx, ny = dx / rr, dy / rr
			nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
			i = nx * lx + ny * ly + nz * lz
			if i > 0.86:
				c = p["highlight"]
			elif i > 0.45:
				c = p["base"]
			elif i > 0.1:
				c = p["shadow"]
			else:
				c = p["deep"]
			s.set(x, y, c)
	speckle(s, p["grog"], 5150, 0.12, {p["base"], p["shadow"]})
	return s


def fireclay_brick():
	p = FIRECLAY
	s = Sprite()
	x0, x1, y0, y1, depth = 1, 11, 8, 12, 3
	for y in range(y0, y1 + 1):  # front face
		for x in range(x0, x1 + 1):
			c = p["base"]
			if y == y1:
				c = p["shadow"]
			if x == x0 and y < y1:
				c = p["highlight"]
			s.set(x, y, c)
	for k in range(1, depth + 1):  # top face
		for x in range(x0 + k, x1 + k + 1):
			s.set(x, y0 - k, p["highlight"])
	for k in range(1, depth + 1):  # right face
		for y in range(y0 - k + 1, y1 - k + 1):
			s.set(x1 + k, y, p["shadow"] if y < y1 - k else p["deep"])
	# crisp edges: the near top edge catches the light, the far vertical edge is a step darker
	for x in range(x0, x1 + 1):
		s.set(x, y0, p["highlight"], False)
	for x in range(x0 + 1, x1 + 1):
		s.set(x, y0, p["base"] if (x % 4 == 0) else p["highlight"])
	s.set(x1 + 1, y0, p["shadow"], False)
	speckle(s, p["grog"], 6161, 0.10, {p["base"]})
	speckle(s, p["deep"], 6162, 0.08, {p["shadow"]})
	return s


def kiln_lining():
	p = FIRECLAY
	z = GLAZES["blaze"]
	s = Sprite()
	cells = ((1, 4), (6, 9), (11, 14))
	for y in range(1, 15):
		for x in range(1, 15):
			s.set(x, y, p["deep"])
	for cy, (ya, yb) in enumerate(cells):
		for cx, (xa, xb) in enumerate(cells):
			for y in range(ya, yb + 1):
				for x in range(xa, xb + 1):
					if y == ya or x == xa:
						c = p["highlight"]
					elif y == yb or x == xb:
						c = p["shadow"]
					else:
						c = p["base"]
					s.set(x, y, c)
	speckle(s, p["grog"], 7171, 0.12, {p["base"]})
	# blaze seam: the mortar ring around the centre brick glows, hottest at the lower-right
	for i in range(5, 11):
		for (x, y) in ((i, 5), (5, i), (i, 10), (10, i)):
			s.set(x, y, z["base"], False)
	for (x, y) in ((6, 5), (7, 5), (5, 6), (5, 7)):
		s.set(x, y, z["highlight"], False)
	for (x, y) in ((10, 9), (9, 10), (10, 10)):
		s.set(x, y, z["shadow"], False)
	# heat spill: the centre brick's inner edge warms up
	s.set(6, 6, z["accent"], False)
	# outer border darkens slightly so the slab reads as a tile, not a window
	for i in range(1, 15):
		s.set(i, 14, shade(s.get(i, 14), 0.9), False)
		s.set(14, i, shade(s.get(14, i), 0.9), False)
	return s


def pyrometer():
	s = Sprite()
	cx, cy, R, r_in = 8.0, 7.5, 6.2, 4.25
	for y in range(16):
		for x in range(16):
			dx, dy = x + 0.5 - cx, y + 0.5 - cy
			d = math.hypot(dx, dy)
			if d > R:
				continue
			light = -(dx + dy) / (d + 1e-6)  # +1 at upper-left, -1 at lower-right
			if d > r_in:
				if light > 0.45:
					c = COPPER["highlight"]
				elif light > -0.35:
					c = COPPER["base"]
				else:
					c = COPPER["shadow"]
				s.set(x, y, c)
			else:
				s.set(x, y, GLASS, False)
	# glass shading: a shaded inner rim lower-right, a glint upper-left
	for (x, y) in ((10, 11), (11, 10), (11, 9), (9, 11), (8, 11)):
		s.set(x, y, GLASS_SHADE, False)
	for (x, y) in ((5, 4), (6, 4), (5, 5)):
		s.set(x, y, GLASS_LIGHT, False)
	# scale: cool ticks on the left, the hot band at the upper right where the needle points
	for (x, y) in ((5, 9), (5, 7)):
		s.set(x, y, GLASS_SHADE, False)
	for (x, y) in ((10, 4), (11, 5)):
		s.set(x, y, HEAT["hot"], False)
	s.set(9, 3, HEAT["warm"], False)
	# needle from the hub pointing to the hot (upper-right) end
	for (x, y) in ((8, 7), (9, 6), (10, 5)):
		s.set(x, y, NEEDLE, False)
	s.set(9, 5, NEEDLE_DARK, False)
	s.set(7, 8, COPPER_DEEP, False)
	s.set(8, 8, NEEDLE_DARK, False)
	# probe stem below the dial
	s.set(7, 14, COPPER["base"])
	s.set(8, 14, COPPER["shadow"])
	return s


# ---------------------------------------------------------------- main
def build_all():
	sprites = [
		("unfired_tile", unfired_tile()),
		("unfired_bell", raw_greenware(BELL)),
		("unfired_jar", raw_greenware(JAR)),
		("unfired_urn", raw_greenware(URN)),
		("unfired_effigy", unfired_effigy()),
		("lapis_slip", slip("lapis")),
		("glowink_slip", slip("glowink")),
		("amethyst_slip", slip("amethyst")),
		("blaze_slip", slip("blaze")),
		("echo_slip", slip("echo")),
		("kiln_shard", kiln_shard()),
		("grog", grog()),
		("fireclay_ball", fireclay_ball()),
		("fireclay_brick", fireclay_brick()),
		("kiln_lining", kiln_lining()),
		("sentinel_effigy", sentinel_effigy()),
		("pyrometer", pyrometer()),
	]
	images = []
	for n, (item_id, sprite) in enumerate(sprites):
		images.append((item_id, emit(item_id, sprite, 1000 + n)))
	return images


def preview(images, path):
	from PIL import ImageDraw
	scale_px, cols, pad, label_h = 16, 6, 8, 14
	cell = 16 * scale_px
	rows = (len(images) + cols - 1) // cols
	sheet = Image.new("RGBA", (cols * (cell + pad) + pad, rows * (cell + pad + label_h) + pad), (60, 60, 70, 255))
	dr = ImageDraw.Draw(sheet)
	for i, (name, img) in enumerate(images):
		cx, cy = i % cols, i // cols
		x = pad + cx * (cell + pad)
		y = pad + cy * (cell + pad + label_h)
		bg = Image.new("RGBA", (cell, cell), (139, 139, 139, 255))
		big = img.resize((cell, cell), Image.NEAREST)
		bg.alpha_composite(big)
		sheet.paste(bg, (x, y))
		dr.text((x, y + cell + 1), name, fill=(240, 240, 240, 255))
	os.makedirs(os.path.dirname(path), exist_ok=True)
	sheet.save(path)


if __name__ == "__main__":
	imgs = build_all()
	pv = os.environ.get("FIRECLAY_PREVIEW")
	if pv:
		preview(imgs, pv)
	for p in WRITTEN:
		print(p)
