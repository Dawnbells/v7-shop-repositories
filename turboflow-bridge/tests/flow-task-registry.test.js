import test from 'node:test';
import assert from 'node:assert/strict';

import { FlowTaskRegistry } from '../flow-task-registry.js';

test('keeps submission serial while allowing four concurrent generations', () => {
  const registry = new FlowTaskRegistry(4);

  for (let i = 1; i <= 4; i++) {
    const id = `task-${i}`;
    assert.equal(registry.reserveSubmission(id), true);
    assert.equal(registry.reserveSubmission(`overlap-${i}`), false);
    assert.equal(registry.acceptSubmission(id), true);
  }

  assert.equal(registry.inUse, 4);
  assert.equal(registry.hasCapacity, false);
  assert.equal(registry.reserveSubmission('task-5'), false);
  assert.deepEqual(registry.snapshot().generatingOwners, ['task-1', 'task-2', 'task-3', 'task-4']);
});

test('opens exactly one new submission slot when any generation completes', () => {
  const registry = new FlowTaskRegistry(4);
  for (let i = 1; i <= 4; i++) {
    registry.reserveSubmission(`task-${i}`);
    registry.acceptSubmission(`task-${i}`);
  }

  assert.equal(registry.release('task-2'), true);
  assert.equal(registry.hasCapacity, true);
  assert.equal(registry.reserveSubmission('task-5'), true);
  assert.equal(registry.reserveSubmission('task-6'), false);
  assert.equal(registry.inUse, 4);
});

test('releases failed submissions without adding them to the generating set', () => {
  const registry = new FlowTaskRegistry(4);
  assert.equal(registry.reserveSubmission('failed-before-submit'), true);
  assert.equal(registry.release('failed-before-submit'), true);
  assert.equal(registry.inUse, 0);
  assert.equal(registry.generatingOwners.size, 0);
});

test('ignores stale acceptance and release events from other assignments', () => {
  const registry = new FlowTaskRegistry(4);
  registry.reserveSubmission('current');

  assert.equal(registry.acceptSubmission('stale'), false);
  assert.equal(registry.release('stale'), false);
  assert.equal(registry.submissionOwner, 'current');
  assert.equal(registry.inUse, 1);
});
