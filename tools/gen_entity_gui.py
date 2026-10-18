#!/usr/bin/env python3
"""Fireclay asset generator: entity + GUI group.

Writes (under src/main/resources/assets/fireclay/):
  textures/entity/terracotta_sentinel.png            64x64 skin (contract section 3 UV layout)
  textures/entity/terracotta_sentinel_masterwork.png 64x64 skin with gold leaf / kintsugi
  textures/gui/container/kiln.png                    256x256 kiln screen sheet (contract section 4)
  textures/particle/kiln_ember_{0,1,2}.png           8x8 ember particle frames
  particles/kiln_ember.json                          particle texture list
  icon.png                                           128x128 mod icon
Deterministic (fixed seeds). Run from the fireclay/ project root:
  PYTHONPATH=tools python3 tools/gen_entity_gui.py [preview_dir]
If preview_dir is given, upscaled preview sheets (incl. the skin unwrap check) are written there.
"""
import json
import os
import random
import sys

from PIL import Image, ImageDraw

from palette import TERRACOTTA, KILN_BRICK, HEAT, GOLD, GLAZES, OUTLINE, rgba

ROOT = os.path.join("src", "main", "resources", "assets", "fireclay")
WRITTEN = []

# Local accents (design.md: sentinel glaze shoulders #E8902A, chest #FFC24A).
GLAZE = {"highlight": rgba("#F5B25A"), "base": rgba("#E8902A"), "shadow": rgba("#B8661A"), "deep": rgba("#8A4A12")}
CORE = rgba("#FFC24A")
T = TERRACOTTA


# ---------------------------------------------------------------- helpers
def mix(a, b, t):
	return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3)) + (255,)


def shade(c, f):
	return tuple(int(max(12, min(255, round(c[i] * f)))) for i in range(3)) + (c[3],)


