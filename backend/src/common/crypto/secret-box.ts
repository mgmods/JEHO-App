import * as crypto from 'crypto';

const ALGO = 'aes-256-gcm';
const IV_LEN = 12;

export class SecretBoxError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'SecretBoxError';
  }
}

function parseEncryptionKey(raw: string | undefined): Buffer {
  if (!raw || !raw.trim()) {
    throw new SecretBoxError(
      'SETTINGS_ENCRYPTION_KEY is required to encrypt payment secrets. ' +
        'Set a 32-byte key as 64-character hex or base64.',
    );
  }

  const trimmed = raw.trim();
  if (/^[0-9a-fA-F]{64}$/.test(trimmed)) {
    return Buffer.from(trimmed, 'hex');
  }

  const fromBase64 = Buffer.from(trimmed, 'base64');
  if (fromBase64.length === 32) {
    return fromBase64;
  }

  throw new SecretBoxError(
    'SETTINGS_ENCRYPTION_KEY must decode to exactly 32 bytes (64 hex chars or base64 of 32 bytes).',
  );
}

export function encryptSecret(plaintext: string, keyRaw?: string): string {
  const key = parseEncryptionKey(keyRaw ?? process.env.SETTINGS_ENCRYPTION_KEY);
  const iv = crypto.randomBytes(IV_LEN);
  const cipher = crypto.createCipheriv(ALGO, key, iv);
  const encrypted = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();

  return JSON.stringify({
    iv: iv.toString('base64'),
    tag: tag.toString('base64'),
    data: encrypted.toString('base64'),
  });
}

export function decryptSecret(payload: string, keyRaw?: string): string {
  const key = parseEncryptionKey(keyRaw ?? process.env.SETTINGS_ENCRYPTION_KEY);

  let parsed: { iv?: string; tag?: string; data?: string };
  try {
    parsed = JSON.parse(payload) as { iv?: string; tag?: string; data?: string };
  } catch {
    throw new SecretBoxError('Invalid encrypted secret payload');
  }

  if (!parsed.iv || !parsed.tag || !parsed.data) {
    throw new SecretBoxError('Invalid encrypted secret payload');
  }

  const decipher = crypto.createDecipheriv(ALGO, key, Buffer.from(parsed.iv, 'base64'));
  decipher.setAuthTag(Buffer.from(parsed.tag, 'base64'));
  return Buffer.concat([
    decipher.update(Buffer.from(parsed.data, 'base64')),
    decipher.final(),
  ]).toString('utf8');
}
