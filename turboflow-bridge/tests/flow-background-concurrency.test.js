import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const background = readFileSync(new URL('../background.js', import.meta.url), 'utf8');
const packageScript = readFileSync(new URL('../scripts/package-extension.ps1', import.meta.url), 'utf8');

test('wires the background scheduler to one submit lane and four generation slots', () => {
  assert.match(background, /const FLOW_CONCURRENCY = 4;/);
  assert.match(background, /new FlowTaskRegistry\(FLOW_CONCURRENCY\)/);
  assert.match(background, /flowTasks\.reserveSubmission\(task\.assignmentId\)/);
  assert.match(background, /flowTasks\.acceptSubmission\(assignmentId\)/);
  assert.match(background, /flowTasks\.release\(assignmentId\)/);
  assert.doesNotMatch(background, /flowSubmissionAccepted/);
});

test('ships the concurrency registry in the extension package', () => {
  const occurrences = packageScript.match(/'flow-task-registry\.js'/g) || [];
  assert.equal(occurrences.length, 2);
});
