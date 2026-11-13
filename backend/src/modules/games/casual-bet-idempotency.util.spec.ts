import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';
import { isSameCasualPlayRequest } from './casual-bet-idempotency.util';

describe('casual game play idempotency identity', () => {
  const original = {
    gameId: 'roulette',
    amount: 500,
    roomId: 'room-1',
    choice: 'red',
  };

  it('accepts a retry with the exact same payload', () => {
    assert.equal(isSameCasualPlayRequest(original, original), true);
  });

  it('rejects reuse with a different amount or game', () => {
    assert.equal(isSameCasualPlayRequest(original, { ...original, amount: 1000 }), false);
    assert.equal(isSameCasualPlayRequest(original, { ...original, gameId: 'slots' }), false);
  });

  it('rejects reuse with a different choice or room', () => {
    assert.equal(isSameCasualPlayRequest(original, { ...original, choice: 'black' }), false);
    assert.equal(isSameCasualPlayRequest(original, { ...original, roomId: 'room-2' }), false);
  });
});
