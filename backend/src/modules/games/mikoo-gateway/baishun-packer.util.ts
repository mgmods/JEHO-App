/** BaiShun Packer framing (swimsuit-party and similar). Big-endian by default. */

const SIZE_BYTES = 4;
const HEADER_BYTES = 1;
const ROUTE_BYTES = 4;
const SEQ_BYTES = 4;
const TIMESTAMP_BYTES = 8;
const HEARTBEAT_FLAG = 0x80;

export type PackerMessage = {
  isHeartbeat: boolean;
  seq: number;
  route: number;
  buffer: Buffer;
  millisecond?: number;
};

export function packMessage(route: number, seq: number, body: Buffer = Buffer.alloc(0)): Buffer {
  const payloadLen = HEADER_BYTES + ROUTE_BYTES + SEQ_BYTES + body.length;
  const out = Buffer.alloc(SIZE_BYTES + payloadLen);
  out.writeUInt32BE(payloadLen, 0);
  out.writeUInt8(0, SIZE_BYTES);
  out.writeInt32BE(route | 0, SIZE_BYTES + HEADER_BYTES);
  out.writeInt32BE(seq | 0, SIZE_BYTES + HEADER_BYTES + ROUTE_BYTES);
  if (body.length) body.copy(out, SIZE_BYTES + HEADER_BYTES + ROUTE_BYTES + SEQ_BYTES);
  return out;
}

/** Server→client heartbeat includes timestamp (ms) + seq. */
export function packHeartbeat(seq: number, millisecond = Date.now()): Buffer {
  const payloadLen = HEADER_BYTES + TIMESTAMP_BYTES + SEQ_BYTES;
  const out = Buffer.alloc(SIZE_BYTES + payloadLen);
  out.writeUInt32BE(payloadLen, 0);
  out.writeUInt8(HEARTBEAT_FLAG, SIZE_BYTES);
  out.writeBigUInt64BE(BigInt(Math.max(0, Math.floor(millisecond))), SIZE_BYTES + HEADER_BYTES);
  out.writeInt32BE(seq | 0, SIZE_BYTES + HEADER_BYTES + TIMESTAMP_BYTES);
  return out;
}

export function unpackMessage(buf: Buffer): PackerMessage | null {
  if (!buf || buf.length < SIZE_BYTES + HEADER_BYTES) return null;
  const size = buf.readUInt32BE(0);
  if (buf.length < SIZE_BYTES + size) return null;
  let offset = SIZE_BYTES;
  const header = buf.readUInt8(offset);
  offset += HEADER_BYTES;
  const isHeartbeat = (header & HEARTBEAT_FLAG) === HEARTBEAT_FLAG;

  if (isHeartbeat) {
    // Client→server: [size][0x80][seq] (no timestamp)
    // Server→client style also accepted: [size][0x80][ts][seq]
    const remaining = SIZE_BYTES + size - offset;
    if (remaining >= TIMESTAMP_BYTES + SEQ_BYTES) {
      const millisecond = Number(buf.readBigUInt64BE(offset));
      offset += TIMESTAMP_BYTES;
      const seq = buf.readInt32BE(offset);
      return { isHeartbeat: true, seq, route: 0, buffer: Buffer.alloc(0), millisecond };
    }
    if (remaining >= SEQ_BYTES) {
      const seq = buf.readInt32BE(offset);
      return { isHeartbeat: true, seq, route: 0, buffer: Buffer.alloc(0) };
    }
    return { isHeartbeat: true, seq: 0, route: 0, buffer: Buffer.alloc(0) };
  }

  if (buf.length < offset + ROUTE_BYTES + SEQ_BYTES) return null;
  const route = buf.readInt32BE(offset);
  offset += ROUTE_BYTES;
  const seq = buf.readInt32BE(offset);
  offset += SEQ_BYTES;
  const end = SIZE_BYTES + size;
  const body = offset < end ? buf.subarray(offset, end) : Buffer.alloc(0);
  return { isHeartbeat: false, seq, route, buffer: Buffer.from(body) };
}
