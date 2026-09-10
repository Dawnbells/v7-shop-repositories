import test from 'node:test';
import assert from 'node:assert/strict';

import {
  FLOW_HOME_URL,
  FLOW_TAB_URL_PATTERNS,
  buildFlowMediaRedirectUrl,
  buildFlowProjectUrl,
  getProjectIdFromFlowUrl,
  isFlowUrl,
  isModernFlowUrl,
} from '../flow-sites.js';

test('recognizes both current and legacy Google Flow URLs', () => {
  assert.equal(isFlowUrl('https://flow.google.com/'), true);
  assert.equal(isFlowUrl('https://flow.google.com/project/abc-123'), true);
  assert.equal(isFlowUrl('https://labs.google/fx/zh/tools/flow/'), true);
  assert.equal(isFlowUrl('https://labs.google/fx/tools/flow/project/abc-123'), true);
  assert.equal(isFlowUrl('https://labs.google/fx/tools/whisk/'), false);
  assert.equal(isFlowUrl('https://example.com/project/abc-123'), false);
  assert.equal(isModernFlowUrl('https://flow.google.com/project/abc-123'), true);
  assert.equal(isModernFlowUrl('https://labs.google/fx/tools/flow/project/abc-123'), false);
});

test('queries and opens the current Flow host while retaining the legacy host', () => {
  assert.equal(FLOW_HOME_URL, 'https://flow.google.com/');
  assert.deepEqual(FLOW_TAB_URL_PATTERNS, [
    'https://flow.google.com/*',
    'https://labs.google/fx/*',
  ]);
});

test('extracts project ids on both hosts and hash-routed variants', () => {
  assert.equal(getProjectIdFromFlowUrl('https://flow.google.com/project/new-id'), 'new-id');
  assert.equal(getProjectIdFromFlowUrl('https://flow.google.com/#/project/hash-id'), 'hash-id');
  assert.equal(getProjectIdFromFlowUrl('https://labs.google/fx/zh/tools/flow/project/legacy-id'), 'legacy-id');
});

test('builds host-specific project and media URLs', () => {
  assert.equal(
    buildFlowProjectUrl('https://flow.google.com/', 'new-id'),
    'https://flow.google.com/project/new-id',
  );
  assert.equal(
    buildFlowProjectUrl('https://labs.google/fx/zh/tools/flow/', 'legacy-id'),
    'https://labs.google/fx/tools/flow/project/legacy-id',
  );
  assert.equal(
    buildFlowMediaRedirectUrl('https://flow.google.com/project/new-id', 'media/name'),
    null,
  );
});
