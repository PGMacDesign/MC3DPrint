// Decodes a parsed NBT root (see nbt.js) into a Blueprint, mirroring the Java
// BlueprintSerializer keys and the dense-array index formula exactly:
//   index(x,y,z) = (y*sizeZ + z)*sizeX + x ,  value = palette index or -1 (empty)

const NO_BLOCK = -1;

function parsePaletteEntry(serialized) {
  const bracket = serialized.indexOf('[');
  if (bracket < 0) return { id: normalizeId(serialized.trim()), props: {} };
  const id = normalizeId(serialized.slice(0, bracket).trim());
  const body = serialized.slice(bracket + 1, serialized.lastIndexOf(']')).trim();
  const props = {};
  if (body) for (const pair of body.split(',')) {
    const eq = pair.indexOf('=');
    props[pair.slice(0, eq).trim()] = pair.slice(eq + 1).trim();
  }
  return { id, props };
}

function normalizeId(id) {
  return id.includes(':') ? id : 'minecraft:' + id;
}

export function decodeBlueprint(root) {
  const size = root.Size;
  if (!size || size.length !== 3) throw new Error('Blueprint Size missing or malformed');
  const sx = size[0], sy = size[1], sz = size[2];
  if (sx <= 0 || sy <= 0 || sz <= 0 || sx * sy * sz > 12_000_000) {
    throw new Error(`implausible blueprint volume ${sx}x${sy}x${sz}`);
  }

  const blocks = root.Blocks;
  if (!blocks || blocks.length !== sx * sy * sz) {
    throw new Error(`Blocks length ${blocks ? blocks.length : 0} != volume ${sx}x${sy}x${sz}`);
  }

  const palette = (root.Palette || []).map(parsePaletteEntry);
  let blockCount = 0;
  for (let i = 0; i < blocks.length; i++) if (blocks[i] !== NO_BLOCK) blockCount++;

  return {
    name: root.Name || '(unnamed)',
    version: root.Version,
    sx, sy, sz,
    palette,
    blocks,
    blockCount,
    NO_BLOCK,
    at(x, y, z) {
      const idx = blocks[(y * sz + z) * sx + x];
      return idx === NO_BLOCK ? null : palette[idx];
    },
  };
}
