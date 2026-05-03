(function () {
  /* ── DOM refs ── */
  const bridgeIdEl = document.getElementById('bridge-id');
  const statusDot = document.getElementById('status-dot');
  const statusText = document.getElementById('status-text');
  const btnOpenFlow = document.getElementById('btn-open-flow');
  const currentTaskEl = document.getElementById('current-task');
  const countdownEl = document.getElementById('countdown');
  const taskTbody = document.getElementById('task-tbody');
  const taskEmpty = document.getElementById('task-empty');
  const taskTable = document.getElementById('task-table');
  const paginationEl = document.getElementById('pagination');

  const servicesEl = document.getElementById('services');
  const btnAddService = document.getElementById('btn-add-service');
  const btnSave = document.getElementById('btn-save');
  const toastEl = document.getElementById('toast');

  const logsEl = document.getElementById('logs');
  const logCountEl = document.getElementById('log-count');
  const btnClearLogs = document.getElementById('btn-clear-logs');
  const chkAutoScroll = document.getElementById('chk-auto-scroll');
  const logsContainer = document.querySelector('.logs-container');

  const statTotalEl = document.getElementById('stat-total');
  const statSuccessEl = document.getElementById('stat-success');
  const statFailedEl = document.getElementById('stat-failed');

  /* ── State ── */
  const PAGE_SIZE = 20;
  let currentPage = 0;
  let totalTasks = 0;
  let connected = false;
  let autoCheckTimer = null;
  let countdownTimer = null;
  let nextPollAt = 0;
  let services = [];
  let logsLoaded = false;
  let autoScrollLogs = true;

  /* ── SPA Navigation ── */
  document.querySelectorAll('[data-nav]').forEach((btn) => {
    btn.addEventListener('click', () => navigate(btn.dataset.nav));
  });

  function navigate(viewId) {
    document.querySelectorAll('.view').forEach((v) => v.classList.remove('active'));
    const target = document.getElementById('view-' + viewId);
    if (target) target.classList.add('active');

    if (viewId === 'settings') loadSettings();
    if (viewId === 'logs') loadLogs();
  }

  /* ── Main View ── */
  btnOpenFlow.addEventListener('click', () => {
    chrome.runtime.sendMessage({ type: 'OPEN_FLOW' });
    log('info', 'Opening Google Flow tab');
  });

  chrome.runtime.onMessage.addListener((msg) => {
    if (msg.type === 'CONNECTION_CHANGED') {
      setConnection(msg.connected, msg.message || (msg.connected ? 'Connected' : 'Disconnected'), msg.projectId);
    }
    if (msg.type === 'TASK_CHANGED') {
      renderCurrentTask(msg.currentTask);
      if (!msg.currentTask) {
        loadTaskHistory();
        loadTodayStats();
      }
    }
    if (msg.type === 'COUNTDOWN_UPDATE') {
      nextPollAt = msg.nextPollAt || 0;
    }
    if (msg.type === 'BRIDGE_LOG') {
      prependLogEntry(msg.level || 'info', msg.message || '', msg.time);
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
    loadTodayStats();
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

  let elapsedTimer = null;
  let activeTask = null;

  function renderCurrentTask(task) {
    activeTask = task;
    if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null; }

    if (!task) {
      currentTaskEl.className = 'task-card idle';
      currentTaskEl.innerHTML = '<span class="task-status-badge idle">Idle</span>';
      return;
    }
    currentTaskEl.className = 'task-card running';
    updateCurrentTaskContent();
    elapsedTimer = setInterval(updateCurrentTaskContent, 1000);
  }

  function updateCurrentTaskContent() {
    const task = activeTask;
    if (!task) return;
    const elapsed = Math.round((Date.now() - task.startedAt) / 1000);
    const imgHtml = task.sourceThumb
      ? `<div class="task-thumb-wrap"><img class="task-thumb" src="${task.sourceThumb}" alt="source"><div class="thumb-preview"><img src="${task.sourceThumb}" alt="preview"></div></div>`
      : '';
    const promptHtml = task.targetLang
      ? `<div class="task-prompt" title="${escAttr(task.prompt || '')}">${esc(task.targetLang)}</div>`
      : '';
    currentTaskEl.innerHTML = `
      <span class="task-status-badge running">Running</span>
      <span class="task-elapsed">${elapsed}s</span>
      <div class="task-detail">
        ${imgHtml}
        <div class="task-meta">
          <div><span class="label">SubTask:</span>${esc(task.subTaskId || '-')}</div>
          <div><span class="label">Server:</span>${esc(shortenUrl(task.service))}</div>
          ${promptHtml}
        </div>
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
      const sourceThumbHtml = t.sourceThumb
        ? `<div class="hist-thumb-wrap"><img class="hist-thumb" src="${t.sourceThumb}" alt="src"><div class="thumb-preview"><img src="${t.sourceThumb}" alt="preview"></div></div>`
        : '<span class="no-img">-</span>';
      const resultThumbHtml = t.resultThumb
        ? `<div class="hist-thumb-wrap"><img class="hist-thumb" src="${t.resultThumb}" alt="res"><div class="thumb-preview"><img src="${t.resultThumb}" alt="preview"></div></div>`
        : '<span class="no-img">-</span>';
      tr.innerHTML = `
        <td><div class="td-imgs">${sourceThumbHtml}${resultThumbHtml}</div></td>
        <td><span class="status-tag ${statusCls}">${esc(t.status)}</span></td>
        <td>${elapsedStr}</td>
        <td>${timeStr}</td>
        <td class="td-prompt">${t.targetLang ? esc(t.targetLang) : '-'}</td>
      `;
      taskTbody.appendChild(tr);
    });

    renderPagination();
  }

  function renderPagination() {
    paginationEl.innerHTML = '';
    const totalPages = Math.ceil(totalTasks / PAGE_SIZE);
    if (totalPages <= 1) return;

    addPageBtn('‹', currentPage > 0, () => { currentPage--; loadTaskHistory(); });

    for (let i = 0; i < totalPages; i++) {
      if (totalPages > 7 && i > 1 && i < totalPages - 2 && Math.abs(i - currentPage) > 1) {
        if (paginationEl.lastChild?.textContent !== '…') {
          const dots = document.createElement('span');
          dots.textContent = '…';
          dots.style.cssText = 'color:var(--text-muted);font-size:11px;padding:0 2px';
          paginationEl.appendChild(dots);
        }
        continue;
      }
      const btn = addPageBtn(String(i + 1), true, () => { currentPage = i; loadTaskHistory(); });
      if (i === currentPage) btn.className += ' active';
    }

    addPageBtn('›', currentPage < totalPages - 1, () => { currentPage++; loadTaskHistory(); });
  }

  function addPageBtn(text, enabled, handler) {
    const btn = document.createElement('button');
    btn.textContent = text;
    btn.disabled = !enabled;
    if (enabled) btn.addEventListener('click', handler);
    paginationEl.appendChild(btn);
    return btn;
  }

  /* ── Settings View ── */
  btnAddService.addEventListener('click', () => {
    services.push({ baseUrl: '', token: '', enabled: true });
    renderServices();
    log('info', `Service added (total: ${services.length})`);
  });
  btnSave.addEventListener('click', saveConfig);

  async function loadSettings() {
    const config = await chrome.runtime.sendMessage({ type: 'GET_CONFIG' });
    services = config.services || [];
    renderServices();
  }

  function renderServices() {
    servicesEl.innerHTML = '';
    if (services.length === 0) {
      const empty = document.createElement('div');
      empty.className = 'empty';
      empty.textContent = 'No services configured. Click "+ Add Service" to start.';
      servicesEl.appendChild(empty);
      return;
    }
    services.forEach((service, index) => {
      const row = document.createElement('div');
      row.className = 'service-row';
      row.innerHTML = `
        <label><input type="checkbox" class="enabled" ${service.enabled !== false ? 'checked' : ''}> Enabled</label>
        <input class="base-url" placeholder="https://admin.example.com" value="${esc(service.baseUrl || '')}">
        <input class="token" placeholder="TurboFlow AI Account API Key" type="password" value="${esc(service.token || '')}">
        <button class="remove">Remove</button>
      `;
      row.querySelector('.enabled').addEventListener('change', (e) => service.enabled = e.target.checked);
      row.querySelector('.base-url').addEventListener('input', (e) => service.baseUrl = e.target.value);
      row.querySelector('.token').addEventListener('input', (e) => service.token = e.target.value);
      row.querySelector('.remove').addEventListener('click', () => {
        const removed = services.splice(index, 1)[0];
        renderServices();
        log('info', `Service removed: ${removed.baseUrl || '(empty)'} (total: ${services.length})`);
      });
      servicesEl.appendChild(row);
    });
  }

  async function saveConfig() {
    await chrome.runtime.sendMessage({ type: 'SAVE_CONFIG', config: { services } });
    showToast('Settings saved');
  }

  function showToast(message) {
    toastEl.textContent = message;
    toastEl.classList.remove('hidden');
    setTimeout(() => toastEl.classList.add('hidden'), 2000);
  }

  /* ── Logs View ── */
  chkAutoScroll.addEventListener('change', () => {
    autoScrollLogs = chkAutoScroll.checked;
  });

  btnClearLogs.addEventListener('click', async () => {
    await chrome.runtime.sendMessage({ type: 'CLEAR_LOGS' });
    logsEl.innerHTML = '';
    logCountEl.textContent = '0 entries';
  });

  async function loadLogs() {
    const result = await chrome.runtime.sendMessage({ type: 'GET_LOGS' });
    const logs = result.logs || [];
    logCountEl.textContent = `${logs.length} entries`;
    logsEl.innerHTML = '';
    logs.forEach((entry) => {
      logsEl.appendChild(createLogEntry(entry.level || 'info', entry.message || '', entry.time));
    });
    logsLoaded = true;
  }

  function prependLogEntry(level, message, time) {
    if (!logsLoaded) return;
    const el = createLogEntry(level, message, time);
    logsEl.prepend(el);
    while (logsEl.children.length > 500) logsEl.lastChild.remove();
    logCountEl.textContent = `${logsEl.children.length} entries`;
    if (autoScrollLogs && logsContainer) logsContainer.scrollTop = 0;
  }

  function createLogEntry(level, message, time) {
    const el = document.createElement('div');
    el.className = 'log ' + level;
    const timeStr = time ? new Date(time).toLocaleTimeString() : new Date().toLocaleTimeString();
    el.innerHTML = `<span class="log-time">${timeStr}</span> ${esc(message)}`;
    return el;
  }

  function log(level, message) {
    chrome.runtime.sendMessage({ type: 'LOG', level, message }).catch(() => {});
  }

  /* ── Today Stats ── */
  async function loadTodayStats() {
    const result = await chrome.runtime.sendMessage({ type: 'GET_TODAY_STATS' });
    statTotalEl.textContent = result.total || 0;
    statSuccessEl.textContent = result.success || 0;
    statFailedEl.textContent = result.failed || 0;
  }

  /* ── Utilities ── */
  function shortenUrl(url) {
    if (!url) return '-';
    try { return new URL(url).host; } catch { return url.substring(0, 30); }
  }

  function esc(value) {
    return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function escAttr(value) {
    return esc(value).replace(/\n/g, '&#10;').replace(/\r/g, '');
  }

  init();
})();
