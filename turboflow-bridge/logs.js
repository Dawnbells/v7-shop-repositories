(function () {
  const logsEl = document.getElementById('logs');
  const logCountEl = document.getElementById('log-count');
  const btnBack = document.getElementById('btn-back');
  const btnClear = document.getElementById('btn-clear');

  btnBack.addEventListener('click', () => window.close());
  btnClear.addEventListener('click', async () => {
    await chrome.runtime.sendMessage({ type: 'CLEAR_LOGS' });
    logsEl.innerHTML = '';
    logCountEl.textContent = '0 entries';
  });

  chrome.runtime.onMessage.addListener((msg) => {
    if (msg.type === 'BRIDGE_LOG') {
      prependLog(msg.level || 'info', msg.message || '', msg.time);
      trimLogs();
    }
  });

  async function init() {
    const result = await chrome.runtime.sendMessage({ type: 'GET_LOGS' });
    const logs = result.logs || [];
    logCountEl.textContent = `${logs.length} entries`;

    logs.forEach((entry) => {
      appendLog(entry.level || 'info', entry.message || '', entry.time);
    });
  }

  function prependLog(level, message, time) {
    const el = createLogEntry(level, message, time);
    logsEl.prepend(el);
    updateCount();
  }

  function appendLog(level, message, time) {
    const el = createLogEntry(level, message, time);
    logsEl.appendChild(el);
    updateCount();
  }

  function createLogEntry(level, message, time) {
    const el = document.createElement('div');
    el.className = 'log ' + level;
    const timeStr = time ? new Date(time).toLocaleTimeString() : new Date().toLocaleTimeString();
    el.innerHTML = `<span class="log-time">${timeStr}</span> ${escapeHtml(message)}`;
    return el;
  }

  function trimLogs() {
    while (logsEl.children.length > 500) {
      logsEl.lastChild.remove();
    }
  }

  function updateCount() {
    logCountEl.textContent = `${logsEl.children.length} entries`;
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  init();
})();
