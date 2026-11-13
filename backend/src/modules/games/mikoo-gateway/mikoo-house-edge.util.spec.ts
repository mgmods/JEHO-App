import { strict as assert } from 'node:assert';
import { describe, it } from 'node:test';
import { calculateAreaOutcomeWeights } from './mikoo-house-edge.util';

function expectedEqualStakeReturn(multipliers: number[], weights: number[]) {
  return multipliers.reduce((sum, multiplier, index) => sum + multiplier * weights[index]!, 0) /
    multipliers.length;
}

describe('Mikoo multi-area RTP calibration', () => {
  it('normalizes probabilities and calibrates the equal-stake benchmark', () => {
    const multipliers = [2, 3, 4, 6, 10, 12, 15, 5];
    const weights = calculateAreaOutcomeWeights(multipliers, 0.7);
    assert.ok(Math.abs(weights.reduce((sum, value) => sum + value, 0) - 1) < 1e-10);
    assert.ok(Math.abs(expectedEqualStakeReturn(multipliers, weights) - 0.7) < 1e-8);
  });

  it('changes the outcome distribution when the dashboard RTP changes', () => {
    const multipliers = [2, 3, 4, 6, 10, 12, 15, 5];
    const conservative = calculateAreaOutcomeWeights(multipliers, 0.62);
    const playerFriendly = calculateAreaOutcomeWeights(multipliers, 0.78);
    assert.notDeepEqual(conservative, playerFriendly);
    assert.ok(expectedEqualStakeReturn(multipliers, conservative) <
      expectedEqualStakeReturn(multipliers, playerFriendly));
  });

  it('clamps unattainable RTP targets to the mathematical minimum', () => {
    const multipliers = [2, 3, 10];
    const weights = calculateAreaOutcomeWeights(multipliers, 0.55);
    assert.ok(Math.abs(expectedEqualStakeReturn(multipliers, weights) - (2 / 3)) < 1e-8);
  });

  it('uses a uniform distribution when every area has the same multiplier', () => {
    assert.deepEqual(calculateAreaOutcomeWeights([3, 3, 3], 0.7), [1 / 3, 1 / 3, 1 / 3]);
  });
});
