import test from 'node:test';
import assert from 'node:assert/strict';
import { FlowRecoveryController } from '../flow-recovery-controller.js';
const tick = () => new Promise(resolve => setImmediate(resolve));

async function harness(saved, options = {}) {
  const events = [], writes = [];
  let idle = true, clock = 0;
  const c = new FlowRecoveryController({
    persist: async value => writes.push(structuredClone(value)),
    drained: () => idle, now: () => clock,
    sleep: async () => { clock += 100; await tick(); },
    restart: async (target, phase) => { events.push(['restart', target]); await phase('closing'); await phase('opening'); await phase('creating'); },
    stop: (reason, code) => events.push(['stop', code]), ...options,
  });
  await c.restore(saved);
  return { c, events, writes, idle: value => { idle = value; } };
}
test('draining closes the gate synchronously and merges concurrent errors until reporting finishes', async () => {
  const h = await harness(); h.idle(false);
  const first = h.c.request({ homeUrl: 'https://flow.google.com/u/1/' });
  assert.equal(h.c.blocked, true);
  assert.equal(h.c.request(), first);
  await tick(); assert.equal(h.events.length, 0);
  h.c.success(1, 'late-success'); h.c.success(1, 'late-success');
  assert.equal(h.c.state.successfulTasks.length, 1);
  h.idle(true); assert.equal(await first, true);
  assert.equal(h.c.state.emptyRounds, 0);
  assert.equal(h.c.state.roundId, 2);
  assert.equal(h.events.length, 1);
});
test('two empty rounds stop without closing tabs for a third round; Run Now resets', async () => {
  const h = await harness();
  assert.equal(await h.c.request(), true);
  assert.equal(h.c.state.emptyRounds, 1);
  assert.equal(await h.c.request(), false);
  assert.equal(h.c.state.phase, 'stopped');
  assert.deepEqual(h.events.map(e => e[0]), ['restart', 'stop']);
  assert.equal(await h.c.request(null, { manual: true }), true);
  assert.equal(h.c.state.emptyRounds, 0);
});
test('downloaded success resets an empty streak without waiting for backend success', async () => {
  const h = await harness(); await h.c.request();
  h.c.success(h.c.state.roundId, 'downloaded-report-failed');
  await h.c.request(); assert.equal(h.c.state.emptyRounds, 0);
  await h.c.request(); assert.equal(h.c.state.emptyRounds, 1);
  assert.equal(h.c.state.phase, 'running');
});
test('success from an earlier round cannot reset a later empty round', async () => {
  const h = await harness(); await h.c.request();
  h.c.success(1, 'stale');
  await h.c.request(); assert.equal(h.c.state.phase, 'stopped');
});
test('quota or authentication stop while draining prevents restart', async () => {
  const h = await harness(); h.idle(false);
  const p = h.c.request(); await tick(); h.c.halt(); h.idle(true);
  assert.equal(await p, false); assert.equal(h.events.length, 0);
});
test('drain timeout preserves tabs and requires manual action', async () => {
  const h = await harness(null, { drainTimeoutMs: 200 }); h.idle(false);
  assert.equal(await h.c.request(), false);
  assert.deepEqual(h.events.map(e => e[0]), ['stop']);
});
test('interrupted recovery stops on restore; normal restart retains round accounting', async () => {
  const saved = { phase: 'creating', roundId: 3, emptyRounds: 1, successfulTasks: [] };
  const h = await harness(saved);
  assert.equal(h.c.state.phase, 'stopped');
  assert.equal(h.events[0][1], 'RECOVERY_INTERRUPTED');
  assert.equal(h.c.state.emptyRounds, 1);
  const normal = await harness({ ...saved, phase: 'running' });
  assert.equal(normal.c.blocked, false);
  assert.equal(normal.c.state.emptyRounds, 1);
});
test('navigation failure stops without retrying the lifecycle', async () => {
  const h = await harness(null, { restart: async () => { throw new Error('navigation failed'); } });
  assert.equal(await h.c.request(), false);
  assert.equal(h.c.state.phase, 'stopped');
  assert.equal(h.events[0][1], 'RECOVERY_FAILED');
});
