#!/usr/bin/env python3
"""Fireclay asset generator: blocks_core group (kiln, potters_wheel, bellows).

Writes textures, block models, blockstates and item model definitions under
src/main/resources/assets/fireclay/. Deterministic (fixed seeds).
Run from the fireclay/ project root:  PYTHONPATH=tools python3 tools/gen_blocks_core.py
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

from palette import (KILN_BRICK, FIRECLAY, RAW_CLAY, HEAT, IRON, OAK, LEATHER, STONE, OUTLINE, rgba)

ROOT = os.path.join("src", "main", "resources", "assets", "fireclay")
TEX = os.path.join(ROOT, "textures", "block")
MODELS = os.path.join(ROOT, "models")
BLOCKSTATES = os.path.join(ROOT, "blockstates")
ITEMS = os.path.join(ROOT, "items")
NS = "fireclay"

WRITTEN = []


# ---------------------------------------------------------------- helpers
def c3(c):
	return np.array(c[:3], dtype=np.float64)


def lerp(a, b, t):
	return c3(a) * (1.0 - t) + c3(b) * t


def scale(c, f):
	return np.clip(c3(c) * f, 0, 255)


def new_canvas(w=16, h=16):
	return np.zeros((h, w, 3), dtype=np.float64)


def to_image(arr):
	a = np.clip(np.rint(arr), 0, 255).astype(np.uint8)
	# no pure black anywhere
	a = np.maximum(a, 8)
	h, w, _ = a.shape
	alpha = np.full((h, w, 1), 255, dtype=np.uint8)
	return Image.fromarray(np.concatenate([a, alpha], axis=2), "RGBA")


def save_png(img, rel):
	path = os.path.join(TEX, rel + ".png")
	os.makedirs(os.path.dirname(path), exist_ok=True)
	img.save(path)
	WRITTEN.append(path)
	return path


def save_json(path, obj):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8") as f:
		json.dump(obj, f, indent=2)
		f.write("\n")
	WRITTEN.append(path)


def jitter(arr, rng, amount):
	"""Per-pixel brightness noise (multiplicative, quantized to keep it crisp)."""
	h, w, _ = arr.shape
	n = rng.randint(-1, 2, size=(h, w)).astype(np.float64)  # -1, 0, 1
	return np.clip(arr * (1.0 + n[..., None] * amount), 0, 255)


def tex(name):
	return f"{NS}:block/{name}"


# ---------------------------------------------------------------- kiln bricks
def brick_layout(offset=0):
	"""Return (mortar_mask, brick_id) for a 16x16 vanilla-like brick course layout.
	Courses 4 px tall (3 brick + 1 mortar), bricks 8 wide, alternate courses offset by 4."""
	mortar = np.zeros((16, 16), dtype=bool)
	bid = np.zeros((16, 16), dtype=np.int32)
	for y in range(16):
		course = y // 4
		if y % 4 == 3:
			mortar[y, :] = True
		shift = (4 if course % 2 else 0) + offset
		for x in range(16):
			xx = (x + shift) % 16
			if xx % 8 == 7:
				mortar[y, x] = True
			bid[y, x] = course * 4 + ((x + shift) % 16) // 8
	return mortar, bid


def brick_face(seed, soot_top=0.0, soot_base=0.0, darken=1.0, offset=0):
	"""Kiln brick face. soot_top: soot blend at the top row fading to soot_base at the bottom."""
	rng = np.random.RandomState(seed)
	mortar, bid = brick_layout(offset)
	arr = new_canvas()
	tones = [KILN_BRICK["base"], KILN_BRICK["base"], KILN_BRICK["shadow"], KILN_BRICK["highlight"]]
	brick_tone = {b: tones[rng.randint(0, len(tones))] for b in np.unique(bid)}
	for y in range(16):
		for x in range(16):
			if mortar[y, x]:
				arr[y, x] = c3(KILN_BRICK["mortar"]) * 0.84
				continue
			base = c3(brick_tone[bid[y, x]])
			# top-left light within each brick: top row of a brick lighter, bottom row darker
			row = y % 4
			if row == 0:
				base = lerp(base, KILN_BRICK["highlight"], 0.35)
			elif row == 2:
				base = lerp(base, KILN_BRICK["shadow"], 0.45)
			# right end of a brick (pixel before vertical joint) slightly darker
			if x < 15 and mortar[y, x + 1] and not mortar[y, :].all():
				base = lerp(base, KILN_BRICK["shadow"], 0.3)
			arr[y, x] = base
	arr = jitter(arr, rng, 0.06)
	# soot gradient
	for y in range(16):
		t = soot_base + (soot_top - soot_base) * (1.0 - y / 15.0) ** 1.4
		if t > 0:
			arr[y] = arr[y] * (1 - t) + c3(KILN_BRICK["soot"]) * t
	# random soot flecks
	fleck = rng.rand(16, 16) < 0.06 + soot_top * 0.12
	arr[fleck] = arr[fleck] * 0.55 + c3(KILN_BRICK["soot"]) * 0.45
	return arr * darken, mortar


def mouth_mask():
	"""Arched 6x6 mouth at x5..10, y7..12 (top corners cut for the arch)."""
	m = np.zeros((16, 16), dtype=bool)
	m[7:13, 5:11] = True
	m[7, 5] = m[7, 10] = False
	return m


def dilate(mask):
	out = mask.copy()
	out[1:, :] |= mask[:-1, :]
	out[:-1, :] |= mask[1:, :]
	out[:, 1:] |= mask[:, :-1]
	out[:, :-1] |= mask[:, 1:]
	# diagonals for a closed ring
	out[1:, 1:] |= mask[:-1, :-1]
	out[1:, :-1] |= mask[:-1, 1:]
	out[:-1, 1:] |= mask[1:, :-1]
	out[:-1, :-1] |= mask[1:, 1:]
	return out


def inner_depth(mask):
	"""Chebyshev-ish distance (in px) from the mask edge, 0 on the edge."""
	d = np.full(mask.shape, -1, dtype=np.int32)
	cur = mask.copy()
	level = 0
	while cur.any():
		er = cur.copy()
		er[1:, :] &= cur[:-1, :]
		er[:-1, :] &= cur[1:, :]
		er[:, 1:] &= cur[:, :-1]
		er[:, :-1] &= cur[:, 1:]
		er[0, :] = er[-1, :] = False
		er[:, 0] = er[:, -1] = False
		edge = cur & ~er
		d[edge] = level
		cur = er
		level += 1
	return d


def kiln_front(heat, lined):
	arr, _ = brick_face(1100, soot_top=0.40, soot_base=0.05)
	rng = np.random.RandomState(1200 + heat * 7 + (100 if lined else 0))
	mouth = mouth_mask()
	ring = dilate(mouth) & ~mouth
	ring2 = dilate(dilate(mouth)) & ~dilate(mouth)
	depth = inner_depth(mouth)
	ys, xs = np.nonzero(mouth)

	# soot plume above the mouth
	for y in range(0, 7):
		for x in range(4, 12):
			t = 0.45 * (1 - abs(x - 7.5) / 4.5) * (y / 6.0) ** 0.7
			arr[y, x] = arr[y, x] * (1 - t) + c3(KILN_BRICK["soot"]) * t

	# glow spill on the surrounding bricks
	glow = {0: None, 1: (HEAT["warm"], 0.25), 2: (HEAT["hot"], 0.30), 3: (HEAT["hot_light"], 0.35)}[heat]
	if glow is not None:
		col, strength = glow
		for y in range(16):
			for x in range(16):
				if mouth[y, x]:
					continue
				dy = np.abs(ys - y)
				dx = np.abs(xs - x)
				dist = np.min(np.maximum(dx, dy))
				if dist <= 3:
					t = strength * (1 - (dist - 1) / 3.0)
					arr[y, x] = arr[y, x] * (1 - t) + c3(col) * t

	# 1 px arch reveal around the mouth: dark jamb bricks (unlined) or buff fireclay rim (lined)
	for y, x in zip(*np.nonzero(ring)):
		if lined:
			# top-left light on the rim: upper/left pixels highlight, lower/right shadow
			if y <= 7 or x <= 4:
				c = lerp(FIRECLAY["base"], FIRECLAY["highlight"], 0.45)
			elif y >= 13 or x >= 11:
				c = c3(FIRECLAY["shadow"])
			else:
				c = c3(FIRECLAY["base"])
			if rng.rand() < 0.15:
				c = lerp(c, FIRECLAY["grog"], 0.5)
			if heat >= 2:
				c = lerp(c, HEAT["white"] if heat == 3 else HEAT["hot_light"], 0.18)
			arr[y, x] = c
		else:
			c = lerp(KILN_BRICK["shadow"], KILN_BRICK["soot"], 0.55)
			if y <= 6:
				c = lerp(KILN_BRICK["shadow"], KILN_BRICK["soot"], 0.35)  # voussoir top
			if heat >= 1:
				c = lerp(c, {1: HEAT["warm"], 2: HEAT["hot"], 3: HEAT["hot_light"]}[heat], 0.25 + 0.08 * heat)
			arr[y, x] = c
	# sill below the mouth: a lighter header brick row (2nd ring, bottom)
	for y, x in zip(*np.nonzero(ring2)):
		if y == 14 and 4 <= x <= 11:
			arr[y, x] = lerp(arr[y, x], KILN_BRICK["highlight"], 0.25)

	# mouth interior by heat
	for y, x in zip(ys, xs):
		d = depth[y, x]  # 0 edge, 1, 2 centre
		bottom = (y == 12)
		if heat == 0:
			c = c3(HEAT["cold"]) if d >= 1 else lerp(HEAT["cold"], KILN_BRICK["soot"], 0.35)
			if bottom:
				c = lerp(HEAT["cold"], rgba("#5A4A42"), 0.5 if (x % 2 == 0) else 0.25)  # cold ash
		elif heat == 1:
			c = [lerp(KILN_BRICK["soot"], HEAT["warm"], 0.45), c3(HEAT["warm"]), lerp(HEAT["warm"], rgba("#9A3A16"), 0.6)][min(d, 2)]
			if bottom:
				c = lerp(HEAT["warm"], rgba("#B4481C"), 0.7 if (x % 2) else 0.35)  # glowing coals
		elif heat == 2:
			c = [lerp(HEAT["warm"], HEAT["hot"], 0.55), c3(HEAT["hot"]), c3(HEAT["hot_light"])][min(d, 2)]
			if bottom:
				c = lerp(HEAT["hot_light"], rgba("#FFB347"), 0.8 if (x % 2) else 0.4)
		else:
			c = [lerp(HEAT["hot_light"], HEAT["white"], 0.5), c3(HEAT["white"]), c3(HEAT["core"])][min(d, 2)]
			if bottom:
				c = lerp(HEAT["white"], HEAT["core"], 0.6 if (x % 2) else 0.2)
		arr[y, x] = c
	# flicker: a couple of tongues of flame at heat 1-3
	if heat >= 1:
		tongue = {1: rgba("#9A3A16"), 2: rgba("#FFB347"), 3: HEAT["core"]}[heat]
		for (y, x) in [(10, 6), (9, 8), (10, 9)]:
			if mouth[y, x] and depth[y, x] >= 1:
				arr[y, x] = lerp(arr[y, x], tongue, 0.6)
	else:
		# a faint grate line across the dark mouth
		for x in range(6, 10):
			arr[11, x] = lerp(HEAT["cold"], IRON["shadow"], 0.25)
	return to_image(arr)


def kiln_side():
	arr, _ = brick_face(1001, soot_top=0.7, soot_base=0.0)
	return to_image(arr)


def kiln_bottom():
	arr, _ = brick_face(1002, soot_top=0.0, soot_base=0.0, darken=0.9, offset=2)
	return to_image(arr)


def kiln_top(open_):
	arr, _ = brick_face(1003, soot_top=0.35, soot_base=0.35, offset=0)
	rng = np.random.RandomState(1004)
	# soot darkening toward the vent
	for y in range(16):
		for x in range(16):
			d = max(abs(x - 7.5), abs(y - 7.5))
			if d <= 5:
				t = 0.45 * (1 - (d - 2) / 3.5)
				t = min(max(t, 0), 0.45)
				arr[y, x] = arr[y, x] * (1 - t) + c3(KILN_BRICK["soot"]) * t
	# 1 px frame of header bricks around the vent (x5..10, y5..10)
	for i in range(5, 11):
		for (y, x) in [(5, i), (10, i), (i, 5), (i, 10)]:
			arr[y, x] = lerp(KILN_BRICK["shadow"], KILN_BRICK["soot"], 0.5)
	if not open_:
		# closed iron damper plate over the 4x4 vent
		for y in range(6, 10):
			for x in range(6, 10):
				c = c3(IRON["shadow"]) * 0.82
				if y == 6 or x == 6:
					c = lerp(IRON["shadow"], IRON["base"], 0.55)
				if y == 9 or x == 9:
					c = c3(IRON["shadow"]) * 0.62
				arr[y, x] = c
		# hinge/handle bar across the middle, sooty
		arr[7, 7] = lerp(IRON["base"], IRON["highlight"], 0.3)
		arr[8, 8] = c3(IRON["shadow"]) * 0.55
	else:
		# open vent: deep hole with a glow ring
		hole = c3(rgba("#1E1410"))
		for y in range(6, 10):
			for x in range(6, 10):
				arr[y, x] = hole
		arr[8, 7] = c3(HEAT["warm"])
		arr[8, 8] = lerp(HEAT["warm"], HEAT["hot"], 0.4)
		arr[7, 8] = lerp(hole, HEAT["warm"], 0.5)
		for i in range(5, 11):
			for (y, x) in [(5, i), (10, i), (i, 5), (i, 10)]:
				corner = (y in (5, 10)) and (x in (5, 10))
				if corner:
					arr[y, x] = lerp(HEAT["warm"], KILN_BRICK["soot"], 0.2)
				elif y == 10 or x == 10:
					arr[y, x] = c3(HEAT["hot_light"])  # far rim lit from inside
				else:
					arr[y, x] = c3(HEAT["hot"])
		# faint warm halo on the bricks next to the rim
		for i in range(4, 12):
			for (y, x) in [(4, i), (11, i), (i, 4), (i, 11)]:
				arr[y, x] = lerp(arr[y, x], HEAT["hot"], 0.22)
	return to_image(arr)


# ---------------------------------------------------------------- potter's wheel
def oak_planks(seed, vertical=False, seams=(3, 6, 9, 12)):
	rng = np.random.RandomState(seed)
	arr = new_canvas()
	for y in range(16):
		for x in range(16):
			a, b = (x, y) if vertical else (y, x)  # a = across planks, b = along
			c = c3(OAK["base"])
			if a in seams:
				c = c3(OAK["deep"])
			elif (a - 1) in seams or a == 0:
				c = lerp(OAK["base"], OAK["highlight"], 0.6)
			arr[y, x] = c
	# grain streaks along the planks
	for _ in range(14):
		a = rng.randint(0, 16)
		b0 = rng.randint(0, 16)
		ln = rng.randint(2, 6)
		for k in range(ln):
			b = (b0 + k) % 16
			y, x = (b, a) if vertical else (a, b)
			if not np.allclose(arr[y, x], c3(OAK["deep"])):
				arr[y, x] = c3(OAK["shadow"])
	return jitter(arr, rng, 0.04)


def wheel_base():
	"""Oak frame. Top face: 3 px bevelled frame + planked deck; side faces use uv rows 0..2."""
	arr = oak_planks(2001, vertical=False, seams=(5, 8, 11))
	rng = np.random.RandomState(2002)
	for y in range(16):
		for x in range(16):
			e = min(x, y, 15 - x, 15 - y)
			if e <= 2:
				c = [lerp(OAK["base"], OAK["highlight"], 0.7), c3(OAK["base"]), c3(OAK["shadow"])][e]
				arr[y, x] = c
	# corner legs (end grain): 3x3 squares
	for (cy, cx) in [(0, 0), (0, 13), (13, 0), (13, 13)]:
		for y in range(cy, cy + 3):
			for x in range(cx, cx + 3):
				arr[y, x] = c3(OAK["deep"]) if (y - cy, x - cx) != (1, 1) else c3(OAK["shadow"])
		arr[cy, cx] = lerp(OAK["deep"], OAK["shadow"], 0.6)
	# pegs where the column meets the deck
	for (y, x) in [(5, 5), (5, 10), (10, 5), (10, 10)]:
		arr[y, x] = c3(OAK["deep"])
	return to_image(jitter(arr, rng, 0.03))


def wheel_column():
	"""Oak spindle with iron collars; sides use uv [6,4,10,11] (collars on rows 4 and 10)."""
	rng = np.random.RandomState(2101)
	arr = new_canvas()
	shade = [OAK["highlight"], OAK["base"], OAK["base"], OAK["shadow"]]
	for y in range(16):
		for x in range(16):
			c = c3(shade[(x - 6) % 4])
			if (x + y * 3) % 7 == 0:
				c = lerp(c, OAK["deep"], 0.5)
			arr[y, x] = c
	for y in (4, 10):
		for x in range(16):
			k = (x - 6) % 4
			arr[y, x] = [c3(IRON["highlight"]), c3(IRON["base"]), c3(IRON["base"]), c3(IRON["shadow"])][k]
	return to_image(jitter(arr, rng, 0.03))


def wheel_top(spoke_dir=0):
	"""Stone wheel head top: #A0A0A0 disc, concentric rings #7F7F7F, spoke mark #5E5E5E.
	spoke_dir: 0 north, 1 east, 2 south, 3 west (rotated by 90 deg steps)."""
	rng = np.random.RandomState(2201)
	arr = new_canvas()
	for y in range(16):
		for x in range(16):
			r = np.hypot(x - 7.5, y - 7.5)
			c = c3(STONE["base"])
			if 2.4 <= r < 3.3 or 5.0 <= r < 5.9:
				c = c3(STONE["ring"])
			elif r >= 6.6:
				c = lerp(STONE["base"], STONE["ring"], 0.55)  # worn outer edge
			if r < 1.6:
				c = c3(STONE["highlight"])  # polished hub
			# top-left light
			if x + y < 9 and 3.3 <= r < 5.0:
				c = lerp(c, STONE["highlight"], 0.35)
			if x + y > 21 and r < 6.6:
				c = lerp(c, STONE["ring"], 0.3)
			arr[y, x] = c
	arr = jitter(arr, rng, 0.035)
	# spoke mark pointing north from the hub to the rim (x 7..8, y 1..5)
	for y in range(1, 6):
		arr[y, 7] = c3(STONE["spoke"])
		arr[y, 8] = lerp(STONE["spoke"], STONE["ring"], 0.5)
	arr = np.rot90(arr, k=-spoke_dir).copy()  # clockwise steps
	return to_image(arr)


def wheel_head_side():
	"""Stone rim. Sides use uv rows 0..1 (highlight edge + shadow); bottom uses rows 2..15."""
	rng = np.random.RandomState(2301)
	arr = new_canvas()
	for y in range(16):
		for x in range(16):
			if y == 0:
				c = c3(STONE["highlight"])
			elif y == 1:
				c = c3(STONE["ring"])
				if x % 4 == 1:
					c = c3(STONE["spoke"])  # throwing notches
			else:
				c = lerp(STONE["base"], STONE["ring"], 0.35)
			arr[y, x] = c
	return to_image(jitter(arr, rng, 0.03))


def lump_texture(pal, seed, specks=None):
	rng = np.random.RandomState(seed)
	arr = new_canvas()
	for y in range(16):
		for x in range(16):
			c = c3(pal["base"])
			# throwing rings (horizontal ridges)
			if y % 3 == 0:
				c = lerp(pal["base"], pal["highlight"], 0.55)
			elif y % 3 == 2:
				c = lerp(pal["base"], pal["shadow"], 0.55)
			arr[y, x] = c
	arr = jitter(arr, rng, 0.04)
	# wet sheen on the upper-left
	sheen = pal.get("sheen", pal["highlight"])
	for (y, x) in [(5, 5), (5, 6), (6, 5), (9, 7), (4, 9)]:
		arr[y, x] = c3(sheen)
	if specks is not None:
		for _ in range(12):
			y, x = rng.randint(0, 16), rng.randint(0, 16)
			arr[y, x] = c3(specks)
	return to_image(arr)


# ---------------------------------------------------------------- bellows
def bellows_board():
	arr = oak_planks(3001, vertical=True, seams=(5, 10))
	for y in range(16):
		for x in range(16):
			e = min(x, y, 15 - x, 15 - y)
			if e == 0:
				arr[y, x] = c3(OAK["deep"]) if (x == 15 or y == 15) else lerp(OAK["shadow"], OAK["deep"], 0.4)
	# iron rivets
	for (y, x) in [(2, 2), (2, 13), (13, 2), (13, 13)]:
		arr[y, x] = c3(IRON["highlight"])
		arr[y + 1, x] = c3(IRON["shadow"])
	return to_image(arr)


def bellows_leather():
	"""Leather pleats (#7A4A2B / #5E3720) as vertical folds: lit ridge, flank, shadowed crease.
	Columns 0..8: relaxed pleats, period 3 (unpressed body uv [0,2,9,14]).
	Columns 10..13: compressed pleats, period 2 (pressed body uv [10,2,14,14]).
	Rows 2 and 13 (the body's top/bottom edges in uv) carry a stitched binding."""
	rng = np.random.RandomState(3101)
	arr = new_canvas()
	relaxed = [c3(LEATHER["highlight"]), c3(LEATHER["base"]), c3(LEATHER["shadow"])]
	crease = lerp(LEATHER["shadow"], OUTLINE, 0.35)
	for y in range(16):
		for x in range(16):
			if x <= 8:
				ph = x % 3
				c = relaxed[ph]
				# the crease line wanders by a pixel every few rows (zigzag fold)
				if ph == 2 and (y // 3) % 2 == 1:
					c = crease
			elif 10 <= x <= 13:
				c = lerp(LEATHER["base"], LEATHER["highlight"], 0.6) if x % 2 == 0 else crease
			else:
				c = c3(LEATHER["base"])
			arr[y, x] = c
	# stitched binding along the rows used as body edges (uv rows 2 and 13)
	for x in range(16):
		arr[2, x] = lerp(LEATHER["shadow"], LEATHER["highlight"], 0.25) if x % 2 else c3(LEATHER["shadow"])
		arr[13, x] = lerp(LEATHER["shadow"], OUTLINE, 0.45) if x % 2 else c3(LEATHER["shadow"])
	return to_image(jitter(arr, rng, 0.03))


def bellows_nozzle():
	"""Iron nozzle. North face uses uv [6,6,10,10] (2x2 bore at 7..8); sides use uv [0,0,3,4]/[0,0,4,3]."""
	rng = np.random.RandomState(3201)
	arr = new_canvas()
	for y in range(16):
		for x in range(16):
			c = c3(IRON["base"])
			if (x + 2 * y) % 5 == 0:
				c = lerp(IRON["base"], IRON["shadow"], 0.35)  # hammer marks
			arr[y, x] = c
	# side strips (uv [0,0,3,4] / [0,0,4,3]): lit top row, darker bottom row (symmetric across u)
	arr[0, 0:4] = lerp(IRON["base"], IRON["highlight"], 0.7)
	arr[1:3, 0:4] = c3(IRON["base"])
	arr[3, 0:4] = c3(IRON["shadow"])
	# bore
	arr[7:9, 7:9] = c3(OUTLINE)
	arr[6, 6:10] = c3(IRON["highlight"])
	arr[6:10, 6] = c3(IRON["highlight"])
	arr[9, 6:10] = c3(IRON["shadow"])
	arr[6:10, 9] = c3(IRON["shadow"])
	arr[6, 6] = c3(IRON["highlight"])
	return to_image(jitter(arr, rng, 0.03))


# ---------------------------------------------------------------- models
def face(t, uv=None, cull=None, rotation=None):
	f = {}
	if uv is not None:
		f["uv"] = uv
	if rotation:
		f["rotation"] = rotation
	f["texture"] = t
	if cull:
		f["cullface"] = cull
	return f


def cuboid(frm, to, faces):
	return {"from": frm, "to": to, "faces": faces}


def auto_uv(frm, to, side):
	"""Vanilla-style default UV for a face of a cuboid."""
	x1, y1, z1 = frm
	x2, y2, z2 = to
	if side in ("up", "down"):
		return [x1, z1, x2, z2]
	if side in ("north", "south"):
		return [16 - x2, 16 - y2, 16 - x1, 16 - y1] if side == "north" else [x1, 16 - y2, x2, 16 - y1]
	return [z1, 16 - y2, z2, 16 - y1] if side == "west" else [16 - z2, 16 - y2, 16 - z1, 16 - y1]


def boundary_cull(frm, to, side):
	x1, y1, z1 = frm
	x2, y2, z2 = to
	touch = {"down": y1 == 0, "up": y2 == 16, "north": z1 == 0, "south": z2 == 16, "west": x1 == 0, "east": x2 == 16}
	return side if touch[side] else None


SIDES = ("down", "up", "north", "south", "west", "east")


def box(frm, to, texmap, uvmap=None, skip=(), rot=None):
	"""texmap: side->texture ref; uvmap: side->uv (default auto)."""
	faces = {}
	for s in SIDES:
		if s in skip:
			continue
		uv = (uvmap or {}).get(s) or auto_uv(frm, to, s)
		faces[s] = face(texmap[s], uv=uv, cull=boundary_cull(frm, to, s), rotation=(rot or {}).get(s))
	return cuboid(frm, to, faces)


def all_sides(t):
	return {s: t for s in SIDES}


def model(path, obj):
	save_json(os.path.join(MODELS, path + ".json"), obj)


# wheel parts
WHEEL_BASE_ELEMS = [
	box([0, 0, 0], [16, 3, 16], all_sides("#base"),
		uvmap={"north": [0, 0, 16, 3], "south": [0, 0, 16, 3], "west": [0, 0, 16, 3], "east": [0, 0, 16, 3],
			"up": [0, 0, 16, 16], "down": [0, 0, 16, 16]}),
	box([6, 3, 6], [10, 10, 10], all_sides("#column"), skip=("down",),
		uvmap={"north": [6, 4, 10, 11], "south": [6, 4, 10, 11], "west": [6, 4, 10, 11], "east": [6, 4, 10, 11],
			"up": [6, 6, 10, 10]}),
]
SIDE_UV_HEAD = [1, 0, 15, 2]


def head_elem(top_ref):
	t = all_sides("#side")
	t["up"] = top_ref
	return box([1, 10, 1], [15, 12, 15], t,
		uvmap={"north": SIDE_UV_HEAD, "south": SIDE_UV_HEAD, "west": SIDE_UV_HEAD, "east": SIDE_UV_HEAD,
			"up": [1, 1, 15, 15], "down": [1, 2, 15, 16]})


LUMPS = {1: ((6, 12, 6), (10, 14, 10)), 2: ((5.5, 12, 5.5), (10.5, 15, 10.5)),
	3: ((5, 12, 5), (11, 16, 11)), 4: ((4.5, 12, 4.5), (11.5, 17, 11.5))}


def lump_elem(n):
	frm, to = LUMPS[n]
	w = to[0] - frm[0]
	h = to[1] - frm[1]
	side_uv = [8 - w / 2, 8 - h / 2, 8 + w / 2, 8 + h / 2]
	top_uv = [8 - w / 2, 8 - w / 2, 8 + w / 2, 8 + w / 2]
	faces = {}
	for s in ("up", "north", "south", "west", "east"):
		uv = top_uv if s == "up" else side_uv
		faces[s] = face("#clay", uv=[round(v, 2) for v in uv], cull="up" if (s == "up" and to[1] == 16) else None)
	# clay=3's top sits exactly on y=16 and culls against the block above; clay=4 pokes above y=16 (no cull)
	return cuboid(list(frm), list(to), faces)


def gen_models():
	# ---- kiln (16 models)
	for h in range(4):
		for lined in (False, True):
			for open_ in (False, True):
				name = f"kiln_h{h}" + ("_lined" if lined else "") + ("_open" if open_ else "")
				model(f"block/{name}", {
					"parent": "minecraft:block/orientable_with_bottom",
					"textures": {
						"front": tex(f"kiln_front_lined_h{h}" if lined else f"kiln_front_h{h}"),
						"side": tex("kiln_side"),
						"top": tex("kiln_top_open" if open_ else "kiln_top"),
						"bottom": tex("kiln_bottom"),
					},
				})
	# ---- potter's wheel
	model("block/potters_wheel_base", {
		"parent": "minecraft:block/block",
		"textures": {"particle": tex("potters_wheel_base"), "base": tex("potters_wheel_base"),
			"column": tex("potters_wheel_column")},
		"elements": WHEEL_BASE_ELEMS,
	})
	model("block/potters_wheel_head", {
		"parent": "minecraft:block/block",
		"textures": {"particle": tex("potters_wheel_head_side"), "top": tex("potters_wheel_top"),
			"side": tex("potters_wheel_head_side")},
		"elements": [head_elem("#top")],
	})
	model("block/potters_wheel_head_spinning", {
		"parent": "minecraft:block/block",
		"textures": {"particle": tex("potters_wheel_head_side"), "top": tex("potters_wheel_top_spinning"),
			"side": tex("potters_wheel_head_side")},
		"elements": [head_elem("#top")],
	})
	for n in range(1, 5):
		model(f"block/potters_wheel_lump_{n}", {
			"parent": "minecraft:block/block",
			"textures": {"particle": tex("wheel_clay"), "clay": tex("wheel_clay")},
			"elements": [lump_elem(n)],
		})
		model(f"block/potters_wheel_lump_fireclay_{n}", {
			"parent": "minecraft:block/block",
			"textures": {"particle": tex("wheel_fireclay"), "clay": tex("wheel_fireclay")},
			"elements": [lump_elem(n)],
		})
	# composed item model: base + column + static head
	model("item/potters_wheel", {
		"parent": "minecraft:block/block",
		"textures": {"particle": tex("potters_wheel_base"), "base": tex("potters_wheel_base"),
			"column": tex("potters_wheel_column"), "top": tex("potters_wheel_top"),
			"side": tex("potters_wheel_head_side")},
		"elements": WHEEL_BASE_ELEMS + [head_elem("#top")],
	})
	# ---- bellows (nozzle points NORTH = -Z)
	def bellows_elems(pressed):
		back_z = 9 if pressed else 14
		lz2 = back_z
		ldepth = lz2 - 5
		luv = [10, 2, 14, 14] if pressed else [0, 2, 9, 14]
		elems = [
			box([6, 6, 0], [10, 10, 3], all_sides("#nozzle"), skip=("south",),
				uvmap={"north": [6, 6, 10, 10], "west": [0, 0, 3, 4], "east": [0, 0, 3, 4],
					"up": [0, 0, 4, 3], "down": [0, 0, 4, 3]}),
			box([0, 0, 3], [16, 16, 5], all_sides("#board"),
				uvmap={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16], "west": [0, 0, 2, 16], "east": [0, 0, 2, 16],
					"up": [0, 0, 16, 2], "down": [0, 0, 16, 2]}),
			# leather body: relaxed pleat strip uv [0,2,9,14], compressed strip [10,2,14,14] when pressed;
			# top/bottom rotated so the folds run across the compression axis
			box([2, 2, 5], [14, 14, lz2], all_sides("#leather"), skip=("north", "south"),
				uvmap={s: luv for s in ("west", "east", "up", "down")},
				rot={"up": 90, "down": 90}),
			box([0, 0, back_z], [16, 16, back_z + 2], all_sides("#board"),
				uvmap={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16], "west": [0, 0, 2, 16], "east": [0, 0, 2, 16],
					"up": [0, 0, 16, 2], "down": [0, 0, 16, 2]}),
		]
		return elems

	for pressed in (False, True):
		model("block/bellows" + ("_pressed" if pressed else ""), {
			"parent": "minecraft:block/block",
			"textures": {"particle": tex("bellows_board"), "board": tex("bellows_board"),
				"leather": tex("bellows_leather"), "nozzle": tex("bellows_nozzle")},
			"elements": bellows_elems(pressed),
		})


def gen_blockstates():
	# kiln: 64 variants
	ys = {"north": 0, "east": 90, "south": 180, "west": 270}
	variants = {}
	for damper in ("false", "true"):
		for facing in ("east", "north", "south", "west"):
			for heat in range(4):
				for lined in ("false", "true"):
					m = f"{NS}:block/kiln_h{heat}" + ("_lined" if lined == "true" else "") + ("_open" if damper == "true" else "")
					v = {"model": m}
					if ys[facing]:
						v["y"] = ys[facing]
					variants[f"damper={damper},facing={facing},heat={heat},lined={lined}"] = v
	assert len(variants) == 64
	save_json(os.path.join(BLOCKSTATES, "kiln.json"), {"variants": variants})

	# bellows: 12 variants (powered does not change the model)
	rot = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
	variants = {}
	for facing in ("down", "east", "north", "south", "up", "west"):
		for pressed in ("false", "true"):
			v = {"model": f"{NS}:block/bellows" + ("_pressed" if pressed == "true" else "")}
			v.update(rot[facing])
			variants[f"facing={facing},pressed={pressed}"] = v
	assert len(variants) == 12
	save_json(os.path.join(BLOCKSTATES, "bellows.json"), {"variants": variants})

	# potters wheel: multipart
	parts = [
		{"apply": {"model": f"{NS}:block/potters_wheel_base"}},
		{"apply": {"model": f"{NS}:block/potters_wheel_head"}, "when": {"spinning": "false"}},
		{"apply": {"model": f"{NS}:block/potters_wheel_head_spinning"}, "when": {"spinning": "true"}},
	]
	for n in range(1, 5):
		parts.append({"apply": {"model": f"{NS}:block/potters_wheel_lump_{n}"}, "when": {"clay": str(n), "fireclay": "false"}})
		parts.append({"apply": {"model": f"{NS}:block/potters_wheel_lump_fireclay_{n}"}, "when": {"clay": str(n), "fireclay": "true"}})
	save_json(os.path.join(BLOCKSTATES, "potters_wheel.json"), {"multipart": parts})


def gen_items():
	for item_id, m in (("kiln", "block/kiln_h0"), ("bellows", "block/bellows"), ("potters_wheel", "item/potters_wheel")):
		save_json(os.path.join(ITEMS, item_id + ".json"), {"model": {"type": "minecraft:model", "model": f"{NS}:{m}"}})


def gen_textures():
	out = {}
	out["kiln_side"] = kiln_side()
	out["kiln_bottom"] = kiln_bottom()
	out["kiln_top"] = kiln_top(False)
	out["kiln_top_open"] = kiln_top(True)
	for h in range(4):
		out[f"kiln_front_h{h}"] = kiln_front(h, False)
		out[f"kiln_front_lined_h{h}"] = kiln_front(h, True)
	out["potters_wheel_base"] = wheel_base()
	out["potters_wheel_column"] = wheel_column()
	out["potters_wheel_top"] = wheel_top(0)
	out["potters_wheel_head_side"] = wheel_head_side()
	strip = Image.new("RGBA", (16, 64))
	for f in range(4):
		strip.paste(wheel_top(f), (0, 16 * f))
	out["potters_wheel_top_spinning"] = strip
	out["wheel_clay"] = lump_texture(RAW_CLAY, 2401)
	out["wheel_fireclay"] = lump_texture(FIRECLAY, 2402, specks=FIRECLAY["grog"])
	out["bellows_board"] = bellows_board()
	out["bellows_leather"] = bellows_leather()
	out["bellows_nozzle"] = bellows_nozzle()
	for name, img in out.items():
		assert img.mode == "RGBA" and img.getextrema()[3] == (255, 255), name
		save_png(img, name)
	mc = os.path.join(TEX, "potters_wheel_top_spinning.png.mcmeta")
	save_json(mc, {"animation": {"frametime": 2}})
	return out


def preview(out, path):
	from PIL import ImageFont
	names = list(out.keys())
	S = 16
	cell = 16 * S
	cols = 6
	rows = (len(names) + cols - 1) // cols
	pad = 20
	row_h = [max((out[n].size[1] * S) for n in names[r * cols:(r + 1) * cols]) + pad for r in range(rows)]
	sheet = Image.new("RGBA", (cols * (cell + 10) + 10, sum(row_h) + 10), (40, 44, 52, 255))
	d = ImageDraw.Draw(sheet)
	y0 = 5
	for r in range(rows):
		for c in range(cols):
			i = r * cols + c
			if i >= len(names):
				break
			n = names[i]
			im = out[n].resize((out[n].size[0] * S, out[n].size[1] * S), Image.NEAREST)
			x = 10 + c * (cell + 10)
			d.text((x, y0), n, fill=(230, 230, 230, 255))
			sheet.paste(im, (x, y0 + 14))
		y0 += row_h[r]
	os.makedirs(os.path.dirname(path), exist_ok=True)
	sheet.save(path)


def main():
	out = gen_textures()
	gen_models()
	gen_blockstates()
	gen_items()
	if len(sys.argv) > 1:
		preview(out, sys.argv[1])
	for p in WRITTEN:
		print(p)


if __name__ == "__main__":
	main()