def save(img, rel):
	path = os.path.join(ROOT, rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	img.save(path)
	WRITTEN.append(path)


def write_json(rel, data):
	path = os.path.join(ROOT, rel)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(data, f, indent=2)
		f.write("\n")
	WRITTEN.append(path)


# ================================================================ SENTINEL SKIN
# Box UV unwrap (contract section 3): texOffs(u,v), size (w,h,d)
PARTS = {
	"head": ((0, 0), (8, 8, 8)),
	"crest": ((32, 0), (2, 3, 5)),
	"body": ((16, 16), (8, 12, 4)),
	"waist": ((0, 32), (9, 3, 5)),
	"right_arm": ((40, 16), (4, 12, 4)),
	"left_arm": ((32, 48), (4, 12, 4)),
	"right_leg": ((0, 16), (4, 12, 4)),
	"left_leg": ((16, 48), (4, 12, 4)),
}


def faces(part):
	"""Return {face: (x, y, w, h)} texture rectangles for a part."""
	(u, v), (w, h, d) = PARTS[part]
	return {
		"top": (u + d, v, w, d),
		"bottom": (u + d + w, v, w, d),
		"right": (u, v + d, d, h),
		"front": (u + d, v + d, w, h),
		"left": (u + d + w, v + d, d, h),
		"back": (u + 2 * d + w, v + d, w, h),
	}


class Skin:
	def __init__(self, masterwork, seed):
		self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
		self.px = self.img.load()
		self.mw = masterwork
		self.rng = random.Random(seed)
		self.quiet = set()  # pixels excluded from noise (glows, gold)

	def put(self, x, y, c, quiet=False):
		self.px[x, y] = c
		if quiet:
			self.quiet.add((x, y))
		else:
			self.quiet.discard((x, y))

	def region(self, part, face, fn, quiet=False):
		x0, y0, w, h = faces(part)[face]
		for ly in range(h):
			for lx in range(w):
				c = fn(lx, ly, w, h)
				if c is not None:
					self.put(x0 + lx, y0 + ly, c, quiet)

	def local(self, part, face, lx, ly, c, quiet=True):
		x0, y0, w, h = faces(part)[face]
		if 0 <= lx < w and 0 <= ly < h:
			self.put(x0 + lx, y0 + ly, c, quiet)

	def crack(self, part, face, pts):
		"""Hairline crack along local points. Plain: grout with an ember glint. Masterwork: kintsugi gold."""
		for i, (lx, ly) in enumerate(pts):
			if self.mw:
				c = GOLD["highlight"] if i == 0 else (GOLD["base"] if i % 2 else GOLD["rim"])
			else:
				c = HEAT["warm"] if i == len(pts) // 2 else T["grout"]
			self.local(part, face, lx, ly, c)

	def noise(self):
		for y in range(64):
			for x in range(64):
				r = self.rng.random()
				f = 1.0 + self.rng.uniform(-0.06, 0.06)
				c = self.px[x, y]
				if c[3] and (x, y) not in self.quiet and r < 0.35:
					self.px[x, y] = shade(c, f)


def lamellar(lx, ly, w, h, off=0, row_h=3, plate_w=4, stagger=2, pal=T, top_tone="highlight"):
	"""Small overlapping plates: top row lit, middle base, lower edge shaded, dark vertical seams."""
	row = (ly + off) // row_h
	ry = (ly + off) % row_h
	seam = (lx + (row % 2) * stagger) % plate_w == plate_w - 1
	if ry == row_h - 1:
		return pal["shadow"] if not seam else pal["deep"]
	if seam:
		return pal["shadow"]
	if ry == 0:
		return pal[top_tone]
	return pal["base"]


def glaze_band(ly, h):
	if ly == 0:
		return GLAZE["highlight"]
	if ly == h - 1:
		return GLAZE["shadow"]
	return GLAZE["base"]


def build_skin(masterwork):
	s = Skin(masterwork, seed=4117 if masterwork else 4116)
	mw = masterwork
	gold_leaf_rng = random.Random(9031)

	def leaf(c, x, y):
		"""Masterwork gold leaf on plate highlights."""
		if mw and c == T["highlight"] and gold_leaf_rng.random() < 0.14:
			return GOLD["base"] if gold_leaf_rng.random() < 0.6 else GOLD["highlight"]
		return c

	# ---------------- head: lamellar helmet + sculpted face
	def head_front(lx, ly, w, h):
		if ly == 0:
			return lamellar(lx, 0, w, h, pal=T, top_tone="shadow")
		if ly == 1:  # helmet brim
			if mw:
				return GOLD["shadow"] if lx in (0, 7) else GOLD["base"]
			return T["shadow"] if lx in (0, 7) else T["highlight"]
		if ly == 2:  # brow
			if lx in (0, 7):
				return T["shadow"]
			return T["highlight"] if lx in (1, 2, 5, 6) else T["base"]
		if ly == 3:  # eyes row
			return {0: T["shadow"], 3: T["highlight"], 4: T["base"], 7: T["shadow"]}.get(lx)
		if ly == 4:
			if lx in (1, 2, 5, 6):
				return mix(T["base"], HEAT["hot"], 0.35)  # eye glow bleeding onto cheeks
			if lx in (3, 4):
				return T["shadow"]  # nose underside
			return T["shadow"] if lx in (0, 7) else T["base"]
		if ly == 5:  # moustache
			if lx in (2, 3, 4, 5):
				return T["deep"]
			return T["shadow"] if lx in (0, 7) else T["base"]
		if ly == 6:  # mouth
			if lx in (3, 4):
				return T["grout"]
			return T["shadow"] if lx in (0, 7) else T["base"]
		# chin + strap
		if lx in (0, 7):
			return GOLD["shadow"] if mw else T["deep"]
		return T["highlight"] if lx in (3, 4) else T["shadow"]

	s.region("head", "front", head_front)
	# glowing eyes (quiet, no noise)
	for lx, c in ((1, HEAT["hot_light"]), (2, HEAT["white"]), (5, HEAT["white"]), (6, HEAT["hot_light"])):
		s.local("head", "front", lx, 3, c)

	def head_side(right):
		def fn(lx, ly, w, h):
			# lx 0 is the back edge for the right face, front edge for the left face (box unwrap order)
			front_x = w - 1 if right else 0
			if ly <= 1:
				return lamellar(lx, ly, w, h, off=1, pal=T, top_tone="shadow") if ly == 0 else (
					(GOLD["base"] if mw else T["highlight"]) if abs(lx - front_x) <= 3 else T["highlight"])
			if ly <= 5 and abs(lx - front_x) >= 4:  # helmet neck guard (back half)
				return lamellar(lx, ly, w, h, off=1)
			if ly in (3, 4) and abs(lx - front_x) == 2:  # ear
				return T["shadow"] if ly == 3 else T["deep"]
			if ly >= 6:
				return T["shadow"] if ly == 6 else T["deep"]
			return T["base"] if abs(lx - front_x) > 0 else T["shadow"]
		return fn

	s.region("head", "right", head_side(True))
	s.region("head", "left", head_side(False))

	def head_back(lx, ly, w, h):
		if ly <= 5:
			return leaf(lamellar(lx, ly, w, h, off=1), lx, ly)
		if ly == 6:
			return T["shadow"]
		return T["deep"]

	s.region("head", "back", head_back)
	# topknot tie on the back
	s.local("head", "back", 3, 2, GLAZE["base"])
	s.local("head", "back", 4, 2, GLAZE["shadow"])

	def head_top(lx, ly, w, h):
		c = lamellar(lx, ly, w, h, row_h=4, plate_w=4)
		if lx in (3, 4):
			c = T["deep"]  # seam under the crest
		return leaf(c, lx, ly)

	s.region("head", "top", head_top)
	s.region("head", "bottom", lambda lx, ly, w, h: T["deep"] if 2 <= lx <= 5 and 2 <= ly <= 5 else T["shadow"])

	# ---------------- crest (glazed ridge; gold on the masterwork)
	cp = GOLD if mw else GLAZE
	crest_hi = cp["highlight"]
	crest_base = cp["base"]
	crest_sh = cp["shadow"]
	crest_deep = GOLD["shadow"] if mw else GLAZE["deep"]

	def crest_side(lx, ly, w, h):
		if ly == 0:
			return crest_hi
		if ly == h - 1:
			return crest_deep if lx % 2 == 0 else crest_sh
		return crest_sh if lx % 2 else crest_base

	s.region("crest", "top", lambda lx, ly, w, h: crest_hi if (lx + ly) % 3 else crest_base, quiet=mw)
	s.region("crest", "bottom", lambda lx, ly, w, h: crest_deep, quiet=mw)
	for f in ("right", "left"):
		s.region("crest", f, crest_side, quiet=mw)
	for f in ("front", "back"):
		s.region("crest", f, lambda lx, ly, w, h: crest_hi if ly == 0 else (crest_base if ly == 1 else crest_sh), quiet=mw)

	# ---------------- body: lamellar cuirass + chest kiln mouth
	def body_front(lx, ly, w, h):
		if ly == 0:  # gorget
			return T["deep"] if lx in (0, 7) else T["shadow"]
		if ly == 1:
			if mw and lx in (1, 6):
				return GOLD["base"]
			return T["deep"] if lx in (0, 7) else T["grout"] if lx in (3, 4) else T["shadow"]
		return leaf(lamellar(lx, ly - 2, w, h), lx, ly)

	s.region("body", "front", body_front)
	# kiln mouth on the chest: x1..6, y3..8
	brick = KILN_BRICK
	mouth = [
		"..bBBb..",
		".bghhgb.",
		".bhwwhb.",
		".bwccwb.",
		".bhwwhb.",
		".SsSsSS.",
	]
	glow = {"g": HEAT["hot"], "h": HEAT["hot_light"], "w": HEAT["white"], "c": HEAT["core"]}
	for ry, row in enumerate(mouth):
		for lx, ch in enumerate(row):
			if ch == ".":
				continue
			if ch in glow:
				c = glow[ch]
				if mw and ch == "g":
					c = CORE
			elif ch == "b":
				c = brick["shadow"] if (lx + ry) % 2 else brick["base"]
			elif ch == "B":
				c = GOLD["base"] if mw else brick["highlight"]
			elif ch == "S":
				c = GOLD["shadow"] if mw else brick["soot"]
			else:
				c = brick["shadow"]
			s.local("body", "front", lx, 3 + ry, c)

	def body_side(lx, ly, w, h):
		if ly <= 1:
			return T["shadow"] if ly == 0 else T["deep"]
		return leaf(lamellar(lx, ly - 2, w, h, stagger=1, plate_w=2 if False else 4), lx, ly)

	s.region("body", "right", body_side)
	s.region("body", "left", body_side)

	def body_back(lx, ly, w, h):
		if ly == 0:
			return T["shadow"]
		if ly == 1:
			return T["deep"]
		if lx in (3, 4) and 2 <= ly <= 9:  # spine ridge
			return T["highlight"] if lx == 3 else T["shadow"]
		return leaf(lamellar(lx, ly - 2, w, h), lx, ly)

	s.region("body", "back", body_back)
	s.region("body", "top", lambda lx, ly, w, h: T["shadow"] if 1 <= ly <= 2 else T["deep"])
	s.region("body", "bottom", lambda lx, ly, w, h: T["deep"])

	# ---------------- waist: glazed belt with buckle
	s.region("waist", "front", lambda lx, ly, w, h: glaze_band(ly, h))
	s.region("waist", "back", lambda lx, ly, w, h: glaze_band(ly, h))
	s.region("waist", "right", lambda lx, ly, w, h: glaze_band(ly, h))
	s.region("waist", "left", lambda lx, ly, w, h: glaze_band(ly, h))
	s.region("waist", "top", lambda lx, ly, w, h: GLAZE["highlight"] if ly in (0, h - 1) else GLAZE["shadow"])
	s.region("waist", "bottom", lambda lx, ly, w, h: GLAZE["deep"])
	# belt studs + buckle
	for f, w in (("front", 9), ("back", 9)):
		for lx in range(1, w, 3):
			if f == "front" and 3 <= lx <= 5:
				continue
			s.local("waist", f, lx, 1, GOLD["base"] if mw else GLAZE["deep"])
	buckle_hi = GOLD["highlight"] if mw else T["highlight"]
	buckle = GOLD["base"] if mw else T["base"]
	buckle_sh = GOLD["shadow"] if mw else T["deep"]
	for lx, ly, c in ((3, 0, buckle_hi), (4, 0, buckle_hi), (5, 0, buckle), (3, 1, buckle_hi), (4, 1, CORE if mw else HEAT["hot"]),
			(5, 1, buckle_sh), (3, 2, buckle), (4, 2, buckle_sh), (5, 2, buckle_sh)):
		s.local("waist", "front", lx, ly, c)

	# ---------------- arms: glazed pauldrons, lamellar sleeves, clay hands
	def arm_fn(face):
		def fn(lx, ly, w, h):
			if ly <= 3:  # pauldron
				if ly == 3:
					return GOLD["rim"] if mw else GLAZE["deep"]
				if ly == 0:
					return GLAZE["highlight"]
				seam = lx % 2 == (ly % 2)
				return GLAZE["shadow"] if seam and ly == 2 else GLAZE["base"]
			if ly <= 8:
				return leaf(lamellar(lx, ly - 4, w, h, plate_w=2, stagger=1), lx, ly)
			if ly == 9:  # bracer
				return GOLD["shadow"] if mw else T["deep"]
			if ly == 10:
				return T["highlight"] if face in ("front", "top") and lx in (1, 2) else T["base"]
			# fingers
			return T["shadow"] if lx % 2 else T["base"]
		return fn

	for arm in ("right_arm", "left_arm"):
		for f in ("front", "back", "right", "left"):
			s.region(arm, f, arm_fn(f))
		s.region(arm, "top", lambda lx, ly, w, h: GLAZE["highlight"] if 1 <= lx <= 2 and 1 <= ly <= 2 else GLAZE["base"])
		s.region(arm, "bottom", lambda lx, ly, w, h: T["shadow"])
		if mw:
			s.local(arm, "top", 1, 1, GOLD["highlight"])

	# ---------------- legs: plate skirt, trousers, knee guard, boots
	def leg_fn(face):
		def fn(lx, ly, w, h):
			if ly <= 1:
				return leaf(lamellar(lx, ly, w, h, row_h=2, plate_w=2, stagger=1), lx, ly)
			if ly <= 6:
				return T["shadow"] if lx in (0, 3) and face != "front" else T["base"]
			if ly == 7:  # knee guard
				return (GOLD["base"] if mw else T["highlight"]) if face == "front" else T["shadow"]
			if ly <= 9:
				return T["base"] if face == "front" and lx in (1, 2) else T["shadow"]
			if ly == 10:
				return T["deep"]
			return T["grout"]
		return fn

	for leg in ("right_leg", "left_leg"):
		for f in ("front", "back", "right", "left"):
			s.region(leg, f, leg_fn(f))
		s.region(leg, "top", lambda lx, ly, w, h: T["shadow"])
		s.region(leg, "bottom", lambda lx, ly, w, h: T["grout"] if (lx + ly) % 3 else T["deep"])

	s.noise()

	# ---------------- hairline cracks (after noise so they stay crisp)
	s.crack("head", "front", [(6, 2), (6, 3), (7, 4), (7, 5)])
	s.crack("head", "left", [(2, 3), (3, 4), (3, 5)])
	s.crack("body", "front", [(0, 9), (1, 10), (1, 11)])
	s.crack("body", "front", [(7, 2), (6, 3)])
	s.crack("body", "back", [(5, 4), (6, 5), (6, 6), (7, 7)])
	s.crack("right_arm", "front", [(2, 5), (1, 6), (1, 7)])
	s.crack("left_arm", "back", [(1, 4), (2, 5), (2, 6), (3, 7)])
	s.crack("right_leg", "front", [(2, 2), (2, 3), (3, 4)])
	s.crack("left_leg", "left", [(1, 8), (2, 9)])
	s.crack("head", "top", [(1, 1), (2, 2), (2, 3)])
	return s.img


# ================================================================ GUI
GUI_BG = rgba("#C6C6C6")
GUI_HI = rgba("#FFFFFF")
GUI_SH = rgba("#555555")
GUI_OUT = rgba("#101010")
SLOT_DARK = rgba("#373737")
SLOT_BG = rgba("#8B8B8B")
WELL_DARK = rgba("#2A1A14")

FLAME_MASK = [
	"......#.......",
	"......##......",
	".....###......",
	".....####.....",
	"....#####.#...",
	"...######.##..",
	"...#########..",
	"..##########..",
	"..###########.",
	".############.",
	".############.",
	".############.",
	"..##########..",
	"...########...",
]


def arrow_filled(x, y):
	if x < 15:
		return 6 <= y <= 10
	return abs(y - 8) <= 8 - (x - 15)


def mask_depth(mask, w, h):
	"""Chebyshev-ish distance to the outside of a boolean mask."""
	depth = {}
	for y in range(h):
		for x in range(w):
			if not mask(x, y):
				continue
			d = 0
			while True:
				ok = all(0 <= x + dx < w and 0 <= y + dy < h and mask(x + dx, y + dy)
					for dx, dy in ((d + 1, 0), (-d - 1, 0), (0, d + 1), (0, -d - 1)))
				if not ok:
					break
				d += 1
			depth[(x, y)] = d
	return depth


def draw_well(px, x0, y0, w, h, inner=SLOT_BG):
	for y in range(y0, y0 + h):
		for x in range(x0, x0 + w):
			top_left = (x == x0 or y == y0)
			bot_right = (x == x0 + w - 1 or y == y0 + h - 1)
			if (x, y) in ((x0 + w - 1, y0), (x0, y0 + h - 1)):
				c = SLOT_BG
			elif top_left:
				c = SLOT_DARK
			elif bot_right:
				c = GUI_HI
			else:
				c = inner
			px[x, y] = c


def build_gui():
	img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
	px = img.load()
	W, H = 176, 166
	for y in range(H):
		for x in range(W):
			a, b, c, d = x, y, W - 1 - x, H - 1 - y
			dx, dy = min(a, c), min(b, d)
			layer = min(dx, dy, dx + dy - 2)
			if layer < 0:
				continue
			if layer == 0:
				px[x, y] = GUI_OUT
			elif layer <= 2:
				tl, br = min(a, b), min(c, d)
				px[x, y] = GUI_HI if tl < br else (GUI_SH if br < tl else GUI_BG)
			else:
				px[x, y] = GUI_BG

	# kiln-mouth illustration (muted brick facade with an arched firebox) behind flame + fuel slot
	rng = random.Random(777)
	fx0, fx1, fy0, fy1 = 42, 75, 35, 71
	cx = (fx0 + fx1) / 2.0
	for y in range(fy0, fy1 + 1):
		for x in range(fx0, fx1 + 1):
			# rounded arch top of the facade
			if y < fy0 + 6 and ((x - cx) / 17.0) ** 2 + ((y - (fy0 + 6)) / 6.5) ** 2 > 1.0:
				continue
			row = (y - fy0) // 4
			mortar = (y - fy0) % 4 == 3 or (x - fx0 + (row % 2) * 4) % 8 == 7
			if mortar:
				c = KILN_BRICK["mortar"]
			else:
				c = KILN_BRICK["base"] if rng.random() < 0.7 else (KILN_BRICK["highlight"] if rng.random() < 0.5 else KILN_BRICK["shadow"])
			edge = (x in (fx0, fx1) or y == fy1)
			if edge:
				c = KILN_BRICK["soot"]
			px[x, y] = mix(c, GUI_BG, 0.45)
	# firebox opening behind the flame indicator: arch x46..67, y34..51
	ox0, ox1, oy0, oy1 = 47, 66, 33, 51
	ocx = (ox0 + ox1) / 2.0
	opening = set()
	for y in range(oy0, oy1 + 1):
		for x in range(ox0, ox1 + 1):
			if y < oy0 + 8 and ((x - ocx) / 10.0) ** 2 + ((y - (oy0 + 8)) / 8.5) ** 2 > 1.0:
				continue
			opening.add((x, y))
			t = (y - oy0) / float(oy1 - oy0)
			px[x, y] = mix(rgba("#3A2A24"), rgba("#5A3A2C"), t)
	# voussoir ring (sooty) around the opening
	for (x, y) in sorted(opening):
		for nx, ny in ((x + 1, y), (x - 1, y), (x, y - 1), (x + 1, y - 1), (x - 1, y - 1)):
			if (nx, ny) not in opening:
				px[nx, ny] = mix(KILN_BRICK["soot"], GUI_BG, 0.25)
	for x in range(ox0 - 1, ox1 + 2):
		px[x, oy1 + 1] = mix(KILN_BRICK["soot"], GUI_BG, 0.15)

	# unlit flame silhouette in the firebox (lit sprite is drawn over it bottom-up)
	for y, row in enumerate(FLAME_MASK):
		for x, ch in enumerate(row):
			if ch == "#":
				px[50 + x, 36 + y] = mix(rgba("#6B3420"), rgba("#3A2A24"), 0.25)

	# empty arrow
	for y in range(17):
		for x in range(24):
			if arrow_filled(x, y):
				px[79 + x, 34 + y] = SLOT_BG

	# slots
	for sx, sy in ((38, 17), (62, 17), (50, 53)):
		draw_well(px, sx - 1, sy - 1, 18, 18)
	draw_well(px, 111, 30, 26, 26)
	for row, yy in enumerate((84, 102, 120)):
		for i in range(9):
			draw_well(px, 8 + 18 * i - 1, yy - 1, 18, 18)
	for i in range(9):
		draw_well(px, 8 + 18 * i - 1, 142 - 1, 18, 18)

	# heat gauge frame 12x62 at (151,13) enclosing a 10x60 dark well at (152,14)
	draw_well(px, 151, 13, 12, 62, inner=WELL_DARK)
	# tick marks every 100 deg (6 px), longer at 0/500/1000
	for k in range(11):
		y = 14 + 59 - round(k * 59 / 10.0)
		ln = 3 if k in (0, 5, 10) else 2
		for x in range(164, 164 + ln):
			px[x, y] = GUI_SH
	# tiny thermometer bulb hint on the left of the frame's bottom (decorative): none, needle lives at x=148

	# ---- sprites
	# flame 14x14 at (176,0)
	fm = lambda x, y: FLAME_MASK[y][x] == "#"
	depth = mask_depth(fm, 14, 14)
	ramp = [GLAZES["blaze"]["shadow"], HEAT["hot"], HEAT["hot_light"], GLAZES["blaze"]["highlight"], GLAZES["blaze"]["accent"]]
	for (x, y), d in depth.items():
		lvl = min(d, 4)
		# hotter towards the base
		if y >= 9 and lvl >= 1:
			lvl = min(4, lvl + 1)
		px[176 + x, y] = ramp[lvl]
	# arrow 24x17 at (176,14): white fill with soft grey lower edge (vanilla look)
	for y in range(17):
		for x in range(24):
			if not arrow_filled(x, y):
				continue
			below = arrow_filled(x, y + 1) if y < 16 else False
			right = arrow_filled(x + 1, y) if x < 23 else False
			px[176 + x, 14 + y] = rgba("#E6E6E6") if (not below or not right) else GUI_HI
	return img


# ================================================================ PARTICLES
def build_embers():
	frames = []
	layouts = [
		["........",
		 "...h....",
		 "..hwh...",
		 ".hwcwh..",
		 "..hwh...",
		 "...h....",
		 "........",
		 "........"],
		["........",
		 "........",
		 "...g....",
		 "..gwh...",
		 "...hg...",
		 "........",
		 "........",
		 "........"],
		["........",
		 "........",
		 "........",
		 "...rg...",
		 "...gr...",
		 "........",
		 "........",
		 "........"],
	]
	legend = {"c": HEAT["core"], "w": HEAT["white"], "h": HEAT["hot_light"], "g": HEAT["hot"], "r": rgba("#A8401A")}
	for rows in layouts:
		img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
		for y, row in enumerate(rows):
			for x, ch in enumerate(row):
				if ch != ".":
					img.putpixel((x, y), legend[ch])
		frames.append(img)
	return frames


# ================================================================ ICON
def build_icon():
	n = 32
	img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
	px = img.load()
	rng = random.Random(1312)
	# rounded-square brick kiln facade
	for y in range(n):
		for x in range(n):
			dx, dy = min(x, n - 1 - x), min(y, n - 1 - y)
			if min(dx, dy, dx + dy - 2) < 0:
				continue
			row = y // 4
			mortar = y % 4 == 3 or (x + (row % 2) * 4) % 8 == 7
			if mortar:
				c = KILN_BRICK["mortar"]
				c = mix(c, KILN_BRICK["shadow"], 0.35)
			else:
				r = rng.random()
				c = KILN_BRICK["base"] if r < 0.65 else (KILN_BRICK["highlight"] if r < 0.85 else KILN_BRICK["shadow"])
			# top-left light, bottom-right soot
			if x + y > 44:
				c = mix(c, KILN_BRICK["soot"], 0.35)
			elif x + y < 12:
				c = mix(c, KILN_BRICK["highlight"], 0.3)
			if min(dx, dy, dx + dy - 2) == 0:
				c = OUTLINE
			px[x, y] = c
	# arched mouth x6..25, y6..27
	ox0, ox1, oy0, oy1 = 6, 25, 6, 27
	ocx = (ox0 + ox1) / 2.0
	opening = set()
	for y in range(oy0, oy1 + 1):
		for x in range(ox0, ox1 + 1):
			if y < oy0 + 10 and ((x - ocx) / 10.0) ** 2 + ((y - (oy0 + 10)) / 10.0) ** 2 > 1.0:
				continue
			opening.add((x, y))
	cx, cy = ocx, 21.0
	for (x, y) in opening:
		d = (((x - cx) / 1.0) ** 2 + ((y - cy) / 1.25) ** 2) ** 0.5
		if d < 3.5:
			c = HEAT["core"]
		elif d < 6:
			c = HEAT["white"]
		elif d < 8.5:
			c = GLAZES["blaze"]["highlight"]
		elif d < 11:
			c = HEAT["hot_light"]
		elif d < 13.5:
			c = HEAT["hot"]
		else:
			c = HEAT["warm"]
		px[x, y] = c
	# voussoir ring around the opening
	for (x, y) in list(opening):
		for nx, ny in ((x + 1, y), (x - 1, y), (x, y - 1), (x + 1, y - 1), (x - 1, y - 1)):
			if (nx, ny) not in opening and 0 < nx < n - 1 and 0 < ny < n - 1:
				px[nx, ny] = KILN_BRICK["soot"]
	# keystone
	for x in (15, 16):
		px[x, 4] = KILN_BRICK["highlight"]
		px[x, 5] = KILN_BRICK["soot"]
	# sill
	for x in range(ox0 - 1, ox1 + 2):
		px[x, oy1 + 1] = KILN_BRICK["soot"]
		px[x, oy1 + 2] = KILN_BRICK["shadow"] if x % 3 else KILN_BRICK["soot"]
	# glazed lapis jar silhouette standing on the sill, backlit
	L = GLAZES["lapis"]
	jar = [
		"....rrrr....",
		"....GGGG....",
		".....dd.....",
		"....dbbd....",
		"..dbbbbbbd..",
		".dhbbbbbbsd.",
		".dhbbbbbbsd.",
		"dhhbbbbbbssd",
		"dhbbbbbbbssd",
		"dhbbbbbbbssd",
		"dhbbbbbbbssd",
		".dbbbbbbssd.",
		".dbbbbbbssd.",
		"..dsssssdd..",
		"...dddddd...",
	]
	jl = {"r": GOLD["highlight"], "G": GOLD["rim"], "d": L["deep"], "b": L["base"], "h": L["highlight"], "s": L["shadow"]}
	jx0, jy0 = 10, oy1 + 1 - len(jar)
	cells = set()
	for ry, row in enumerate(jar):
		for rx, ch in enumerate(row):
			if ch != ".":
				px[jx0 + rx, jy0 + ry] = jl[ch]
				cells.add((jx0 + rx, jy0 + ry))
	# dark silhouette outline so the jar reads against the glow (backlit)
	for (x, y) in sorted(cells):
		for nx, ny in ((x + 1, y), (x - 1, y), (x, y - 1)):
			if (nx, ny) not in cells and ny <= oy1:
				px[nx, ny] = OUTLINE
	# rim light from the fire on the jar edges facing the glow
	for ry, row in enumerate(jar):
		for rx, ch in enumerate(row):
			if ch == "d" and ry in (4, 5, 6) and rx in (1, 2, 9, 10):
				px[jx0 + rx, jy0 + ry] = mix(L["deep"], HEAT["hot_light"], 0.45)
	# a specular glint
	px[jx0 + 3, jy0 + 6] = L["accent"]
	px[jx0 + 3, jy0 + 7] = L["accent"]
	# rising embers
	for ex, ey, c in ((22, 9, HEAT["white"]), (23, 8, HEAT["hot_light"]), (8, 11, HEAT["hot_light"]), (20, 13, HEAT["core"])):
		px[ex, ey] = c
	return img.resize((128, 128), Image.NEAREST)


# ================================================================ PREVIEWS
def unwrap_check(skin, scale=8):
	"""Rebuild front / back / right-side / top views from the face regions (verifies UV placement)."""
	# part -> (box origin x,y,z in model space incl. pivot)
	geo = {
		"head": ((-4, -8, -4), (8, 8, 8)),
		"crest": ((-1, -11, -3), (2, 3, 5)),
		"body": ((-4, 0, -2), (8, 12, 4)),
		"right_arm": ((-8, 0, -2), (4, 12, 4)),
		"left_arm": ((4, 0, -2), (4, 12, 4)),
		"right_leg": ((-3.9, 12, -2), (4, 12, 4)),
		"left_leg": ((-0.1, 12, -2), (4, 12, 4)),
		"waist": ((-4.5, 10, -2.5), (9, 3, 5)),
	}
	order = ["right_leg", "left_leg", "body", "waist", "right_arm", "left_arm", "head", "crest"]
	W, Hh = 24, 36
	views = []
	for view in ("front", "back", "right", "left", "top"):
		canvas = Image.new("RGBA", (W, Hh), (60, 70, 90, 255))
		for part in order:
			(ox, oy, oz), (w, h, d) = geo[part]
			fx, fy, fw, fh = faces(part)[view]
			tile = skin.crop((fx, fy, fx + fw, fy + fh))
			if view == "front":
				px, py = ox + 12, oy + 11
			elif view == "back":
				tile = tile  # back face is unwrapped as seen from behind
				px, py = 12 - (ox + w), oy + 11
			elif view == "right":  # seen from the entity's right (-X): front (-Z) on the image's right
				px, py = 12 - (oz + d), oy + 11
			elif view == "left":  # seen from +X: front (-Z) on the image's left
				px, py = oz + 12, oy + 11
			else:  # top: x across, z down (back at top)
				px, py = ox + 12, oz + 12
				if part not in ("head", "crest", "right_arm", "left_arm"):
					continue
				if part == "crest":
					py = oz + 12
			canvas.alpha_composite(tile, (int(round(px)), int(round(py))))
		views.append((view, canvas.resize((W * scale, Hh * scale), Image.NEAREST)))
	return views


def label(draw, xy, text):
	draw.text(xy, text, fill=(255, 255, 255, 255))


def previews(out_dir, skins, gui, embers, icon):
	os.makedirs(out_dir, exist_ok=True)
	sheet = Image.new("RGBA", (2 * 64 * 8 + 3 * 16 + 5 * 24 * 6, 64 * 8 + 40), (40, 44, 52, 255))
	d = ImageDraw.Draw(sheet)
	x = 16
	for name, sk in skins:
		sheet.alpha_composite(sk.resize((512, 512), Image.NEAREST), (x, 24))
		# grid lines for every part's face regions
		for part in PARTS:
			for fname, (fx, fy, fw, fh) in faces(part).items():
				d.rectangle((x + fx * 8, 24 + fy * 8, x + (fx + fw) * 8 - 1, 24 + (fy + fh) * 8 - 1), outline=(80, 255, 120, 110))
		label(d, (x, 6), name)
		x += 528
	sheet.save(os.path.join(out_dir, "entity_gui_skins.png"))

	# unwrap check sheet
	rows = []
	for name, sk in skins:
		rows.append((name, unwrap_check(sk)))
	uw = Image.new("RGBA", (5 * (24 * 8 + 12) + 12, len(rows) * (36 * 8 + 30) + 10), (30, 30, 36, 255))
	d = ImageDraw.Draw(uw)
	for r, (name, views) in enumerate(rows):
		for c, (vname, im) in enumerate(views):
			ox, oy = 12 + c * (24 * 8 + 12), 24 + r * (36 * 8 + 30)
			uw.alpha_composite(im, (ox, oy))
			label(d, (ox, oy - 14), "%s %s" % (name, vname))
	uw.save(os.path.join(out_dir, "entity_gui_unwrap.png"))

	# gui + particles + icon
	g = Image.new("RGBA", (256 * 3 + 40 + 128 * 2 + 20, 256 * 3 + 30), (40, 44, 52, 255))
	d = ImageDraw.Draw(g)
	g.alpha_composite(gui.resize((768, 768), Image.NEAREST), (10, 24))
	label(d, (10, 6), "kiln.png x3")
	g.alpha_composite(icon.resize((256, 256), Image.NEAREST), (800, 24))
	label(d, (800, 6), "icon x2")
	for i, e in enumerate(embers):
		g.alpha_composite(Image.new("RGBA", (80, 80), (20, 20, 24, 255)), (800 + i * 88, 320))
		g.alpha_composite(e.resize((80, 80), Image.NEAREST), (800 + i * 88, 320))
		label(d, (800 + i * 88, 404), "ember_%d" % i)
	# simulated in-game screen: lit flame + 60% arrow + gauge fill
	sim = gui.crop((0, 0, 176, 166)).copy()
	sim.alpha_composite(gui.crop((176, 0, 190, 14)), (50, 36))
	sim.alpha_composite(gui.crop((176, 14, 176 + 15, 31)), (79, 34))
	sd = ImageDraw.Draw(sim)
	sd.rectangle((152, 14 + 60 - 36, 161, 14 + 60 - 31), fill=(63, 163, 77, 255))
	g.alpha_composite(sim.resize((352, 332), Image.NEAREST), (800, 430))
	label(d, (800, 418), "simulated screen x2")
	g.save(os.path.join(out_dir, "entity_gui.png"))


def main():
	skins = [("terracotta_sentinel", build_skin(False)), ("terracotta_sentinel_masterwork", build_skin(True))]
	for name, sk in skins:
		save(sk, os.path.join("textures", "entity", name + ".png"))
	gui = build_gui()
	save(gui, os.path.join("textures", "gui", "container", "kiln.png"))
	embers = build_embers()
	for i, e in enumerate(embers):
		save(e, os.path.join("textures", "particle", "kiln_ember_%d.png" % i))
	write_json(os.path.join("particles", "kiln_ember.json"),
		{"textures": ["fireclay:kiln_ember_0", "fireclay:kiln_ember_1", "fireclay:kiln_ember_2"]})
	icon = build_icon()
	save(icon, "icon.png")
	if len(sys.argv) > 1:
		previews(sys.argv[1], skins, gui, embers, icon)
	for p in WRITTEN:
		print(p)


if __name__ == "__main__":
	main()
