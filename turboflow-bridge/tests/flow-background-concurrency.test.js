import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const background = readFileSync(new URL('../background.js', import.meta.url), 'utf8');
const packageScript = readFileSync(new URL('../scripts/package-extension.ps1', import.meta.url), 'utf8');

test('runs Flow single-threaded: the next source image starts only after the previous translation', () => {
  assert.match(background, /const FLOW_CONCURRENCY = 1;/);
  assert.match(background, /const PREFETCH_LIMIT = 1;/);

  // 译图已下载到扩展后才释放槽位；释放发生在服务端回传之前，回传与下一张并行。
  const execute = background.slice(background.indexOf('async function executeTask'), background.indexOf('function runWithTimeout'));
  const translated = execute.indexOf('const result = await runWithTimeout(');
  const release = execute.indexOf('releaseFlowSlot(task.assignmentId);', translated);
  const report = execute.indexOf('await postCompletionWithRetry(service', translated);
  assert.ok(translated > 0 && release > translated && report > release);

  // UI 方式只在拿到可读的译图 dataURL 后才返回。
  const dom = background.slice(background.indexOf('async function runFlowDomTranslation'), background.indexOf('function dispatchTrustedClick'));
  assert.match(dom, /if \(!resultDataUrl\) throw new Error/);
});

test('wires the background scheduler to one submit lane and the Flow generation slots', () => {
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
