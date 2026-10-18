#!/usr/bin/env python3
"""Fireclay asset generator: blocks_ceramics group.

Blocks: fired_tiles (+stairs, slab), lumen_tiles, storage_jar, memory_jar, sentry_chime,
vigil_urn, ember_tile, reliquary_urn, fireclay_bricks (+stairs, slab, wall).

Writes textures, block models, blockstates and item model definitions under
src/main/resources/assets/fireclay/. Deterministic (fixed seeds).
Run from the fireclay/ project root:
    PYTHONPATH=tools python3 tools/gen_blocks_ceramics.py [--preview DIR]
With --preview, an upscaled labelled texture sheet and a model render sheet are written to DIR.
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from palette import (TERRACOTTA, FIRECLAY, GLAZES, GOLD, IRON, MEMORY_VEIN, rgba)

ROOT = os.path.join("src", "main", "resources", "assets", "fireclay")
TEX = os.path.join(ROOT, "textures", "block")
MODELS = os.path.join(ROOT, "models", "block")
BLOCKSTATES = os.path.join(ROOT, "blockstates")
ITEMS = os.path.join(ROOT, "items")
NS = "fireclay"

WRITTEN = []
TEXTURES = {}   # name -> PIL image (for previews)
MODEL_DEFS = {}  # name -> model dict (for previews)


# ---------------------------------------------------------------- colour helpers
def C(c):
	if isinstance(c, str):
		c = rgba(c)
	return np.array(c[:3], dtype=np.float64)


def mix(a, b, t):
	return C(a) * (1.0 - t) + C(b) * t


def canvas(color):
	arr = np.zeros((16, 16, 3), dtype=np.float64)
	arr[:, :] = C(color)
	return arr


def speckle(arr, rng, amount=0.035, mask=None):
	"""Quantized multiplicative noise (-1/0/+1 steps) so pixels stay crisp."""
	n = rng.choice([-1.0, 0.0, 0.0, 1.0], size=arr.shape[:2])
	if mask is not None:
		n = n * mask
	return np.clip(arr * (1.0 + n[..., None] * amount), 0, 255)


def to_image(arr):
	a = np.clip(np.rint(arr), 0, 255).astype(np.uint8)
	a = np.maximum(a, 10)  # never pure black
	alpha = np.full(a.shape[:2] + (1,), 255, dtype=np.uint8)
	return Image.fromarray(np.concatenate([a, alpha], axis=2), "RGBA")


def save_tex(name, arr):
	img = to_image(arr)
	path = os.path.join(TEX, name + ".png")
	os.makedirs(os.path.dirname(path), exist_ok=True)
	img.save(path)
	WRITTEN.append(path)
	TEXTURES[name] = img
	return img


def save_json(path, obj):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8") as f:
		json.dump(obj, f, indent=2)
		f.write("\n")
	WRITTEN.append(path)


def tex(name):
	return f"{NS}:block/{name}"


def mdl(name):
	return f"{NS}:block/{name}"


# ---------------------------------------------------------------- tile faces (4 tiles of 7x7, 1 px grout)
def tile_index(x, y):
	if x % 8 == 7 or y % 8 == 7:
		return -1
	return (y // 8) * 2 + (x // 8)


def tile_face(rng, hues, grout, hl, sh, hl_amt=0.38, sh_amt=0.42, noise=0.03):
	arr = np.zeros((16, 16, 3))
	for y in range(16):
		for x in range(16):
			t = tile_index(x, y)
			if t < 0:
				g = C(grout)
				if x % 8 == 7 and y % 8 == 7:
					g = g * 0.88
				arr[y, x] = g
				continue
			lx, ly = x % 8, y % 8
			c = C(hues[t])
			top_left = lx == 0 or ly == 0
			bot_right = lx == 6 or ly == 6
			if top_left and not bot_right:
				c = mix(c, hl, hl_amt)
			elif bot_right and not top_left:
				c = mix(c, sh, sh_amt)
			elif top_left and bot_right:
				c = mix(c, sh, sh_amt * 0.3)
			arr[y, x] = c
	tile_mask = np.array([[1.0 if tile_index(x, y) >= 0 else 0.4 for x in range(16)] for y in range(16)])
	return speckle(arr, rng, noise, tile_mask)


def random_walk(rng, start, steps, tile, allow_grout=False):
	"""A crack path: mostly diagonal/orthogonal steps that keep a heading."""
	x, y = start
	pts = [(x, y)]
	dirs = [(1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1)]
	d = rng.randint(0, 8)
	for _ in range(steps):
		d = (d + rng.choice([-1, 0, 0, 1])) % 8
		dx, dy = dirs[d]
		nx, ny = x + dx, y + dy
		if not (0 <= nx < 16 and 0 <= ny < 16):
			d = (d + 4) % 8
			continue
		if not allow_grout and tile_index(nx, ny) != tile:
			d = (d + rng.choice([2, 3, 5, 6])) % 8
			continue
		x, y = nx, ny
		pts.append((x, y))
	return pts


def clean_walk(rng, start, steps, tile, avoid=()):
	"""A thin crack: never touches itself (8-neighbourhood) except at the previous pixel."""
	dirs = [(1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1)]
	pts = [start]
	taken = set(avoid)
	d = rng.randint(0, 8)
	for _ in range(steps):
		cands = [d, (d + 1) % 8, (d - 1) % 8]
		if rng.rand() < 0.4:
			cands[0], cands[1] = cands[1], cands[0]
		moved = False
		for nd in cands:
			x, y = pts[-1]
			nx, ny = x + dirs[nd][0], y + dirs[nd][1]
			if not (0 <= nx < 16 and 0 <= ny < 16) or tile_index(nx, ny) != tile or (nx, ny) in pts:
				continue
			near = [(px, py) for (px, py) in pts[:-1] + list(taken) if abs(px - nx) <= 1 and abs(py - ny) <= 1]
			if near:
				continue
			pts.append((nx, ny))
			d = nd
			moved = True
			break
		if not moved:
			break
	return pts


def tile_origin(t):
	return (t % 2) * 8, (t // 2) * 8


def gen_fired_tiles():
	rng = np.random.RandomState(1101)
	hues = ["#B5603A", "#BE6A41", "#AD5935", "#B86440"]
	arr = tile_face(rng, hues, TERRACOTTA["grout"], TERRACOTTA["highlight"], TERRACOTTA["shadow"], hl_amt=0.7, sh_amt=0.6, noise=0.025)
	for t in range(4):
		ox, oy = tile_origin(t)
		n = 2 + rng.randint(0, 2)
		used = set()
		while len(used) < n:
			lx, ly = rng.randint(1, 6), rng.randint(1, 6)
			if (lx, ly) in used:
				continue
			used.add((lx, ly))
			arr[oy + ly, ox + lx] = C(TERRACOTTA["highlight"])
		# a faint darker kiln-flash pixel
		lx, ly = rng.randint(2, 6), rng.randint(2, 6)
		if (lx, ly) not in used:
			arr[oy + ly, ox + lx] = mix(arr[oy + ly, ox + lx], TERRACOTTA["shadow"], 0.5)
	save_tex("fired_tiles", arr)


def gen_lumen_tiles():
	rng = np.random.RandomState(1202)
	hues = ["#E8F4F0", "#E3F1EC", "#ECF7F3", "#E5F2EE"]
	arr = tile_face(rng, hues, "#9FC9C1", "#F7FFFD", "#C4DDD7", hl_amt=0.6, sh_amt=0.55, noise=0.015)
	deep, glow = C(GLAZES["glowink"]["base"]), C(GLAZES["glowink"]["highlight"])
	for t in range(4):
		ox, oy = tile_origin(t)
		for k in range(2):
			edge = rng.randint(0, 4)
			p = rng.randint(1, 6)
			start = [(ox + p, oy), (ox + 6, oy + p), (ox + p, oy + 6), (ox, oy + p)][edge]
			pts = random_walk(rng, start, 4 + rng.randint(0, 3), t)
			for i, (x, y) in enumerate(pts):
				arr[y, x] = deep if (i % 3 == 0 or i == len(pts) - 1) else glow
				if i == 0:
					arr[y, x] = deep
	save_tex("lumen_tiles", arr)


def gen_ember_tile():
	rng = np.random.RandomState(1303)
	bl = GLAZES["blaze"]
	hues = ["#C2410C", "#B93D0B", "#C9480F", "#BE430D"]
	arr = tile_face(rng, hues, bl["deep"], "#D4520F", "#97330A", hl_amt=0.45, sh_amt=0.6, noise=0.04)
	crack = np.zeros((16, 16), dtype=np.int32)
	dirs = [(1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1)]
	# a seamless (wrapping) network of jagged fissures running across tiles and grout
	for (sx, sy, base, length) in ((3, 0, 1, 13), (12, 2, 2, 11), (0, 11, 0, 6)):
		x, y = sx, sy
		for i in range(length):
			crack[y, x] = 2 if i in (length // 2, 2) else max(crack[y, x], 1)
			dx, dy = dirs[(base + rng.choice([-1, 0, 1])) % 8]
			x, y = (x + dx) % 16, (y + dy) % 16
	halo = np.zeros((16, 16), dtype=bool)
	for y in range(16):
		for x in range(16):
			if crack[y, x]:
				for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
					nx, ny = x + dx, y + dy
					if 0 <= nx < 16 and 0 <= ny < 16 and not crack[ny, nx]:
						halo[ny, nx] = True
	for y in range(16):
		for x in range(16):
			if crack[y, x] == 2:
				arr[y, x] = C(bl["accent"])
			elif crack[y, x] == 1:
				arr[y, x] = C(bl["highlight"])
			elif halo[y, x]:
				arr[y, x] = mix(arr[y, x], "#97330A", 0.35) if tile_index(x, y) >= 0 else arr[y, x]
	save_tex("ember_tile", arr)


def gen_fireclay_bricks():
	rng = np.random.RandomState(1404)
	mortar_c = C("#9E8A66")
	tones = ["#D9C49A", "#C8B184", "#D9C49A", "#D2BC90"]
	arr = np.zeros((16, 16, 3))
	brick_tone = {}
	for y in range(16):
		course = y // 4
		shift = 4 if course % 2 else 0
		for x in range(16):
			xx = (x + shift) % 16
			if y % 4 == 3 or xx % 8 == 7:
				arr[y, x] = mortar_c * (0.9 if (y % 4 == 3 and xx % 8 == 7) else 1.0)
				continue
			bid = (course, xx // 8)
			if bid not in brick_tone:
				brick_tone[bid] = tones[rng.randint(0, len(tones))]
			c = C(brick_tone[bid])
			row = y % 4
			col = xx % 8
			if row == 0:
				c = mix(c, FIRECLAY["highlight"], 0.45)
			elif row == 2:
				c = mix(c, FIRECLAY["shadow"], 0.45)
			if col == 0 and row != 2:
				c = mix(c, FIRECLAY["highlight"], 0.25)
			if col == 6:
				c = mix(c, FIRECLAY["shadow"], 0.35)
			arr[y, x] = c
	arr = speckle(arr, rng, 0.025)
	# grog specks
	for bid in sorted(brick_tone):
		course, b = bid
		shift = 4 if course % 2 else 0
		for _ in range(1 + rng.randint(0, 2)):
			col = rng.randint(1, 6)
			row = rng.randint(0, 3)
			x = (b * 8 + col - shift) % 16
			y = course * 4 + row
			arr[y, x] = C(FIRECLAY["grog"]) if rng.rand() < 0.6 else C(FIRECLAY["deep"])
	save_tex("fireclay_bricks", arr)


# ---------------------------------------------------------------- vessel helpers
def col_shade(x, x0, w):
	"""Cylindrical shading across a face of width w starting at column x0 (light from the left)."""
	p = (x - x0 + 0.5) / w
	if p < 0:
		p = 0.0
	if p > 1:
		p = 1.0
	if p < 0.12:
		return ("hl", 0.65)
	if p < 0.25:
		return ("hl", 0.3)
	if p < 0.7:
		return (None, 0)
	if p < 0.82:
		return ("sh", 0.45)
	if p < 0.92:
		return ("sh", 0.85)
	return ("deep", 0.45)


def apply_shade(c, pal, shade):
	key, amt = shade
	if key is None:
		return C(c)
	target = {"hl": pal["highlight"], "sh": pal["shadow"], "deep": pal["deep"]}[key]
	return mix(c, target, amt)


def vessel_body(arr, pal, x0, w, rows, base=None):
	base = pal["base"] if base is None else base
	for y in rows:
		for x in range(16):
			arr[y, x] = apply_shade(base, pal, col_shade(x, x0, w))


def cheb(x, y, cx=7.5, cy=7.5):
	return max(abs(x - cx), abs(y - cy))


TERRA = {"base": TERRACOTTA["base"], "shadow": TERRACOTTA["shadow"], "deep": TERRACOTTA["deep"], "highlight": TERRACOTTA["highlight"]}


# ---------------------------------------------------------------- storage jar
def gen_storage_jar():
	rng = np.random.RandomState(2101)
	pal = TERRA
	side = canvas(pal["base"])
	vessel_body(side, pal, 3, 10, range(0, 16))
	for y in (7, 10, 13):  # throwing rings every 3 px
		for x in range(16):
			side[y, x] = mix(side[y, x], pal["highlight"], 0.55)
	for x in range(16):
		side[5, x] = mix(side[5, x], pal["highlight"], 0.35)  # shoulder catches light
		side[15, x] = mix(side[15, x], pal["deep"], 0.35)  # foot
		side[4, x] = mix(apply_shade(pal["shadow"], pal, col_shade(x, 5, 6)), pal["deep"], 0.2)  # neck, shaded by lid
	side = speckle(side, rng, 0.03)
	save_tex("storage_jar_side", side)

	top = canvas(pal["base"])
	for y in range(16):
		for x in range(16):
			d = cheb(x, y)
			if d >= 4.5:
				c = mix(pal["base"], pal["shadow"], 0.25)
			elif d >= 3.5:
				c = mix(pal["base"], pal["highlight"], 0.35)
			elif d >= 2.5:
				c = mix(pal["base"], pal["shadow"], 0.5)
			else:
				c = mix(pal["shadow"], pal["deep"], 0.3)
			if x < y and d >= 3.5:
				c = mix(c, pal["shadow"], 0.15)
			top[y, x] = c
	top = speckle(top, rng, 0.03)
	save_tex("storage_jar_top", top)

	lid_c = TERRACOTTA["shadow"]
	lid = canvas(lid_c)
	for y in range(16):
		for x in range(16):
			d = cheb(x, y)
			c = C(lid_c)
			if d >= 3.5 and d < 4.5:
				# rim: lit top/left, shaded bottom/right
				c = mix(lid_c, TERRACOTTA["base"], 0.6) if (y <= 4 or x <= 4) and not (y >= 11 or x >= 11) else mix(lid_c, TERRACOTTA["deep"], 0.55)
			elif d >= 2.5 and d < 3.5:
				c = mix(lid_c, TERRACOTTA["base"], 0.15)
			elif d >= 1.5 and d < 2.5:
				c = mix(lid_c, TERRACOTTA["deep"], 0.3)
			elif d < 1.5:
				c = C(TERRACOTTA["base"])
			lid[y, x] = c
	lid[7, 7] = C(TERRACOTTA["highlight"])
	lid[7, 8] = mix(TERRACOTTA["base"], TERRACOTTA["highlight"], 0.4)
	lid[8, 7] = mix(TERRACOTTA["base"], TERRACOTTA["highlight"], 0.4)
	lid[8, 8] = mix(TERRACOTTA["base"], TERRACOTTA["shadow"], 0.4)
	lid = speckle(lid, rng, 0.03)
	save_tex("storage_jar_lid", lid)


# ---------------------------------------------------------------- memory jar
LAPIS = GLAZES["lapis"]
VEIN_ROWS = list(range(6, 15))  # 9 rows on the body front, bottom-up lighting
VEIN_COLS = (7, 8)


def lapis_body(rng):
	pal = LAPIS
	arr = canvas(pal["base"])
	vessel_body(arr, pal, 3, 10, range(0, 16))
	for x in range(16):
		arr[5, x] = mix(arr[5, x], pal["highlight"], 0.4)
	# glossy streak
	for y in (6, 7, 8):
		arr[y, 4] = mix(arr[y, 4], pal["accent"], 0.55 if y == 6 else 0.35)
	# unglazed foot with glaze drips
	drips = {3: 15, 6: 15, 10: 15}
	for x in range(16):
		arr[15, x] = apply_shade(TERRACOTTA["base"], TERRA, col_shade(x, 3, 10))
		arr[14, x] = mix(arr[14, x], pal["deep"], 0.25)
		if x in drips:
			arr[15, x] = mix(pal["shadow"], pal["deep"], 0.3)
	return speckle(arr, rng, 0.03)


def gen_memory_jar():
	rng = np.random.RandomState(2202)
	side = lapis_body(rng)
	save_tex("memory_jar_side", side)

	front_base = side.copy()
	vein_off = C(LAPIS["deep"])
	vein_on = C(MEMORY_VEIN)
	vein_peak = mix(MEMORY_VEIN, "#FFFFFF", 0.6)
	branches = [(6, 9, 1), (9, 12, 1), (6, 13, 0)]  # (col, row, needs_lit)
	for fill in range(5):
		arr = front_base.copy()
		lit = int(9 * fill / 4 + 0.5)
		lit_rows = VEIN_ROWS[len(VEIN_ROWS) - lit:] if lit else []
		for y in VEIN_ROWS:
			on = y in lit_rows
			for x in VEIN_COLS:
				arr[y, x] = vein_on if on else mix(vein_off, LAPIS["shadow"], 0.25 if x == 7 else 0.0)
			if on:
				for x in (6, 9):
					arr[y, x] = mix(arr[y, x], LAPIS["highlight"], 0.45)
		if lit_rows:
			top = lit_rows[0]
			arr[top, 7] = vein_peak
			arr[top, 8] = mix(vein_on, "#FFFFFF", 0.3)
		for bx, by, _ in branches:
			if by in lit_rows:
				arr[by, bx] = mix(vein_on, LAPIS["highlight"], 0.35)
			else:
				arr[by, bx] = mix(arr[by, bx], vein_off, 0.6)
		# vein channel caps (gold studs at the ends)
		arr[5, 7] = C(GOLD["rim"])
		arr[5, 8] = C(GOLD["shadow"])
		save_tex(f"memory_jar_front_fill{fill}", arr)

	rim = np.zeros((16, 16, 3))
	gpal = {"base": GOLD["base"], "highlight": GOLD["highlight"], "shadow": GOLD["shadow"], "deep": GOLD["shadow"]}
	for y in range(16):
		for x in range(16):
			rim[y, x] = apply_shade(GOLD["base"], gpal, col_shade(x, 5, 6))
	for x in range(16):
		rim[3, x] = mix(rim[3, x], GOLD["highlight"], 0.3)
		rim[5, x] = mix(rim[5, x], GOLD["shadow"], 0.4)
	rim = speckle(rim, rng, 0.02)
	save_tex("memory_jar_rim", rim)

	top = np.zeros((16, 16, 3))
	for y in range(16):
		for x in range(16):
			d = cheb(x, y)
			if d >= 4.5:
				c = mix(LAPIS["base"], LAPIS["highlight"], 0.35)
			elif d >= 3.5:
				lit = (y <= 4 or x <= 4) and not (y >= 11 or x >= 11)
				c = mix(LAPIS["base"], LAPIS["highlight"], 0.25) if lit else C(LAPIS["shadow"])
			elif d >= 2.5:
				c = C(LAPIS["base"])
			elif d >= 1.5:
				c = mix(LAPIS["base"], LAPIS["shadow"], 0.6)
			else:
				c = C(GOLD["base"])
			top[y, x] = c
	top[7, 7] = C(GOLD["highlight"])
	top[8, 8] = C(GOLD["shadow"])
	top[4, 5] = mix(LAPIS["highlight"], LAPIS["accent"], 0.5)
	top = speckle(top, rng, 0.025)
	save_tex("memory_jar_top", top)


# ---------------------------------------------------------------- sentry chime
AME = GLAZES["amethyst"]


def chime_texture(powered):
	rng = np.random.RandomState(2303)
	pal = AME
	arr = canvas(pal["base"])
	vessel_body(arr, pal, 5, 6, range(9, 16))
	# mouth (top view) rows 0..7, cols 4..11, centre (7.5, 3.5)
	for y in range(0, 8):
		for x in range(16):
			d = cheb(x, y, 7.5, 3.5)
			lit = (y <= 1 or x <= 5) and not (y >= 6 or x >= 10)
			if d >= 3.5:
				c = mix(pal["base"], pal["highlight"], 0.55) if lit else mix(pal["base"], pal["shadow"], 0.3)
			elif d >= 2.5:
				c = mix(pal["shadow"], pal["deep"], 0.2) if lit else C(pal["shadow"])
			elif d >= 1.5:
				c = mix(pal["deep"], pal["shadow"], 0.2)
			else:
				c = mix(pal["deep"], "#2A1840", 0.4)
			arr[y, x] = c
	# lip side band
	for x in range(16):
		arr[8, x] = apply_shade(mix(pal["base"], pal["highlight"], 0.45), pal, col_shade(x, 4, 8))
	# bell waist ring and shoulder
	for x in range(16):
		arr[9, x] = mix(arr[9, x], pal["shadow"], 0.3)
		arr[12, x] = mix(arr[12, x], pal["highlight"], 0.35)
		arr[15, x] = mix(arr[15, x], pal["deep"], 0.4)
	arr = speckle(arr, rng, 0.03)
	accent = C(pal["accent"])
	if powered:
		# glowing interior and resonance streaks
		for y in range(0, 8):
			for x in range(4, 12):
				d = cheb(x, y, 7.5, 3.5)
				if d < 1.5:
					arr[y, x] = accent
				elif d < 2.5:
					arr[y, x] = mix(pal["highlight"], accent, 0.3)
		for x in range(4, 12):
			arr[8, x] = mix(arr[8, x], accent, 0.55)
		for x, y in [(6, 10), (6, 11), (6, 12), (9, 12), (9, 13), (9, 14), (8, 10), (7, 14)]:
			arr[y, x] = accent
		for x, y in [(6, 13), (9, 11), (8, 11), (7, 13)]:
			arr[y, x] = mix(arr[y, x], accent, 0.45)
	else:
		for x, y in [(5, 10), (5, 11), (6, 13)]:
			arr[y, x] = mix(arr[y, x], pal["accent"], 0.4)
	return arr


def gen_sentry_chime():
	save_tex("sentry_chime", chime_texture(False))
	save_tex("sentry_chime_powered", chime_texture(True))
	rng = np.random.RandomState(2304)
	ipal = {"base": IRON["base"], "highlight": IRON["highlight"], "shadow": IRON["shadow"], "deep": "#4E4E4E"}
	m = np.zeros((16, 16, 3))
	for y in range(16):
		for x in range(16):
			m[y, x] = apply_shade(IRON["base"], ipal, col_shade(x, 6, 4))
	for x in range(16):
		m[14, x] = mix(m[14, x], IRON["highlight"], 0.35)
		m[15, x] = mix(m[15, x], "#4E4E4E", 0.4)
	# bottom plate (uv 6..10): bevel + rivet
	for y in range(6, 10):
		for x in range(6, 10):
			c = C(IRON["base"])
			if y == 6 or x == 6:
				c = C(IRON["highlight"])
			if y == 9 or x == 9:
				c = C(IRON["shadow"])
			m[y, x] = c
	m[7, 7] = mix(IRON["shadow"], "#4E4E4E", 0.5)
	m = speckle(m, rng, 0.03)
	save_tex("sentry_chime_mount", m)


# ---------------------------------------------------------------- urns (vigil / reliquary)
def urn_side(rng, pal, band):
	"""Body rows 4..15 (cols 4..11), neck rows 1..3 (cols 5..10)."""
	arr = canvas(pal["base"])
	vessel_body(arr, pal, 4, 8, range(4, 16))
	vessel_body(arr, pal, 5, 6, range(0, 4))
	for x in range(16):
		arr[1, x] = mix(arr[1, x], pal["highlight"], 0.55)  # neck lip
		arr[3, x] = mix(arr[3, x], pal["shadow"], 0.35)
		arr[4, x] = mix(arr[4, x], pal["highlight"], 0.4)  # shoulder
		arr[14, x] = mix(arr[14, x], pal["shadow"], 0.45)
		arr[15, x] = apply_shade(TERRACOTTA["base"], TERRA, col_shade(x, 4, 8))  # unglazed foot
	# decorative band rows 6..7
	for x in range(16):
		arr[6, x] = mix(pal["deep"], pal["shadow"], 0.3)
		arr[7, x] = mix(pal["deep"], pal["shadow"], 0.3)
	for x in range(16):
		if band == "dots" and x % 2 == 0:
			arr[6 + (x // 2) % 2, x] = C(pal["highlight"])
		elif band == "wave":
			arr[6 if x % 4 < 2 else 7, x] = C(pal["highlight"])
	# drip of glaze into the foot
	for x in (6, 9):
		arr[15, x] = mix(pal["shadow"], pal["deep"], 0.3)
	return speckle(arr, rng, 0.03)


def urn_top(rng, pal, interior, core):
	arr = np.zeros((16, 16, 3))
	for y in range(16):
		for x in range(16):
			d = cheb(x, y)
			lit = (y <= 5 or x <= 5) and not (y >= 10 or x >= 10)
			if d >= 4.5:
				c = mix(pal["base"], pal["shadow"], 0.3)
			elif d >= 3.5:
				c = mix(pal["base"], pal["highlight"], 0.3)
			elif d >= 2.5:
				c = mix(pal["base"], pal["highlight"], 0.7) if lit else C(pal["base"])
			elif d >= 1.5:
				c = C(pal["deep"]) if lit else C(pal["shadow"])
			else:
				c = C(interior)
			arr[y, x] = c
	if core is not None:
		arr[7, 7] = C(core)
	return speckle(arr, rng, 0.025)


EYE = [  # 8 wide (cols 4..11), rows 8..13
	"...hh...",
	"..dddd..",
	".dwppwd.",
	".dwpqwd.",
	"..dddd..",
	"...hh...",
]


def gen_vigil_urn():
	rng = np.random.RandomState(2404)
	pal = GLAZES["glowink"]
	side = urn_side(rng, pal, "dots")
	save_tex("vigil_urn_side", side)
	front = side.copy()
	cmap = {"d": C(pal["deep"]), "w": C(pal["accent"]), "p": mix(pal["deep"], "#0E3B37", 0.4),
			"q": C(pal["highlight"]), "h": mix(pal["base"], pal["accent"], 0.55)}
	for r, row in enumerate(EYE):
		for c, ch in enumerate(row):
			if ch in cmap:
				front[8 + r, 4 + c] = cmap[ch]
	front[10, 6] = C("#FFFFFF") * 0.97  # catch-light
	save_tex("vigil_urn_front", front)
	save_tex("vigil_urn_top", urn_top(rng, pal, pal["highlight"], pal["accent"]))


SWIRL = [  # 8 wide (cols 4..11), rows 7..13. a = swirl, c = soul core
	"..aaaa..",
	".a....a.",
	"a..aa..a",
	"a.a..a.a",
	"a.a.ca.a",
	".a..aa.a",
	"..aa..a.",
]


def gen_reliquary_urn():
	rng = np.random.RandomState(2505)
	pal = GLAZES["echo"]
	side = urn_side(rng, pal, "wave")
	save_tex("reliquary_urn_side", side)
	for filled in (False, True):
		front = side.copy()
		swirl = C(pal["accent"]) if filled else C(pal["highlight"])
		core = C("#FFFFFF") * 0.96 if filled else C(pal["accent"])
		for r, row in enumerate(SWIRL):
			for c, ch in enumerate(row):
				x, y = 4 + c, 7 + r
				if ch == "a":
					front[y, x] = swirl
				elif ch == "c":
					front[y, x] = core
				elif filled:
					front[y, x] = mix(front[y, x], pal["highlight"], 0.35)
		save_tex("reliquary_urn_front_filled" if filled else "reliquary_urn_front", front)
	save_tex("reliquary_urn_top", urn_top(rng, pal, pal["deep"], None))


# ---------------------------------------------------------------- models
DIRS = ("down", "up", "north", "south", "west", "east")


def auto_uv(frm, to, d):
	x1, y1, z1 = frm
	x2, y2, z2 = to
	return {
		"north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
		"south": [x1, 16 - y2, x2, 16 - y1],
		"west": [z1, 16 - y2, z2, 16 - y1],
		"east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
		"up": [x1, z1, x2, z2],
		"down": [x1, 16 - z2, x2, 16 - z1],
	}[d]


def cull_for(frm, to, d):
	x1, y1, z1 = frm
	x2, y2, z2 = to
	touch = {"down": y1 == 0, "up": y2 == 16, "north": z1 == 0, "south": z2 == 16, "west": x1 == 0, "east": x2 == 16}
	return d if touch[d] else None


def element(frm, to, faces):
	"""faces: dir -> texture key or (texture key, uv)."""
	out = {}
	for d in DIRS:
		if d not in faces:
			continue
		spec = faces[d]
		if isinstance(spec, tuple):
			key, uv = spec
		else:
			key, uv = spec, auto_uv(frm, to, d)
		f = {"uv": uv, "texture": "#" + key}
		cf = cull_for(frm, to, d)
		if cf:
			f["cullface"] = cf
		out[d] = f
	return {"from": list(frm), "to": list(to), "faces": out}


def save_model(name, obj):
	MODEL_DEFS[name] = obj
	save_json(os.path.join(MODELS, name + ".json"), obj)


def cube_all(name, texture):
	save_model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": tex(texture)}})


def jar_model(name, side, front, top, neck, lid):
	sides = {"south": "side", "west": "side", "east": "side"}
	lid_uv = [4, 4, 12, 5]
	elements = [
		element((3, 0, 3), (13, 11, 13), dict(sides, north="front", up="top", down="top")),
		element((5, 11, 5), (11, 12, 11), {"north": "neck", "south": "neck", "west": "neck", "east": "neck"}),
		element((4, 12, 4), (12, 13, 12), {"up": "lid", "down": "lid", "north": ("lid", lid_uv),
											"south": ("lid", lid_uv), "west": ("lid", lid_uv), "east": ("lid", lid_uv)}),
	]
	save_model(name, {
		"parent": "minecraft:block/block",
		"textures": {"particle": tex(side), "side": tex(side), "front": tex(front), "top": tex(top),
					 "neck": tex(neck), "lid": tex(lid)},
		"elements": elements,
	})


def chime_model(name, bell):
	save_model(name, {
		"parent": "minecraft:block/block",
		"textures": {"particle": tex(bell), "bell": tex(bell), "mount": tex("sentry_chime_mount")},
		"elements": [
			element((6, 0, 6), (10, 2, 10), {"down": "mount", "north": "mount", "south": "mount", "west": "mount", "east": "mount"}),
			element((5, 2, 5), (11, 9, 11), {d: ("bell", [5, 9, 11, 16]) for d in ("north", "south", "west", "east")}
					| {"down": ("bell", [5, 9, 11, 15])}),
			element((4, 9, 4), (12, 10, 12), {d: ("bell", [4, 8, 12, 9]) for d in ("north", "south", "west", "east")}
					| {"up": ("bell", [4, 0, 12, 8]), "down": ("bell", [4, 8, 12, 16])}),
		],
	})


def urn_model(name, side, front, top):
	save_model(name, {
		"parent": "minecraft:block/block",
		"textures": {"particle": tex(side), "side": tex(side), "front": tex(front), "top": tex(top)},
		"elements": [
			element((4, 0, 4), (12, 12, 12), {"north": "front", "south": "side", "west": "side", "east": "side", "up": "top", "down": "top"}),
			element((5, 12, 5), (11, 15, 11), {"north": "side", "south": "side", "west": "side", "east": "side", "up": "top"}),
			element((3, 6, 7), (4, 10, 9), {"north": "side", "south": "side", "west": "side", "up": "side", "down": "side"}),
			element((12, 6, 7), (13, 10, 9), {"north": "side", "south": "side", "east": "side", "up": "side", "down": "side"}),
		],
	})


def stairs_slab_models(prefix_stairs, prefix_slab, full_model, texture):
	t = tex(texture)
	three = {"bottom": t, "side": t, "top": t}
	save_model(prefix_stairs, {"parent": "minecraft:block/stairs", "textures": dict(three)})
	save_model(prefix_stairs + "_inner", {"parent": "minecraft:block/inner_stairs", "textures": dict(three)})
	save_model(prefix_stairs + "_outer", {"parent": "minecraft:block/outer_stairs", "textures": dict(three)})
	save_model(prefix_slab, {"parent": "minecraft:block/slab", "textures": dict(three)})
	save_model(prefix_slab + "_top", {"parent": "minecraft:block/slab_top", "textures": dict(three)})


def wall_models(prefix, texture):
	w = {"wall": tex(texture)}
	save_model(prefix + "_post", {"parent": "minecraft:block/template_wall_post", "textures": dict(w)})
	save_model(prefix + "_side", {"parent": "minecraft:block/template_wall_side", "textures": dict(w)})
	save_model(prefix + "_side_tall", {"parent": "minecraft:block/template_wall_side_tall", "textures": dict(w)})
	save_model(prefix + "_inventory", {"parent": "minecraft:block/wall_inventory", "textures": dict(w)})


def gen_models():
	for name in ("fired_tiles", "lumen_tiles", "ember_tile", "fireclay_bricks"):
		cube_all(name, name)
	stairs_slab_models("fired_tile_stairs", "fired_tile_slab", "fired_tiles", "fired_tiles")
	stairs_slab_models("fireclay_brick_stairs", "fireclay_brick_slab", "fireclay_bricks", "fireclay_bricks")
	wall_models("fireclay_brick_wall", "fireclay_bricks")
	jar_model("storage_jar", "storage_jar_side", "storage_jar_side", "storage_jar_top", "storage_jar_side", "storage_jar_lid")
	for n in range(5):
		jar_model(f"memory_jar_fill{n}", "memory_jar_side", f"memory_jar_front_fill{n}", "memory_jar_top", "memory_jar_rim", "memory_jar_top")
	chime_model("sentry_chime", "sentry_chime")
	chime_model("sentry_chime_powered", "sentry_chime_powered")
	urn_model("vigil_urn", "vigil_urn_side", "vigil_urn_front", "vigil_urn_top")
	urn_model("reliquary_urn", "reliquary_urn_side", "reliquary_urn_front", "reliquary_urn_top")
	urn_model("reliquary_urn_filled", "reliquary_urn_side", "reliquary_urn_front_filled", "reliquary_urn_top")


# ---------------------------------------------------------------- blockstates (mirroring vanilla brick_* / lever)
HORIZONTAL_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def variant(model, x=0, y=0, uvlock=False):
	v = {"model": model}
	if uvlock:
		v["uvlock"] = True
	if x:
		v["x"] = x
	if y:
		v["y"] = y
	return v


def stairs_blockstate(stairs):
	"""Same table as vanilla brick_stairs.json (40 variants)."""
	base_y = {"east": 0, "south": 90, "west": 180, "north": 270}
	out = {}
	for facing in ("east", "north", "south", "west"):
		for half in ("bottom", "top"):
			for shape in ("inner_left", "inner_right", "outer_left", "outer_right", "straight"):
				model = mdl(stairs + {"inner_left": "_inner", "inner_right": "_inner", "outer_left": "_outer",
									   "outer_right": "_outer", "straight": ""}[shape])
				y = base_y[facing]
				if half == "bottom" and shape in ("inner_left", "outer_left"):
					y = (y + 270) % 360
				if half == "top" and shape in ("inner_right", "outer_right"):
					y = (y + 90) % 360
				x = 180 if half == "top" else 0
				out[f"facing={facing},half={half},shape={shape}"] = variant(model, x, y, uvlock=(x != 0 or y != 0))
	return {"variants": out}


def slab_blockstate(slab, full):
	return {"variants": {
		"type=bottom": variant(mdl(slab)),
		"type=double": variant(mdl(full)),
		"type=top": variant(mdl(slab + "_top")),
	}}


def wall_blockstate(wall):
	parts = [{"apply": variant(mdl(wall + "_post")), "when": {"up": "true"}}]
	for kind, suffix in (("low", "_side"), ("tall", "_side_tall")):
		for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
			parts.append({"apply": variant(mdl(wall + suffix), 0, y, uvlock=True), "when": {d: kind}})
	return {"multipart": parts}


def chime_blockstate():
	ceiling_y = {"north": 180, "east": 270, "south": 0, "west": 90}
	out = {}
	for face in ("ceiling", "floor", "wall"):
		for facing in ("east", "north", "south", "west"):
			for power in range(16):
				model = mdl("sentry_chime_powered" if power > 0 else "sentry_chime")
				if face == "floor":
					v = variant(model, 0, HORIZONTAL_Y[facing])
				elif face == "wall":
					v = variant(model, 90, HORIZONTAL_Y[facing])
				else:
					v = variant(model, 180, ceiling_y[facing])
				out[f"face={face},facing={facing},power={power}"] = v
	return {"variants": out}


def gen_blockstates():
	def bs(name, obj):
		save_json(os.path.join(BLOCKSTATES, name + ".json"), obj)

	for name in ("fired_tiles", "lumen_tiles", "fireclay_bricks", "storage_jar"):
		bs(name, {"variants": {"": variant(mdl(name))}})
	for name in ("ember_tile", "vigil_urn"):
		bs(name, {"variants": {"masterwork=false": variant(mdl(name)), "masterwork=true": variant(mdl(name))}})
	bs("reliquary_urn", {"variants": {"filled=false": variant(mdl("reliquary_urn")),
									  "filled=true": variant(mdl("reliquary_urn_filled"))}})
	bs("memory_jar", {"variants": {f"facing={f},fill={n}": variant(mdl(f"memory_jar_fill{n}"), 0, HORIZONTAL_Y[f])
								   for f in ("east", "north", "south", "west") for n in range(5)}})
	bs("sentry_chime", chime_blockstate())
	bs("fired_tile_stairs", stairs_blockstate("fired_tile_stairs"))
	bs("fired_tile_slab", slab_blockstate("fired_tile_slab", "fired_tiles"))
	bs("fireclay_brick_stairs", stairs_blockstate("fireclay_brick_stairs"))
	bs("fireclay_brick_slab", slab_blockstate("fireclay_brick_slab", "fireclay_bricks"))
	bs("fireclay_brick_wall", wall_blockstate("fireclay_brick_wall"))


def gen_items():
	models = {
		"fired_tiles": "fired_tiles", "fired_tile_stairs": "fired_tile_stairs", "fired_tile_slab": "fired_tile_slab",
		"lumen_tiles": "lumen_tiles", "storage_jar": "storage_jar", "memory_jar": "memory_jar_fill0",
		"sentry_chime": "sentry_chime", "vigil_urn": "vigil_urn", "ember_tile": "ember_tile",
		"reliquary_urn": "reliquary_urn", "fireclay_bricks": "fireclay_bricks",
		"fireclay_brick_stairs": "fireclay_brick_stairs", "fireclay_brick_slab": "fireclay_brick_slab",
		"fireclay_brick_wall": "fireclay_brick_wall_inventory",
	}
	for item, model in models.items():
		save_json(os.path.join(ITEMS, item + ".json"), {"model": {"type": "minecraft:model", "model": mdl(model)}})


# ---------------------------------------------------------------- verification against vanilla
def check_against_vanilla(vanilla_dir):
	"""Compare generated stairs/slab/wall/chime blockstates with the vanilla brick_*/lever files."""
	def load(p):
		with open(p, encoding="utf-8") as f:
			return json.load(f)

	ok = True
	pairs = [("brick_stairs", "fired_tile_stairs", {"minecraft:block/brick_stairs": mdl("fired_tile_stairs")}),
			 ("brick_slab", "fired_tile_slab", {"minecraft:block/brick_slab": mdl("fired_tile_slab"), "minecraft:block/bricks": mdl("fired_tiles")}),
			 ("brick_wall", "fireclay_brick_wall", {"minecraft:block/brick_wall": mdl("fireclay_brick_wall")})]
	for van, ours, rename in pairs:
		v = json.dumps(load(os.path.join(vanilla_dir, van + ".json")), sort_keys=True)
		for a, b in rename.items():
			v = v.replace('"' + a, '"' + b)
		o = json.dumps(load(os.path.join(BLOCKSTATES, ours + ".json")), sort_keys=True)
		if json.loads(v) != json.loads(o):
			print("MISMATCH vs vanilla:", van, ours)
			ok = False
	lever = load(os.path.join(vanilla_dir, "lever.json"))["variants"]
	chime = load(os.path.join(BLOCKSTATES, "sentry_chime.json"))["variants"]
	for k, v in lever.items():
		face, facing, _ = k.split(",")
		ours = chime[f"{face},{facing},power=0"]
		if (v.get("x", 0), v.get("y", 0)) != (ours.get("x", 0), ours.get("y", 0)):
			print("MISMATCH vs lever:", k)
			ok = False
	print("vanilla blockstate check:", "OK" if ok else "FAILED")
	return ok


# ---------------------------------------------------------------- previews
def font(size):
	try:
		return ImageFont.load_default(size=size)
	except TypeError:
		return ImageFont.load_default()


def texture_sheet(path):
	names = list(TEXTURES)
	cols = 6
	cell = 256
	pad = 28
	rows = (len(names) + cols - 1) // cols
	sheet = Image.new("RGBA", (cols * (cell + 12) + 12, rows * (cell + pad + 12) + 12), (58, 58, 64, 255))
	draw = ImageDraw.Draw(sheet)
	f = font(18)
	for i, n in enumerate(names):
		cx = 12 + (i % cols) * (cell + 12)
		cy = 12 + (i // cols) * (cell + pad + 12)
		sheet.paste(TEXTURES[n].resize((cell, cell), Image.NEAREST), (cx, cy + pad))
		draw.text((cx, cy + 4), n, fill=(240, 240, 240, 255), font=f)
	sheet.save(path)


def resolve_tex(model, key):
	t = model["textures"]
	ref = key
	for _ in range(5):
		if ref.startswith("#"):
			ref = t[ref[1:]]
		else:
			break
	return TEXTURES[ref.split("/")[-1]]


def render_model(model, scale=11):
	"""Orthographic 3/4 view from (-X, +Y, -Z): shows north (front), west and top faces."""
	if model.get("parent") == "minecraft:block/cube_all":
		t = "#all"
		model = {"textures": {"all": model["textures"]["all"]},
				 "elements": [element((0, 0, 0), (16, 16, 16), {d: "all" for d in DIRS})]}
	r = np.array([-1.0, 0.0, 1.0]) / np.sqrt(2)
	u = np.array([1.0, 2.0, 1.0]) / np.sqrt(6)
	fwd = np.array([1.0, -1.0, 1.0]) / np.sqrt(3)
	size = int(scale * 30)
	img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
	draw = ImageDraw.Draw(img)
	quads = []
	shade = {"up": 1.0, "north": 0.8, "west": 0.6}
	for el in model["elements"]:
		(x1, y1, z1), (x2, y2, z2) = el["from"], el["to"]
		corners = {  # P00 = (u1, v1), P10 = (u2, v1), P01 = (u1, v2)
			"north": ((x2, y2, z1), (x1, y2, z1), (x2, y1, z1)),
			"west": ((x1, y2, z1), (x1, y2, z2), (x1, y1, z1)),
			"up": ((x1, y2, z1), (x2, y2, z1), (x1, y2, z2)),
		}
		for d, (p00, p10, p01) in corners.items():
			if d not in el["faces"]:
				continue
			face = el["faces"][d]
			timg = resolve_tex(model, face["texture"])
			px = np.array(timg)
			uu1, vv1, uu2, vv2 = face["uv"]
			p00, p10, p01 = np.array(p00, float), np.array(p10, float), np.array(p01, float)
			nu = max(1, int(round(abs(uu2 - uu1))))
			nv = max(1, int(round(abs(vv2 - vv1))))
			for i in range(nu):
				for j in range(nv):
					s0, s1 = i / nu, (i + 1) / nu
					t0, t1 = j / nv, (j + 1) / nv
					tu = int(np.floor(uu1 + (uu2 - uu1) * (s0 + s1) / 2))
					tv = int(np.floor(vv1 + (vv2 - vv1) * (t0 + t1) / 2))
					col = px[min(15, max(0, tv)), min(15, max(0, tu))][:3] * shade[d]
					pts = []
					for s, tt in ((s0, t0), (s1, t0), (s1, t1), (s0, t1)):
						p = p00 + s * (p10 - p00) + tt * (p01 - p00) - 8
						pts.append(p)
					depth = np.mean([np.dot(p, fwd) for p in pts])
					quads.append((depth, pts, tuple(int(c) for c in col)))
	quads.sort(key=lambda q: -q[0])
	for _, pts, col in quads:
		poly = [(size / 2 + np.dot(p, r) * scale, size / 2 - np.dot(p, u) * scale) for p in pts]
		draw.polygon(poly, fill=col + (255,), outline=col + (255,))
	return img


def model_sheet(path):
	names = ["storage_jar", "memory_jar_fill0", "memory_jar_fill1", "memory_jar_fill2", "memory_jar_fill3", "memory_jar_fill4",
			 "sentry_chime", "sentry_chime_powered", "vigil_urn", "reliquary_urn", "reliquary_urn_filled",
			 "fired_tiles", "lumen_tiles", "ember_tile", "fireclay_bricks"]
	cell = 330
	cols = 5
	tiles = ["fired_tiles", "lumen_tiles", "ember_tile", "fireclay_bricks"]
	rows = (len(names) + cols - 1) // cols
	tile_h = 16 * 3 * 6 + 40
	sheet = Image.new("RGBA", (cols * cell, rows * (cell + 30) + tile_h), (120, 140, 160, 255))
	draw = ImageDraw.Draw(sheet)
	f = font(18)
	for i, n in enumerate(names):
		cx, cy = (i % cols) * cell, (i // cols) * (cell + 30)
		im = render_model(MODEL_DEFS[n])
		sheet.alpha_composite(im, (cx, cy + 30))
		draw.text((cx + 8, cy + 6), n, fill=(255, 255, 255, 255), font=f)
	y0 = rows * (cell + 30)
	for i, n in enumerate(tiles):
		t = TEXTURES[n]
		tiled = Image.new("RGBA", (48, 48))
		for a in range(3):
			for b in range(3):
				tiled.paste(t, (a * 16, b * 16))
		sheet.paste(tiled.resize((288, 288), Image.NEAREST), (10 + i * 300, y0 + 30))
		draw.text((10 + i * 300, y0 + 6), n + " (3x3 tiled)", fill=(255, 255, 255, 255), font=f)
	sheet.save(path)


# ---------------------------------------------------------------- main
def main():
	preview = None
	if "--preview" in sys.argv:
		preview = sys.argv[sys.argv.index("--preview") + 1]
	vanilla = None
	if "--vanilla-blockstates" in sys.argv:
		vanilla = sys.argv[sys.argv.index("--vanilla-blockstates") + 1]

	gen_fired_tiles()
	gen_lumen_tiles()
	gen_ember_tile()
	gen_fireclay_bricks()
	gen_storage_jar()
	gen_memory_jar()
	gen_sentry_chime()
	gen_vigil_urn()
	gen_reliquary_urn()
	gen_models()
	gen_blockstates()
	gen_items()

	if vanilla:
		check_against_vanilla(vanilla)
	if preview:
		os.makedirs(preview, exist_ok=True)
		texture_sheet(os.path.join(preview, "blocks_ceramics.png"))
		model_sheet(os.path.join(preview, "blocks_ceramics_models.png"))
	for p in WRITTEN:
		print(p)
	print(f"{len(WRITTEN)} files written")


if __name__ == "__main__":
	main()
