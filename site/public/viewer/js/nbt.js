// Minimal big-endian NBT reader. Input is the already-inflated byte buffer
// (the caller gunzips). Returns the root compound as a plain object; lists and
// int/long/byte arrays become JS arrays/typed-arrays. Java strings are modified
// UTF-8, but block ids and names are ASCII, so a plain UTF-8 decode is exact for
// our payloads.

const TAG_END = 0, TAG_COMPOUND = 10;

export function parseNbt(bytes) {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const dec = new TextDecoder('utf-8');
  let off = 0;

  const i8  = () => view.getInt8(off++);
  const u8  = () => view.getUint8(off++);
  const u16 = () => { const v = view.getUint16(off); off += 2; return v; };
  const i16 = () => { const v = view.getInt16(off); off += 2; return v; };
  const i32 = () => { const v = view.getInt32(off); off += 4; return v; };
  const i64 = () => { const v = view.getBigInt64(off); off += 8; return v; };
  const f32 = () => { const v = view.getFloat32(off); off += 4; return v; };
  const f64 = () => { const v = view.getFloat64(off); off += 8; return v; };
  const str = () => {
    const len = u16();
    const s = dec.decode(new Uint8Array(bytes.buffer, bytes.byteOffset + off, len));
    off += len;
    return s;
  };

  function payload(type) {
    switch (type) {
      case 1: return i8();
      case 2: return i16();
      case 3: return i32();
      case 4: return i64();
      case 5: return f32();
      case 6: return f64();
      case 7: { const n = i32(); const a = new Int8Array(n); for (let k = 0; k < n; k++) a[k] = i8(); return a; }
      case 8: return str();
      case 9: { const et = u8(); const n = i32(); const a = []; for (let k = 0; k < n; k++) a.push(payload(et)); return a; }
      case 10: { const o = {}; for (;;) { const t = u8(); if (t === TAG_END) break; o[str()] = payload(t); } return o; }
      case 11: { const n = i32(); const a = new Int32Array(n); for (let k = 0; k < n; k++) a[k] = i32(); return a; }
      case 12: { const n = i32(); const a = new BigInt64Array(n); for (let k = 0; k < n; k++) a[k] = i64(); return a; }
      default: throw new Error(`Unknown NBT tag ${type} at byte ${off - 1}`);
    }
  }

  if (u8() !== TAG_COMPOUND) throw new Error('NBT root is not a compound');
  str(); // root name (unused)
  return payload(TAG_COMPOUND);
}
