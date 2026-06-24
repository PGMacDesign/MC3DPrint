// Block -> RGB lookup for the massing preview.
//
// Step 2 replaces this with data/block_colors.json (a datagen dump of the real
// in-game MapColor for every block). Until that lands, this starter table + a
// few substring heuristics keep builds recognizable. Unknown ids fall back to
// loud magenta so "this block isn't recognized" is visible at a glance.

export const UNKNOWN = [255, 0, 255];

let override = null; // populated from block_colors.json when available

// Minecraft's 16 dye colors, reused for wool/concrete/terracotta/glass families.
const DYE = {
  white: [233, 236, 236], orange: [240, 118, 19], magenta: [189, 68, 179],
  light_blue: [58, 175, 217], yellow: [248, 198, 39], lime: [112, 185, 25],
  pink: [237, 141, 172], gray: [62, 68, 71], light_gray: [142, 142, 134],
  cyan: [21, 137, 145], purple: [121, 42, 172], blue: [53, 57, 157],
  brown: [114, 71, 40], green: [84, 109, 27], red: [161, 39, 34], black: [20, 21, 25],
};

const EXACT = {
  'minecraft:stone': [125, 125, 125], 'minecraft:cobblestone': [122, 122, 122],
  'minecraft:stone_bricks': [122, 121, 121], 'minecraft:chiseled_stone_bricks': [120, 119, 119],
  'minecraft:mossy_cobblestone': [110, 118, 100], 'minecraft:smooth_stone': [158, 158, 158],
  'minecraft:andesite': [136, 136, 137], 'minecraft:granite': [149, 103, 86],
  'minecraft:diorite': [188, 188, 189], 'minecraft:deepslate': [77, 77, 80],
  'minecraft:dirt': [134, 96, 67], 'minecraft:grass_block': [95, 159, 53],
  'minecraft:sand': [219, 207, 163], 'minecraft:sandstone': [216, 203, 155],
  'minecraft:gravel': [136, 126, 124], 'minecraft:bricks': [150, 97, 83],
  'minecraft:glowstone': [203, 169, 92], 'minecraft:bookshelf': [124, 96, 56],
  'minecraft:water': [63, 118, 228], 'minecraft:lava': [217, 108, 30],
  'minecraft:glass': [197, 224, 230], 'minecraft:white_stained_glass': [233, 236, 236],
  'minecraft:obsidian': [20, 18, 30], 'minecraft:bedrock': [85, 85, 85],
  'minecraft:netherrack': [97, 38, 38], 'minecraft:end_stone': [219, 222, 158],
  'minecraft:quartz_block': [235, 229, 222], 'minecraft:gold_block': [246, 208, 61],
  'minecraft:iron_block': [220, 220, 220], 'minecraft:diamond_block': [108, 224, 213],
  'minecraft:emerald_block': [42, 203, 96], 'minecraft:hay_block': [165, 139, 12],
  'minecraft:torch': [255, 214, 110], 'minecraft:lantern': [240, 180, 90],
  'minecraft:chain': [60, 62, 70], 'minecraft:bell': [222, 178, 60],
  'minecraft:ladder': [129, 97, 56],
};

// Wood families -> a representative plank/log brown.
const WOOD = {
  oak: [162, 130, 78], spruce: [114, 84, 48], birch: [196, 178, 123],
  jungle: [160, 115, 80], acacia: [168, 90, 50], dark_oak: [66, 43, 20],
  mangrove: [122, 54, 50], cherry: [226, 184, 188], crimson: [123, 57, 81], warped: [44, 116, 112],
};

function heuristic(id) {
  const path = id.split(':')[1] || id;
  for (const [name, rgb] of Object.entries(DYE)) {
    if (path.startsWith(name + '_')) return rgb; // <color>_wool, _concrete, _terracotta...
  }
  for (const [wood, rgb] of Object.entries(WOOD)) {
    if (path.startsWith(wood + '_') || path === wood + '_log') return rgb;
  }
  if (/leaves$/.test(path)) return [60, 143, 42];
  if (/(log|wood|planks|fence|stairs|slab|door|trapdoor|sign)/.test(path)) return [150, 116, 70];
  if (/(stone|cobble|brick|deepslate|tuff|basalt)/.test(path)) return [125, 125, 125];
  if (/glass/.test(path)) return [197, 224, 230];
  if (/(water|ice|prismarine)/.test(path)) return [63, 118, 200];
  if (/(wool|carpet|concrete|terracotta)/.test(path)) return [150, 150, 150];
  return null;
}

export function blockColor(id) {
  if (override && override[id]) return override[id];
  if (EXACT[id]) return EXACT[id];
  const h = heuristic(id);
  return h || UNKNOWN;
}

export function isUnknown(id) {
  return !(override && override[id]) && !EXACT[id] && !heuristic(id);
}

// Best-effort: prefer the datagen table if it has been deployed. Silently keeps
// the starter table when the file is absent (e.g. running before step 2).
export async function loadColorTable(url = 'data/block_colors.json') {
  try {
    const res = await fetch(url);
    if (res.ok) override = await res.json();
  } catch (_) { /* keep starter table */ }
}
