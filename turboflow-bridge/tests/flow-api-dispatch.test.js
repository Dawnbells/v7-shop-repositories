import test from 'node:test';
import assert from 'node:assert/strict';

for (const modern of [true, false]) {
  test(`${modern ? 'BOQ' : 'legacy'} releases submission before response and keeps concurrent results separate`, async () => {
    globalThis.window = {
      WIZ_global_data: { SNlM0e: 'fixture-xsrf' },
      location: { origin: 'https://flow.google.com', pathname: '/project/test' },
    };
    globalThis.document = { documentElement: { lang: 'en' } };
    const pending = [];
    const submitted = [];
    globalThis.fetch = () => new Promise(resolve => pending.push(resolve));
    globalThis.chrome = {
      tabs: { get: async () => ({ url: modern ? 'https://flow.google.com/project/test' : 'https://labs.google/fx/tools/flow/project/test' }) },
      scripting: { executeScript: async ({ func, args = [] }) => [{
        result: args.length === 2 ? 'fixture-captcha' : await func(...args),
      }] },
    };
    const { generateWithReference } = await import(`../flow-api.js?dispatch=${modern}`);
    let settled = 0;
    const requests = [0, 1, 2, 3].map(i => generateWithReference(1, {
      pid: 'test', prompt: `translate-${i}`, referenceMediaId: `source-${i}`,
      onSubmitted: info => submitted.push({ i, ...info }),
    }).then(result => { settled++; return result; }));
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(submitted.length, 4);
    assert.equal(pending.length, 4);
    assert.equal(settled, 0);
    assert.equal(window.__turboFlowRequests.size, 4);
    for (const i of [3, 1, 0, 2]) {
      const url = `https://lh3.googleusercontent.com/result-${i}.png`;
      const data = modern
        ? [[[`media-${i}`, null, `workflow-${i}`, null, null, null,
          [[null, 123, null, null, null, null, 1, 'prompt', 25, null, null, `workflow-${i}`, null, url]]]], []]
        : { media: [{ image: { generatedImage: { fifeUrl: url } } }] };
      pending[i]({ ok: true, text: async () => modern
        ? JSON.stringify([['wrb.fr', 'ogiZ0b', JSON.stringify(data)]])
        : JSON.stringify(data) });
    }
    const results = await Promise.all(requests);
    results.forEach((result, i) => assert.equal(result.fifeUrl, `https://lh3.googleusercontent.com/result-${i}.png`));
    assert.equal(window.__turboFlowRequests.size, 0);
  });
}
