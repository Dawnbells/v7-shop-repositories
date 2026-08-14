import test from 'node:test';
import assert from 'node:assert/strict';

import {
  STAT_FAILED,
  STAT_POLICY,
  STAT_SUCCESS,
  emptyStats,
  localDayKey,
  normalizeStats,
  recordOutcome,
} from '../bridge-stats.js';

const NOON = new Date(2026, 7, 14, 12, 0, 0).getTime();
const NEXT_DAY = new Date(2026, 7, 15, 9, 0, 0).getTime();

test('total is always the sum of the three buckets', () => {
  let stats = emptyStats(NOON);
  stats = recordOutcome(stats, STAT_SUCCESS, NOON);
  stats = recordOutcome(stats, STAT_SUCCESS, NOON);
  stats = recordOutcome(stats, STAT_FAILED, NOON);
  stats = recordOutcome(stats, STAT_POLICY, NOON);

  assert.deepEqual(stats.today, { day: localDayKey(NOON), total: 4, success: 2, failed: 1, policy: 1 });
  assert.deepEqual(stats.allTime, { total: 4, success: 2, failed: 1, policy: 1 });
});

test('the daily bucket rolls over at local midnight but the all-time bucket keeps growing', () => {
  let stats = recordOutcome(emptyStats(NOON), STAT_SUCCESS, NOON);
  stats = recordOutcome(stats, STAT_FAILED, NEXT_DAY);

  assert.equal(stats.today.day, localDayKey(NEXT_DAY));
  assert.deepEqual(stats.today, { day: localDayKey(NEXT_DAY), total: 1, success: 0, failed: 1, policy: 0 });
  assert.deepEqual(stats.allTime, { total: 2, success: 1, failed: 1, policy: 0 });
});

test('merely reading across a day boundary already rolls the daily bucket', () => {
  const stats = recordOutcome(emptyStats(NOON), STAT_SUCCESS, NOON);
  const rolled = normalizeStats(stats, NEXT_DAY);

  assert.deepEqual(rolled.today, { day: localDayKey(NEXT_DAY), total: 0, success: 0, failed: 0, policy: 0 });
  assert.equal(rolled.allTime.success, 1);
});

test('a corrupted persisted payload heals instead of throwing', () => {
  const stats = normalizeStats(
    { allTime: { success: 'x', failed: -4, policy: 2.7, total: 999 }, today: null },
    NOON,
  );

  assert.deepEqual(stats.allTime, { total: 2, success: 0, failed: 0, policy: 2 });
  assert.deepEqual(stats.today, { day: localDayKey(NOON), total: 0, success: 0, failed: 0, policy: 0 });
});

test('an unknown outcome is ignored but still rolls the day', () => {
  const stats = recordOutcome(emptyStats(NOON), 'not-an-outcome', NOON);
  assert.deepEqual(stats.allTime, { total: 0, success: 0, failed: 0, policy: 0 });
});
