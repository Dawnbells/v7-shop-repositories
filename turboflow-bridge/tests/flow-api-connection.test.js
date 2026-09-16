import test from 'node:test';
import assert from 'node:assert/strict';

const projectId = '11111111-2222-4333-8444-555555555555';
const tab = {
  id: 42,
  status: 'complete',
  lastAccessed: Date.now(),
  url: `https://flow.google.com/project/${projectId}`,
};

let executeScriptCalls = 0;
globalThis.chrome = {
  tabs: {
    query: async () => [tab],
    get: async () => tab,
  },
  scripting: {
    executeScript: async () => {
      executeScriptCalls++;
      return [{ result: { hasTransportConfig: true, hasXsrfToken: true } }];
    },
  },
};

const { checkConnection } = await import('../flow-api.js');

test('page automation connection check does not require BOQ or legacy API session data', async () => {
  executeScriptCalls = 0;
  const connection = await checkConnection();
  assert.equal(connection.connected, true);
  assert.equal(connection.projectId, projectId);
  assert.equal(executeScriptCalls, 0);
});

test('API callers can still request the BOQ session bootstrap check explicitly', async () => {
  executeScriptCalls = 0;
  const connection = await checkConnection({ requireApiSession: true });
  assert.equal(connection.connected, true);
  assert.equal(executeScriptCalls, 1);
});
