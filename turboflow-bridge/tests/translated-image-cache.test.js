import test from 'node:test';
import assert from 'node:assert/strict';

import {
  DROP_EXPIRED,
  DROP_OVER_CAPACITY,
  DROP_WINDOW_EXHAUSTED,
  REUSE_MATCH_WINDOW,
  REUSE_MAX_ENTRIES,
  REUSE_TTL_MS,
  consumeReuseWindow,
  findTranslatedImage,
  forgetTranslatedImage,
  rememberTranslatedImage,
  summarizeTranslatedImages,
} from '../translated-image-cache.js';

/** 最小可用的 chrome.storage.local 替身 */
function fakeStorage() {
  const data = new Map();
  return {
    data,
    async get(keys) {
      if (keys === null || keys === undefined) return Object.fromEntries(data);
      const wanted = Array.isArray(keys) ? keys : [keys];
      const result = {};
      for (const key of wanted) if (data.has(key)) result[key] = data.get(key);
      return result;
    },
    async set(entries) {
      for (const [key, value] of Object.entries(entries)) data.set(key, value);
    },
    async remove(keys) {
      for (const key of (Array.isArray(keys) ? keys : [keys])) data.delete(key);
    },
  };
}

const SERVICE = 'https://api.example.com';
const TARGET_LANGUAGE = 'fr';
const NOW = 1_760_000_000_000;

async function remember(storage, imageHash, now = NOW, targetLanguage = TARGET_LANGUAGE) {
  return rememberTranslatedImage(storage, {
    serviceBaseUrl: SERVICE,
    targetLanguage,
    imageHash,
    resultDataUrl: `data:image/png;base64,translated-${imageHash}`,
    resultUrl: null,
    elapsedMs: 41_000,
    now,
  });
}

test('a cached translation is found again by source image hash', async () => {
  const storage = fakeStorage();
  await remember(storage, 'hash-a');

  const hit = await findTranslatedImage(storage, {
    serviceBaseUrl: SERVICE,
    targetLanguage: TARGET_LANGUAGE,
    imageHash: 'hash-a',
    now: NOW,
  });
  assert.equal(hit.resultDataUrl, 'data:image/png;base64,translated-hash-a');
  // 沿用原始耗时，而不是复用那一刻的接近 0 的值
  assert.equal(hit.elapsedMs, 41_000);
  assert.equal(hit.remaining, REUSE_MATCH_WINDOW);
});

test('records are scoped per service so multi-account bridges cannot cross-submit', async () => {
  const storage = fakeStorage();
  await remember(storage, 'hash-a');

  const other = await findTranslatedImage(storage, {
    serviceBaseUrl: 'https://other.example.com',
    targetLanguage: TARGET_LANGUAGE,
    imageHash: 'hash-a',
    now: NOW,
  });
  assert.equal(other, null);
});

test('records are scoped by target language so the same image cannot reuse the wrong translation', async () => {
  const storage = fakeStorage();
  await remember(storage, 'hash-a', NOW, 'fr');

  const otherLanguage = await findTranslatedImage(storage, {
    serviceBaseUrl: SERVICE,
    targetLanguage: 'de',
    imageHash: 'hash-a',
    now: NOW,
  });
  assert.equal(otherLanguage, null);
});

test('the match window is exhausted after exactly 20 unmatched tasks', async () => {
  const storage = fakeStorage();
  await remember(storage, 'hash-a');

  let dropped = [];
  for (let task = 0; task < REUSE_MATCH_WINDOW - 1; task++) {
    ({ dropped } = await consumeReuseWindow(storage, NOW));
    assert.equal(dropped.length, 0, `第 ${task + 1} 次任务后不该丢弃`);
  }
  assert.equal(
    (await findTranslatedImage(storage, {
      serviceBaseUrl: SERVICE,
      targetLanguage: TARGET_LANGUAGE,
      imageHash: 'hash-a',
      now: NOW,
    })).remaining,
    1,
  );

  ({ dropped } = await consumeReuseWindow(storage, NOW));
  assert.equal(dropped.length, 1);
  assert.equal(dropped[0].reason, DROP_WINDOW_EXHAUSTED);
  assert.equal(await findTranslatedImage(storage, {
    serviceBaseUrl: SERVICE,
    targetLanguage: TARGET_LANGUAGE,
    imageHash: 'hash-a',
    now: NOW,
  }), null);
});

