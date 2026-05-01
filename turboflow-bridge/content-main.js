// Content Script — MAIN world
// Runs in the page's JS context to intercept fetch responses from Google Flow APIs.
// Forwards intercepted data to the ISOLATED world via window.postMessage.

(function () {
  'use strict';

  const originalFetch = window.fetch;

  const INTERCEPT_PATTERNS = [
    'batchGenerateImages',
    'flowWorkflows',
    'media.getMediaUrlRedirect',
  ];

  const SKIP_PATTERNS = [
    'upsampleImage',
    'recaptcha',
    'gstatic.com',
  ];

  window.fetch = async function (...args) {
    const input = args[0];
    const url = typeof input === 'string' ? input : input?.url || '';

    if (SKIP_PATTERNS.some((p) => url.includes(p))) {
      return originalFetch.apply(this, args);
    }

    if (!INTERCEPT_PATTERNS.some((p) => url.includes(p))) {
      return originalFetch.apply(this, args);
    }

    try {
      const method = (args[1] || {}).method || 'GET';
      const response = await originalFetch.apply(this, args);

      response
        .clone()
        .text()
        .then((text) => {
          let data = null;
          try {
            data = JSON.parse(text);
          } catch {
            data = text;
          }

          let eventType = 'UNKNOWN';
          if (url.includes('batchGenerateImages') && method === 'POST') {
            eventType = 'BATCH_GENERATE_RESPONSE';
          } else if (url.includes('flowWorkflows') && method === 'PATCH') {
            eventType = 'WORKFLOW_UPDATE';
          } else if (url.includes('flowWorkflows') && method === 'GET') {
            eventType = 'WORKFLOW_STATUS';
          } else if (url.includes('media.getMediaUrlRedirect')) {
            eventType = 'MEDIA_REDIRECT';
          }

          window.postMessage(
            {
              type: 'BRIDGE_INTERCEPT',
              eventType,
              url,
              method,
              status: response.status,
              data,
              timestamp: Date.now(),
            },
            '*'
          );
        })
        .catch(() => {});

      return response;
    } catch (err) {
      throw err;
    }
  };
})();
