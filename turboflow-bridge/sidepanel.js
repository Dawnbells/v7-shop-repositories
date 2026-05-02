(function () {
  const bridgeIdEl = document.getElementById('bridge-id');
  const statusDot = document.getElementById('status-dot');
  const statusText = document.getElementById('status-text');
  const btnOpenFlow = document.getElementById('btn-open-flow');
  const btnSettings = document.getElementById('btn-settings');
  const btnLogs = document.getElementById('btn-logs');
  const currentTaskEl = document.getElementById('current-task');
  const countdownEl = document.getElementById('countdown');
  const taskTbody = document.getElementById('task-tbody');
  const taskEmpty = document.getElementById('task-empty');
  const taskTable = document.getElementById('task-table');
  const paginationEl = document.getElementById('pagination');

  const PAGE_SIZE = 20;
  let currentPage = 0;
  let totalTasks = 0;
  let connected = false;
  let autoCheckTimer = null;
  let countdownTimer = null;
  let nextPollAt = 0;

  btnOpenFlow.addEventListener('click', () => chrome.runtime.sendMessage({ type: 'OPEN_FLOW' }));
  btnSettings.addEventListener('click', () => chrome.tabs.create({ url: chrome.runtime.getURL('settings.html') }));
  btnLogs.addEventListener('click', () => chrome.tabs.create({ url: chrome.runtime.getURL('logs.html') }));

  chrome.runtime.onMessage.addListener((msg) => {
    if (msg.type === 'CONNECTION_CHANGED') {
      setConnection(msg.connected, msg.message || (msg.connected ? 'Connected' : 'Disconnected'), msg.projectId);
    }
    if (msg.type === 'TASK_CHANGED') {
      renderCurrentTask(msg.currentTask);
      if (!msg.currentTask) loadTaskHistory();
    }
    if (msg.type === 'COUNTDOWN_UPDATE') {
      nextPollAt = msg.nextPollAt || 0;
    }
  });

  async function init() {
    const config = await chrome.runtime.sendMessage({ type: 'GET_CONFIG' });
    bridgeIdEl.textContent = config.bridgeId || '-';

    const status = await chrome.runtime.sendMessage({ type: 'GET_STATUS' });
    if (status) {
      renderCurrentTask(status.currentTask);
      nextPollAt = status.nextPollAt || 0;
      if (status.lastStatus) {
        setConnection(status.lastStatus.connected, status.lastStatus.message, status.lastStatus.projectId);
      }
    }

    await doCheck();
    loadTaskHistory();
    startCountdownTicker();
  }

  async function doCheck() {
    try {
      const response = await chrome.runtime.sendMessage({ type: 'CHECK_CONNECTION' });
      setConnection(response.connected, response.reason || 'Connected', response.projectId);
    } catch {
      setConnection(false, 'Check failed');
    }
  }

  function setConnection(isConnected, message, projectId) {
    connected = isConnected;
    statusDot.className = 'dot ' + (isConnected ? 'connected' : 'disconnected');
    statusText.textContent = isConnected && projectId
      ? `Connected (${projectId.substring(0, 8)}…)`
      : message;
    btnOpenFlow.classList.toggle('hidden', !!isConnected);
    manageAutoCheck();
  }

  function manageAutoCheck() {
    if (!connected && !autoCheckTimer) {
      autoCheckTimer = setInterval(() => doCheck(), 2000);
    } else if (connected && autoCheckTimer) {
      clearInterval(autoCheckTimer);
      autoCheckTimer = null;
    }
  }

  function renderCurrentTask(task) {
    if (!task) {
      currentTaskEl.className = 'task-card idle';
      currentTaskEl.innerHTML = '<span class="task-status-badge idle">Idle</span>';
      return;
    }
    currentTaskEl.className = 'task-card running';
    const elapsed = Math.round((Date.now() - task.startedAt) / 1000);
    currentTaskEl.innerHTML = `
      <span class="task-status-badge running">Running</span>
      <div class="task-detail">
        <span class="label">SubTask:</span>${esc(task.subTaskId || '-')}<br>
        <span class="label">Server:</span>${esc(shortenUrl(task.service))}<br>
        <span class="label">Elapsed:</span>${elapsed}s
      </div>
    `;
  }

  function startCountdownTicker() {
    countdownTimer = setInterval(() => {
      if (!currentTaskEl.classList.contains('idle') || nextPollAt <= 0) {
        countdownEl.textContent = '';
        return;
      }
      const remaining = Math.max(0, Math.ceil((nextPollAt - Date.now()) / 1000));
      countdownEl.textContent = `next poll ${remaining}s`;
    }, 500);
  }

  async function loadTaskHistory() {
    const result = await chrome.runtime.sendMessage({
      type: 'GET_TASK_HISTORY',
      page: currentPage,
      pageSize: PAGE_SIZE,
    });
    totalTasks = result.total;
    const items = result.items || [];

    if (items.length === 0) {
      taskTable.classList.add('hidden');
      taskEmpty.classList.remove('hidden');
      paginationEl.innerHTML = '';
      return;
    }

    taskTable.classList.remove('hidden');
    taskEmpty.classList.add('hidden');
    taskTbody.innerHTML = '';

    items.forEach((t) => {
      const tr = document.createElement('tr');
      const timeStr = new Date(t.time).toLocaleTimeString();
      const elapsedStr = t.elapsedMs != null ? `${(t.elapsedMs / 1000).toFixed(1)}s` : '-';
      const statusCls = t.status === 'completed' ? 'completed' : 'failed';
      tr.innerHTML = `
        <td title="${esc(t.subTaskId || '')}">${esc((t.subTaskId || '').substring(0, 12))}…</td>
        <td><span class="status-tag ${statusCls}">${esc(t.status)}</span></td>
        <td>${timeStr}</td>
        <td>${elapsedStr}</td>
      `;
      taskTbody.appendChild(tr);
    });

    renderPagination();
  }

  function renderPagination() {
    paginationEl.innerHTML = '';
    const totalPages = Math.ceil(totalTasks / PAGE_SIZE);
    if (totalPages <= 1) return;

    const prev = document.createElement('button');
    prev.textContent = '‹';
    prev.disabled = currentPage === 0;
    prev.addEventListener('click', () => { currentPage--; loadTaskHistory(); });
    paginationEl.appendChild(prev);

    for (let i = 0; i < totalPages; i++) {
      if (totalPages > 7 && i > 1 && i < totalPages - 2 && Math.abs(i - currentPage) > 1) {
        if (paginationEl.lastChild?.textContent !== '…') {
          const dots = document.createElement('span');
          dots.textContent = '…';
          dots.style.cssText = 'color: var(--text-muted); font-size: 11px; padding: 0 2px;';
          paginationEl.appendChild(dots);
        }
        continue;
      }
      const btn = document.createElement('button');
      btn.textContent = String(i + 1);
      if (i === currentPage) btn.className = 'active';
      btn.addEventListener('click', () => { currentPage = i; loadTaskHistory(); });
      paginationEl.appendChild(btn);
    }

    const next = document.createElement('button');
    next.textContent = '›';
    next.disabled = currentPage >= totalPages - 1;
    next.addEventListener('click', () => { currentPage++; loadTaskHistory(); });
    paginationEl.appendChild(next);
  }

  function shortenUrl(url) {
    if (!url) return '-';
    try {
      const u = new URL(url);
      return u.host;
    } catch {
      return url.substring(0, 30);
    }
  }

  function esc(value) {
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  init();
})();