test('expired records are dropped with the expiry reason', async () => {
  const storage = fakeStorage();
  await remember(storage, 'hash-a');

  const later = NOW + REUSE_TTL_MS + 1;
  assert.equal(await findTranslatedImage(storage, {
    serviceBaseUrl: SERVICE,
    targetLanguage: TARGET_LANGUAGE,
    imageHash: 'hash-a',
    now: later,
  }), null);

  const { dropped } = await consumeReuseWindow(storage, later);
  assert.equal(dropped.length, 1);
  assert.equal(dropped[0].reason, DROP_EXPIRED);
});

test('going over the entry cap drops the oldest record, not the newest', async () => {
  const storage = fakeStorage();
  for (let i = 0; i < REUSE_MAX_ENTRIES; i++) {
    await remember(storage, `hash-${i}`, NOW + i);
  }
  const { dropped } = await remember(storage, 'hash-newest', NOW + REUSE_MAX_ENTRIES);

  assert.equal(dropped.length, 1);
  assert.equal(dropped[0].reason, DROP_OVER_CAPACITY);
  assert.equal(dropped[0].imageHash, 'hash-0');
  // 最新的那条必须还在
  assert.ok(await findTranslatedImage(storage, {
    serviceBaseUrl: SERVICE,
    targetLanguage: TARGET_LANGUAGE,
    imageHash: 'hash-newest',
    now: NOW,
  }));
});

test('re-remembering the same source image refreshes the window instead of adding a row', async () => {
  const storage = fakeStorage();
  await remember(storage, 'hash-a');
  await consumeReuseWindow(storage, NOW);
  await consumeReuseWindow(storage, NOW);

  await remember(storage, 'hash-a');
  const hit = await findTranslatedImage(storage, {
    serviceBaseUrl: SERVICE,
    targetLanguage: TARGET_LANGUAGE,
    imageHash: 'hash-a',
    now: NOW,
  });
  assert.equal(hit.remaining, REUSE_MATCH_WINDOW);
  assert.equal((await summarizeTranslatedImages(storage, NOW)).count, 1);
});

test('summary reports the count and the tightest remaining window', async () => {
  const storage = fakeStorage();
  await remember(storage, 'hash-a');
  await consumeReuseWindow(storage, NOW);
  await remember(storage, 'hash-b');

  const summary = await summarizeTranslatedImages(storage, NOW);
  assert.equal(summary.count, 2);
  assert.equal(summary.minRemaining, REUSE_MATCH_WINDOW - 1);
});

test('forgetting a record removes it for good', async () => {
  const storage = fakeStorage();
  const { record } = await remember(storage, 'hash-a');
  await forgetTranslatedImage(storage, record);

  assert.equal(await findTranslatedImage(storage, {
    serviceBaseUrl: SERVICE,
    targetLanguage: TARGET_LANGUAGE,
    imageHash: 'hash-a',
    now: NOW,
  }), null);
  assert.equal((await summarizeTranslatedImages(storage, NOW)).count, 0);
});

test('a failed storage write remains reusable from memory when reads still work', async () => {
  const storage = fakeStorage();
  storage.set = async () => {
    throw new Error('write unavailable');
  };
  const { record } = await remember(storage, 'hash-memory-only');

  const summary = await summarizeTranslatedImages(storage, NOW);
  const hit = await findTranslatedImage(storage, {
    serviceBaseUrl: SERVICE,
    targetLanguage: TARGET_LANGUAGE,
    imageHash: 'hash-memory-only',
    now: NOW,
  });

  assert.equal(record.storageError, 'write unavailable');
  assert.equal(summary.count, 1);
  assert.equal(hit?.resultDataUrl, 'data:image/png;base64,translated-hash-memory-only');
  await forgetTranslatedImage(storage, record);
});
