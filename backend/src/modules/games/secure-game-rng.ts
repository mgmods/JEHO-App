import { randomBytes } from 'crypto';

/**
 * Uniform cryptographic random value in [0, 1).
 * Use for server-authoritative game outcomes instead of Math.random().
 */
export function secureGameRandom(): number {
  const bytes = randomBytes(6);
  return bytes.readUIntBE(0, 6) / 0x1_0000_0000_0000;
}
