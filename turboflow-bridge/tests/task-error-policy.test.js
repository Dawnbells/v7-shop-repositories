import test from 'node:test';
import assert from 'node:assert/strict';

import {
  CONSECUTIVE_FAILURE_PAUSE_THRESHOLD,
  FLOW_DISCONNECTED_PAUSE_THRESHOLD,
  FAILURE_STREAK_INCREMENT,
  FAILURE_STREAK_NEUTRAL,
  FAILURE_STREAK_RESET,
  classifyErrorCode,
  nextConsecutiveFailureCount,
  nextFlowDisconnectedCount,
  shouldPauseForRunNow,
} from '../task-error-policy.js';

test('classifies a structured Flow authentication error', () => {
  const error = new Error('Upload failed');
  error.code = 'FLOW_AUTHENTICATION_FAILED';

  assert.equal(classifyErrorCode(error), 'FLOW_AUTHENTICATION_FAILED');
});

test('classifies the raw Google upload 401 error as an authentication failure', () => {
  const message = `Upload failed: HTTP 401: { "error": {
    "code": 401,
    "message": "Request had invalid authentication credentials.",
    "status": "UNAUTHENTICATED"
  } }`;

  assert.equal(classifyErrorCode(message), 'FLOW_AUTHENTICATION_FAILED');
});

test('classifies a 401 from the generate stage too, not just uploads', () => {
  // callFlowApi 抛的消息没有 "Upload failed" 前缀，以前会掉进 FLOW_EXECUTION_FAILED 不停重试
  const message = 'HTTP 401: {"error":{"code":401,"status":"UNAUTHENTICATED"}}';

  assert.equal(classifyErrorCode(message), 'FLOW_AUTHENTICATION_FAILED');
});

test('leaves unrelated upload failures on the ordinary execution path', () => {
  assert.equal(
    classifyErrorCode('Upload failed: HTTP 500: backend unavailable'),
    'FLOW_EXECUTION_FAILED',
  );
});

test('authentication failure always waits for an explicit Run Now', () => {
  assert.equal(shouldPauseForRunNow('FLOW_AUTHENTICATION_FAILED'), true);
  assert.equal(shouldPauseForRunNow('FLOW_EXECUTION_FAILED'), false);
  assert.equal(shouldPauseForRunNow('DAILY_QUOTA_REACHED'), false);
});

test('the third consecutive Flow disconnection waits for Run Now', () => {
  assert.equal(FLOW_DISCONNECTED_PAUSE_THRESHOLD, 3);
  assert.equal(shouldPauseForRunNow('FLOW_DISCONNECTED', { consecutiveFlowDisconnects: 2 }), false);
  assert.equal(shouldPauseForRunNow('FLOW_DISCONNECTED', { consecutiveFlowDisconnects: 3 }), true);
});

test('the fifth consecutive failure of any kind waits for Run Now', () => {
  assert.equal(CONSECUTIVE_FAILURE_PAUSE_THRESHOLD, 5);
  assert.equal(shouldPauseForRunNow('TIMEOUT', { consecutiveFailures: 4 }), false);
  assert.equal(shouldPauseForRunNow('TIMEOUT', { consecutiveFailures: 5 }), true);
  // 没有 errorCode 的场景（译图/政策回退上报失败）同样受这道闸门约束
  assert.equal(shouldPauseForRunNow(null, { consecutiveFailures: 5 }), true);
});

test('counts consecutive Flow disconnections up to the pause threshold', () => {
  let count = 0;
  for (let attempt = 0; attempt < FLOW_DISCONNECTED_PAUSE_THRESHOLD; attempt++) {
    count = nextFlowDisconnectedCount(count, 'FLOW_DISCONNECTED', { flowTabPresent: true });
  }

  assert.equal(count, 3);
});

test('a different task error breaks the Flow disconnection streak', () => {
  assert.equal(nextFlowDisconnectedCount(2, 'TIMEOUT'), 0);
});

test('a disconnection with the tab confirmed gone does not count toward the pause', () => {
  // watchdog 会在 tab 重开后自动恢复轮询，不该消耗人工介入配额
  assert.equal(nextFlowDisconnectedCount(2, 'FLOW_DISCONNECTED', { flowTabPresent: false }), 0);
  // 探测不到结论时（未传字段）按原来的方式累加
  assert.equal(nextFlowDisconnectedCount(2, 'FLOW_DISCONNECTED'), 3);
});

test('failure streak increments on failures and resets on success', () => {
  let count = 0;
  count = nextConsecutiveFailureCount(count, FAILURE_STREAK_INCREMENT);
  count = nextConsecutiveFailureCount(count, FAILURE_STREAK_INCREMENT);
  assert.equal(count, 2);

  assert.equal(nextConsecutiveFailureCount(count, FAILURE_STREAK_RESET), 0);
});

test('a policy fallback is neutral: it neither counts nor breaks the streak', () => {
  assert.equal(nextConsecutiveFailureCount(4, FAILURE_STREAK_NEUTRAL), 4);
  // 所以第 6 张图再失败就会触发停止，而不是从 1 重新数
  assert.equal(
    nextConsecutiveFailureCount(nextConsecutiveFailureCount(4, FAILURE_STREAK_NEUTRAL), FAILURE_STREAK_INCREMENT),
    5,
  );
});

test('failure streak tolerates a corrupted persisted counter', () => {
  assert.equal(nextConsecutiveFailureCount(undefined, FAILURE_STREAK_INCREMENT), 1);
  assert.equal(nextConsecutiveFailureCount(-3, FAILURE_STREAK_INCREMENT), 1);
  assert.equal(nextConsecutiveFailureCount('nonsense', FAILURE_STREAK_INCREMENT), 1);
});
