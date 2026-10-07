import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const background = readFileSync(new URL('../background.js', import.meta.url), 'utf8');
const packageScript = readFileSync(new URL('../scripts/package-extension.ps1', import.meta.url), 'utf8');

function extractFunction(text, name) {
  const start = text.indexOf(`function ${name}(`);
  assert.notEqual(start, -1, `${name} not found`);
  const bodyStart = text.indexOf('{', text.indexOf(')', start));
  let depth = 0;
  for (let i = bodyStart; i < text.length; i++) {
    if (text[i] === '{') depth++;
    if (text[i] === '}' && --depth === 0) return text.slice(start, i + 1);
  }
  throw new Error(`${name} is not balanced`);
}

test('concurrency is configurable from 1 to 10 and defaults to four translations', () => {
  assert.match(background, /const DEFAULT_FLOW_CONCURRENCY = 4;/);
  assert.match(background, /const MAX_FLOW_CONCURRENCY = 10;/);
  const normalize = new Function('DEFAULT_FLOW_CONCURRENCY', 'MAX_FLOW_CONCURRENCY',
    `${extractFunction(background, 'normalizeFlowConcurrency')}; return normalizeFlowConcurrency;`)(4, 10);
  assert.equal(normalize(undefined), 4);
  assert.equal(normalize('abc'), 4);
  assert.equal(normalize(0), 1);
  assert.equal(normalize(2), 2);
  assert.equal(normalize('3'), 3);
  assert.equal(normalize(9), 9);
  assert.equal(normalize(10), 10);
  assert.equal(normalize(25), 10);

  // 读取和保存设置时都同步到调度器的上限。
  assert.match(extractFunction(background, 'saveConfig'), /flowTasks\.setLimit\(nextConcurrency\)/);
  assert.match(background, /flowConcurrency = normalizeFlowConcurrency\(stored\[FLOW_CONCURRENCY_STORAGE_KEY\]\);\s*flowTasks\.setLimit\(flowConcurrency\);/);

  const sidepanel = readFileSync(new URL('../sidepanel.html', import.meta.url), 'utf8');
  const panelScript = readFileSync(new URL('../sidepanel.js', import.meta.url), 'utf8');
  assert.match(sidepanel, /<select id="flow-concurrency">[\s\S]*value="10"/);
  assert.match(panelScript, /flowConcurrency: Number\(flowConcurrencyEl\.value\) \|\| 1/);
});

test('a slot is released only after the translated image is downloaded, before reporting', () => {
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
  assert.match(background, /new FlowTaskRegistry\(flowConcurrency\)/);
  assert.match(background, /flowTasks\.reserveSubmission\(task\.assignmentId\)/);
  assert.match(background, /flowTasks\.acceptSubmission\(assignmentId\)/);
  assert.match(background, /flowTasks\.release\(assignmentId\)/);
  assert.doesNotMatch(background, /flowSubmissionAccepted/);
});

test('ships the concurrency registry in the extension package', () => {
  const occurrences = packageScript.match(/'flow-task-registry\.js'/g) || [];
  assert.equal(occurrences.length, 2);
});

test('Flow UI submissions pace the next upload by the same random 5–10 s gap as the API path', () => {
  // UI：ogiZ0b 真正发出后页面上报 SUBMITTED，此刻抽取间隔；API：onSubmitted 时抽取。
  const ui = background.slice(background.indexOf("if (msg.type === 'FLOW_DOM_TRANSLATION_SUBMITTED')"));
  const accepted = ui.indexOf('flowTasks.acceptSubmission(assignmentId)');
  const paced = ui.indexOf('submissionPacer.submitted();');
  const next = ui.indexOf('startPrefetchedTask()');
  assert.ok(accepted > 0 && paced > accepted && next > paced);
  assert.match(extractFunction(background, 'translateImage'), /submissionPacer\.submitted\(submittedAt\)/);

  // 任何新任务开始（第一步即上传源图）前都要过同一个间隔闸门。
  assert.match(extractFunction(background, 'reserveFlowSlotAndExecute'), /submissionPacer\.remainingMs > 0/);
  const pacer = readFileSync(new URL('../flow-submission-pacer.js', import.meta.url), 'utf8');
  assert.match(pacer, /at \+ 5000 \+ Math\.min\(1, Math\.max\(0, this\.random\(\)\)\) \* 5000/);
});
