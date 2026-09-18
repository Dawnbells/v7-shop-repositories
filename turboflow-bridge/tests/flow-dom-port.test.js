import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const background = readFileSync(new URL('../background.js', import.meta.url), 'utf8');
const pageAutomation = readFileSync(new URL('../flow-dom-method.js', import.meta.url), 'utf8');

test('uses a dedicated port instead of one long-lived sendMessage response', () => {
  const start = background.indexOf('async function runFlowDomTranslation(conn, task)');
  const end = background.indexOf('function buildDomUploadFileName', start);
  const pipeline = background.slice(start, end);

  assert.match(pipeline, /sendFlowDomRequestOverPort\(conn\.tabId, requestId, taskPayload\)/);
  assert.match(pipeline, /chrome\.tabs\.connect\(tabId, \{ name: DOM_TRANSLATE_PORT, frameId: 0 \}\)/);
  assert.match(pipeline, /FLOW_DOM_TRANSLATION_ACCEPTED/);
  assert.match(pipeline, /FLOW_DOM_TRANSLATION_RESULT/);
  assert.doesNotMatch(pipeline, /chrome\.tabs\.sendMessage/);
});

test('page automation acknowledges first and reports detailed completion over the port', () => {
  assert.match(pageAutomation, /chrome\.runtime\.onConnect\.addListener\(portListener\)/);
  assert.match(pageAutomation, /FLOW_DOM_TRANSLATION_ACCEPTED/);
  assert.match(pageAutomation, /FLOW_DOM_TRANSLATION_RESULT/);
  assert.match(pageAutomation, /Unknown Flow page automation error/);
});

test('refreshes stale listeners and reconnects with the same deduplicated request id', () => {
  assert.match(background, /DOM_PORT_CONNECT_ATTEMPTS = 3/);
  assert.match(background, /retryableChannel/);
  assert.match(background, /sendFlowDomRequestOverPort\(conn\.tabId, requestId, taskPayload\)/);
  assert.match(pageAutomation, /previous\.refreshListeners\?\.\(\)/);
  assert.match(pageAutomation, /const requestStates = new Map\(\)/);
  assert.match(pageAutomation, /const existing = requestStates\.get\(requestId\)/);
  assert.match(pageAutomation, /resumed: true/);
});
