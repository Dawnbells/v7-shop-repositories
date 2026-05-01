// Content Script — ISOLATED world
// Relays intercepted data from MAIN world (via postMessage) to the background service worker.
// Also handles direct page-state queries from the background.

(function () {
  'use strict';

  window.addEventListener('message', function (event) {
    if (event.source !== window) return;
    if (event.data?.type !== 'BRIDGE_INTERCEPT') return;
    if (!chrome.runtime?.id) return;

    chrome.runtime
      .sendMessage({
        type: 'API_INTERCEPTED',
        eventType: event.data.eventType,
        url: event.data.url,
        method: event.data.method,
        status: event.data.status,
        data: event.data.data,
        timestamp: event.data.timestamp,
      })
      .catch(() => {});
  });

  chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
    if (msg.type === 'GET_PAGE_STATE') {
      const editor = document.querySelector('div[data-slate-editor="true"]');
      sendResponse({
        hasEditor: !!editor,
        currentPrompt: editor?.textContent || '',
        url: window.location.href,
      });
      return true;
    }
    return false;
  });
})();
