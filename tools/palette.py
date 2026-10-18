"""Shared Fireclay palette (hex strings -> RGBA tuples). Used by every asset generator."""


def rgba(hex_color, alpha=255):
	h = hex_color.lstrip("#")
	return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), alpha)


OUTLINE = rgba("#2B1D17")
TRANSPARENT = (0, 0, 0, 0)

RAW_CLAY = {"base": rgba("#A4A9B8"), "shadow": rgba("#7E8496"), "deep": rgba("#5F6577"), "highlight": rgba("#C5CAD6"), "sheen": rgba("#DDE2EC")}
FIRECLAY = {"base": rgba("#D9C49A"), "shadow": rgba("#B39D72"), "deep": rgba("#8F7A52"), "highlight": rgba("#EDE0BE"), "grog": rgba("#8A6E4B")}
TERRACOTTA = {"base": rgba("#B5603A"), "shadow": rgba("#8E4527"), "deep": rgba("#6B3420"), "highlight": rgba("#D07A4F"), "grout": rgba("#5A3A2C")}
KILN_BRICK = {"base": rgba("#9A4B35"), "shadow": rgba("#7C3A28"), "soot": rgba("#4A2A20"), "highlight": rgba("#B4644A"), "mortar": rgba("#B7A99A")}

GLAZES = {
	"lapis": {"base": rgba("#2F5BD3"), "shadow": rgba("#1F3FA8"), "deep": rgba("#142A73"), "highlight": rgba("#5C86F0"), "accent": rgba("#A9C2FF")},
	"glowink": {"base": rgba("#3FD6C4"), "shadow": rgba("#2A9E92"), "deep": rgba("#1B6B63"), "highlight": rgba("#8EF5E6"), "accent": rgba("#D8FFF8")},
	"amethyst": {"base": rgba("#8D5CC9"), "shadow": rgba("#6A3FA0"), "deep": rgba("#4A2A73"), "highlight": rgba("#B98AE8"), "accent": rgba("#E2C9FF")},
	"blaze": {"base": rgba("#E87A1E"), "shadow": rgba("#C2410C"), "deep": rgba("#7A2808"), "highlight": rgba("#FFB347"), "accent": rgba("#FFE08A")},
	"echo": {"base": rgba("#136E80"), "shadow": rgba("#0E4A5A"), "deep": rgba("#052C36"), "highlight": rgba("#29DFEB"), "accent": rgba("#A6FBFF")},
}

HEAT = {"cold": rgba("#2B1D17"), "warm": rgba("#7A2E12"), "hot": rgba("#E0601A"), "hot_light": rgba("#FF8C2A"), "white": rgba("#FFE9A8"), "core": rgba("#FFF8E0")}
GOLD = {"base": rgba("#F2C94C"), "shadow": rgba("#C99A2E"), "highlight": rgba("#FFF0A8"), "rim": rgba("#E5B83A")}
IRON = {"base": rgba("#A8A8A8"), "shadow": rgba("#6E6E6E"), "highlight": rgba("#D8D8D8")}
COPPER = {"base": rgba("#C36A35"), "shadow": rgba("#8B4A26"), "highlight": rgba("#E3895B")}
GLASS = rgba("#CFE8F0")
OAK = {"base": rgba("#A8834F"), "shadow": rgba("#8A6A3B"), "deep": rgba("#6B5230"), "highlight": rgba("#BE9A63")}
LEATHER = {"base": rgba("#7A4A2B"), "shadow": rgba("#5E3720"), "highlight": rgba("#9C6640")}
STONE = {"base": rgba("#A0A0A0"), "ring": rgba("#7F7F7F"), "spoke": rgba("#5E5E5E"), "highlight": rgba("#BDBDBD")}
MEMORY_VEIN = rgba("#7FD3FF")
