import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { FlowTaskRegistry } from '../flow-task-registry.js';
import { FlowSubmissionPacer } from '../flow-submission-pacer.js';

const source = readFileSync(new URL('../background.js', import.meta.url), 'utf8');
function scheduler() {
  let now = 1000;
  let polls = 0;
  const started = [];
  const registry = new FlowTaskRegistry(4);
  const pacer = new FlowSubmissionPacer({ now: () => now, random: () => 0.5 });
  const context = vm.createContext({
    flowTasks: registry, submissionPacer: pacer,
    Date: { now: () => now }, PREFETCH_LIMIT: 1, POLL_INTERVAL_MS: 500,
    running: false, prefetchedTask: null, pollPaused: false,
    recoveryPromise: null, flowTabAvailable: true, nextPollAt: 0, serviceCursor: 0,
    scheduleLoop: () => {}, broadcast: () => {}, broadcastTasksChanged: () => {}, addLog: () => {},
    createThumbnail: async () => 'thumbnail', ensureDataUrl: value => value, buildPrompt: () => 'translate',
    loadConfig: async () => ({ services: [{ baseUrl: 'server', token: 'test' }] }),
    checkConnection: async () => ({ connected: true }), rotateServices: services => services,
    pollTask: async () => ({ hasTask: true, assignmentId: `task-${++polls}`, imageBase64: 'image' }),
    executeTask: async (_, task) => { started.push(task.assignmentId); },
  });
  vm.runInContext(source.slice(source.indexOf('function getPrefetchedTaskSummary()'),
    source.indexOf('async function pollTask(')), context);
  return { context, registry, pacer, started, polls: () => polls, time: value => { now = value; },
    tick: () => context.runLoop(),
    submit() { registry.acceptSubmission(registry.submissionOwner); pacer.submitted(); },
  };
}

test('keeps four generations plus exactly one downloaded standby', async () => {
  const s = scheduler();
  for (let i = 0; i < 4; i++) {
    s.time(1000 + i * 7500);
    await s.tick();
    s.submit();
    await s.tick();
  }
  assert.equal(s.registry.generatingOwners.size, 4);
  assert.equal(s.polls(), 5);
  assert.equal(s.context.getPrefetchedTaskSummary().phase, 'standby');
  assert.equal(s.context.getPrefetchedTaskSummary().sourceImage, 'image');
  await s.tick();
  assert.equal(s.polls(), 5);
  // Two completions cannot double-dispatch the one standby.
  s.time(26000);
  s.context.releaseFlowSlot('task-1');
  s.context.releaseFlowSlot('task-2');
  assert.equal(s.started.length, 4);
  s.time(30999);
  await s.tick();
  assert.equal(s.started.length, 4);
  s.time(31000);
  await s.tick();
  assert.equal(s.started.length, 5);
  assert.equal(s.registry.submissionOwner, 'task-5');
  assert.equal(s.polls(), 6);
});

test('starts standby immediately when a slot finishes after the global deadline', async () => {
  const s = scheduler();
  await s.tick();
  s.submit();
  await s.tick();
  s.time(10000);
  s.context.releaseFlowSlot('task-1');
  assert.deepEqual(s.started, ['task-1', 'task-2']);
});

test('draws a 5–10 second deadline only on submission, never on polling or completion', () => {
  let now = 0;
  let draws = 0;
  const p = new FlowSubmissionPacer({ now: () => now, random: () => draws++ ? 1 : 0 });
  p.submitted();
  assert.equal(p.remainingMs, 5000);
  now = 4000;
  assert.equal(p.remainingMs, 1000);
  assert.equal(p.remainingMs, 1000);
  assert.equal(draws, 1);
  now = 5000;
  p.submitted();
  assert.equal(p.remainingMs, 10000);
});
