/** Mikoo hash-game wire framing: BE uint16 outerLen + BE uint16 nameLen + name + body. */

export function packFrame(name: string, body: Buffer): Buffer {
  const nameBuf = Buffer.from(name, 'utf8');
  const innerLen = 2 + nameBuf.length + body.length;
  const out = Buffer.alloc(2 + innerLen);
  out.writeUInt16BE(innerLen, 0);
  out.writeUInt16BE(nameBuf.length, 2);
  nameBuf.copy(out, 4);
  body.copy(out, 4 + nameBuf.length);
  return out;
}

export function unpackFrame(buf: Buffer): { name: string; body: Buffer } | null {
  if (buf.length < 4) return null;
  const outerLen = buf.readUInt16BE(0);
  if (buf.length !== outerLen + 2) return null;
  const nameLen = buf.readUInt16BE(2);
  if (4 + nameLen > buf.length) return null;
  const name = buf.slice(4, 4 + nameLen).toString('utf8');
  const body = buf.slice(4 + nameLen);
  return { name, body };
}
