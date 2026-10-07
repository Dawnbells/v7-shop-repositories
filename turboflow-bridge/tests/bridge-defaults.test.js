import test from 'node:test';
import assert from 'node:assert/strict';
import { initializeDefaultConfig } from '../bridge-defaults.js';

function harness(initial = {}) {
  const data = { ...initial };
  const writes = [];
  return { data, writes, storage: { get: async () => ({ ...data }),
    set: async patch => { writes.push(patch); Object.assign(data, patch); } } };
}
const preset = async () => ({ baseUrl: 'https://api.xyzdwd.com', token: 'fake-test-token' });
test('fresh install seeds exactly one enabled service and defaults; restart does not reseed', async () => {
  const h = harness();
  await initializeDefaultConfig(h.storage, preset);
  assert.deepEqual(h.data, { services: [{ baseUrl: 'https://api.xyzdwd.com', token: 'fake-test-token', enabled: true }],
    generationMode: 'api-2.3.5.1', flowConcurrency: 4 });
  await initializeDefaultConfig(h.storage, () => assert.fail('must not re-read preset'));
  assert.equal(h.writes.length, 1);
});
test('upgrade retains user settings including intentionally empty services', async () => {
  const h = harness({ services: [], generationMode: 'ui', flowConcurrency: 2 });
  await initializeDefaultConfig(h.storage, () => assert.fail('must preserve empty list'));
  assert.deepEqual(h.data, { services: [], generationMode: 'ui', flowConcurrency: 2 });
  assert.equal(h.writes.length, 0);
});
test('partial settings only fill missing fields; invalid preset never leaks contents', async () => {
  const h = harness({ services: [] });
  await initializeDefaultConfig(h.storage, preset);
  assert.equal(h.data.flowConcurrency, 4);
  const empty = harness();
  await assert.rejects(initializeDefaultConfig(empty.storage, async () => ({ token: 'private-test' })),
    error => !error.message.includes('private-test') && /preset/.test(error.message));
  assert.equal(empty.writes.length, 0);
});
