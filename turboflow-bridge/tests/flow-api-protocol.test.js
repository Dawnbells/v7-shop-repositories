import test from 'node:test';
import assert from 'node:assert/strict';
import { generateWithReference, uploadImageToFlow } from '../flow-api.js';

function rpcResponse(rpc, payload) {
  return { ok: true, text: async () => JSON.stringify([['wrb.fr', rpc, JSON.stringify(payload)]]) };
}

function harness(t, respond) {
  const events = [];
  const requests = [];
  for (const name of ['window', 'document', 'chrome', 'fetch']) {
    const previous = Object.getOwnPropertyDescriptor(globalThis, name);
    t.after(() => {
      if (previous) Object.defineProperty(globalThis, name, previous);
      else delete globalThis[name];
    });
  }
  globalThis.window = {
    WIZ_global_data: { SNlM0e: 'fixture-xsrf' },
    location: { origin: 'https://flow.google.com', pathname: '/project/test' },
  };
  globalThis.document = { documentElement: { lang: 'en' } };
  globalThis.chrome = {
    tabs: { get: async () => ({ url: 'https://flow.google.com/project/test' }) },
    scripting: { executeScript: async ({ func, args = [] }) => {
      if (args.length === 2) {
        events.push(args[1]);
        return [{ result: `fresh-token-${events.length}` }];
      }
      return [{ result: await func(...args) }];
    } },
  };
  globalThis.fetch = async (url, options) => {
    const rpc = new URL(url).searchParams.get('rpcids');
    const envelope = JSON.parse(new URLSearchParams(options.body).get('f.req'));
    const payload = JSON.parse(envelope[0][0][1]);
    events.push(rpc);
    requests.push({ rpc, payload });
    return respond(rpc, payload, requests);
  };
  return { events, requests };
}

const generation = { pid: 'test', prompt: 'translate', referenceMediaId: 'source' };
const upload = { pid: 'test', base64: 'YWJj', fileName: 'source.jpg', mimeType: 'image/jpeg' };
const result = [[['media/result', null, ['https://flow-content.google/image/result']]], []];

test('generation obtains fresh verification after credit preflight, then submits immediately', async t => {
  const { events } = harness(t, rpc => rpcResponse(rpc,
    rpc === 'nzlxg' ? [1050, 1, 2, 2, null, 1050] : result));
  const generated = await generateWithReference(1, generation);
  assert.deepEqual(events, ['nzlxg', 'IMAGE_GENERATION', 'ogiZ0b']);
  assert.equal(generated.mediaId, 'media/result');
});

test('credit rejection does not request verification or submit generation', async t => {
  const { events } = harness(t, rpc => ({
    ok: true, text: async () => JSON.stringify([['wrb.fr', rpc, null, null, null, [8]]]),
  }));
  await assert.rejects(generateWithReference(1, generation), { code: 'FLOW_RESOURCE_EXHAUSTED' });
  assert.deepEqual(events, ['nzlxg']);
});

test('a pause during the credit request stops verification and generation', async t => {
  let paused = false;
  const { events } = harness(t, rpc => {
    paused = true;
    return rpcResponse(rpc, [1050]);
  });
  await assert.rejects(generateWithReference(1, {
    ...generation,
    beforeSubmit() {
      if (paused) throw Object.assign(new Error('paused'), { code: 'FLOW_SUBMISSION_PAUSED' });
    },
  }), { code: 'FLOW_SUBMISSION_PAUSED' });
  assert.deepEqual(events, ['nzlxg']);
});

test('transport retry preserves upload identity but refreshes verification; next upload gets new IDs', async t => {
  const { requests } = harness(t, (rpc, _payload, calls) => {
    if (calls.length === 1) throw new Error('temporary connection loss');
    return rpcResponse(rpc, [['media/uploaded']]);
  });
  assert.equal(await uploadImageToFlow(1, upload), 'media/uploaded');
  await uploadImageToFlow(1, upload);
  assert.equal(requests.length, 3);
  const [first, retry, next] = requests.map(r => r.payload);
  assert.equal(first.length, 12);
  assert.match(first[10], /^[0-9a-f-]{36}$/i);
  assert.match(first[11], /^[0-9a-f-]{36}$/i);
  assert.notEqual(first[10], first[11]);
  assert.deepEqual(retry.slice(10), first.slice(10));
  assert.notEqual(retry[0][10][0], first[0][10][0]);
  assert.notEqual(next[10], first[10]);
  assert.notEqual(next[11], first[11]);
});

test('upload RPC rejection is preserved and is not retried', async t => {
  const { requests } = harness(t, rpc => ({
    ok: true, text: async () => JSON.stringify([['wrb.fr', rpc, null, null, null, [3]]]),
  }));
  await assert.rejects(uploadImageToFlow(1, upload), { code: 'FLOW_RPC_REJECTED', rpcStatus: 3 });
  assert.equal(requests.length, 1);
});
