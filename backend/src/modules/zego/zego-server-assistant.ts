/**
 * Official ZEGOCLOUD Token04 generator (from zego_server_assistant).
 * Uses ServerSecret (32-byte string) — never ship this in the Android app.
 */
import { createCipheriv } from 'crypto';

enum ErrorCode {
  success = 0,
  appIDInvalid = 1,
  userIDInvalid = 3,
  secretInvalid = 5,
  effectiveTimeInSecondsInvalid = 6,
}

function rndNum(a: number, b: number): number {
  return Math.ceil((a + (b - a)) * Math.random());
}

function makeRandomIv(): string {
  const str = '0123456789abcdefghijklmnopqrstuvwxyz';
  let result = '';
  for (let i = 0; i < 16; i++) {
    result += str.charAt(Math.floor(Math.random() * str.length));
  }
  return result;
}

function getAlgorithm(key: Buffer): string {
  switch (key.length) {
    case 16:
      return 'aes-128-cbc';
    case 24:
      return 'aes-192-cbc';
    case 32:
      return 'aes-256-cbc';
    default:
      throw new Error('Invalid key length: ' + key.length);
  }
}

function aesEncrypt(plainText: string, key: string, iv: string): Buffer {
  const keyBuf = Buffer.from(key);
  const cipher = createCipheriv(getAlgorithm(keyBuf), keyBuf, Buffer.from(iv));
  cipher.setAutoPadding(true);
  return Buffer.concat([cipher.update(plainText, 'utf8'), cipher.final()]);
}

export function generateToken04(
  appId: number,
  userId: string,
  secret: string,
  effectiveTimeInSeconds: number,
  payload = '',
): string {
  if (!appId || typeof appId !== 'number') {
    throw { errorCode: ErrorCode.appIDInvalid, errorMessage: 'appID invalid' };
  }
  if (!userId || typeof userId !== 'string') {
    throw { errorCode: ErrorCode.userIDInvalid, errorMessage: 'userId invalid' };
  }
  if (!secret || typeof secret !== 'string' || secret.length !== 32) {
    throw {
      errorCode: ErrorCode.secretInvalid,
      errorMessage: 'secret must be a 32 byte string',
    };
  }
  if (!effectiveTimeInSeconds || typeof effectiveTimeInSeconds !== 'number') {
    throw {
      errorCode: ErrorCode.effectiveTimeInSecondsInvalid,
      errorMessage: 'effectiveTimeInSeconds invalid',
    };
  }

  const createTime = Math.floor(Date.now() / 1000);
  const tokenInfo = {
    app_id: appId,
    user_id: userId,
    nonce: rndNum(-2147483648, 2147483647),
    ctime: createTime,
    expire: createTime + effectiveTimeInSeconds,
    payload: payload || '',
  };

  const plainText = JSON.stringify(tokenInfo);
  const iv = makeRandomIv();
  const encryptBuf = aesEncrypt(plainText, secret, iv);

  const b1 = Buffer.alloc(8);
  b1.writeBigInt64BE(BigInt(tokenInfo.expire));
  const b2 = Buffer.alloc(2);
  b2.writeUInt16BE(iv.length);
  const b3 = Buffer.alloc(2);
  b3.writeUInt16BE(encryptBuf.length);

  const buf = Buffer.concat([b1, b2, Buffer.from(iv), b3, encryptBuf]);
  return '04' + buf.toString('base64');
}
