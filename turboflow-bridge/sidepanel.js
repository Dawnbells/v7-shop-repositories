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
  const concurrencyEl = document.getElementById('concurrency');
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
      const tasks = Array.isArray(msg.currentTasks)
        ? msg.currentTasks
        : (msg.currentTask ? [msg.currentTask] : []);
      const completedOrFailed = tasks.length < activeTasks.length;
      renderCurrentTasks(tasks);
      if (completedOrFailed || tasks.length === 0) {
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
      renderCurrentTasks(Array.isArray(status.currentTasks)
        ? status.currentTasks
        : (status.currentTask ? [status.currentTask] : []));
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
  let activeTasks = [];

  function renderCurrentTasks(tasks) {
    activeTasks = Array.isArray(tasks) ? tasks : [];
    if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null; }

    if (activeTasks.length === 0) {
      currentTaskEl.className = 'task-list idle';
      currentTaskEl.innerHTML = '<span class="task-status-badge idle">Idle</span>';
      return;
    }
    currentTaskEl.className = 'task-list running';
    updateCurrentTaskContent();
    elapsedTimer = setInterval(updateCurrentTaskContent, 1000);
  }

  function updateCurrentTaskContent() {
    if (activeTasks.length === 0) return;
    currentTaskEl.innerHTML = activeTasks.map((task, index) => {
      const elapsed = Math.round((Date.now() - task.startedAt) / 1000);
      const thumbSrc = task.sourceThumb || task.sourceImage;
      const previewSrc = task.sourceImage || task.sourceThumb;
      const imgHtml = thumbSrc
        ? `<div class="task-thumb-wrap"><img class="task-thumb" src="${thumbSrc}" alt="source"><div class="thumb-preview"><img src="${previewSrc}" alt="preview"></div></div>`
        : '';
      const promptHtml = task.targetLang
        ? `<div class="task-prompt" title="${escAttr(task.prompt || '')}">${esc(task.targetLang)}</div>`
        : '';
      return `
        <div class="task-card running">
          <span class="task-status-badge running">Running ${index + 1}</span>
          <span class="task-elapsed">${elapsed}s</span>
          <div class="task-detail">
            ${imgHtml}
            <div class="task-meta">
              <div><span class="label">SubTask:</span>${esc(task.subTaskId || '-')}</div>
              <div><span class="label">Server:</span>${esc(shortenUrl(task.service))}</div>
              ${promptHtml}
            </div>
          </div>
        </div>
      `;
    }).join('');
  }

  function startCountdownTicker() {
    countdownTimer = setInterval(() => {
      // 只要 background 维护着 nextPollAt(>0) 就显示倒计时
      // background 端仅在并发槽位已满时才把 nextPollAt 设为 0,
      // 因此只要还能接新任务,前端都会看到倒计时,而不局限于完全 Idle
      if (nextPollAt <= 0) {
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
      const srcThumb = t.sourceThumb || t.sourceImage;
      const srcPreview = t.sourceImage || t.sourceThumb;
      const resThumb = t.resultThumb || t.resultImage;
      const resPreview = t.resultImage || t.resultThumb;
      const sourceThumbHtml = srcThumb
        ? `<div class="hist-thumb-wrap"><img class="hist-thumb" src="${srcThumb}" alt="src"><div class="thumb-preview"><img src="${srcPreview}" alt="preview"></div></div>`
        : '<span class="no-img">-</span>';
      const resultThumbHtml = resThumb
        ? `<div class="hist-thumb-wrap"><img class="hist-thumb" src="${resThumb}" alt="res"><div class="thumb-preview"><img src="${resPreview}" alt="preview"></div></div>`
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
    if (concurrencyEl) concurrencyEl.value = config.concurrency || 1;
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
    await chrome.runtime.sendMessage({
      type: 'SAVE_CONFIG',
      config: { services, concurrency: concurrencyEl ? concurrencyEl.value : 1 },
    });
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

  /* ── Test Translate View ── */
  const testFile = document.getElementById('test-file');
  const testDropZone = document.getElementById('test-drop-zone');
  const testDropPlaceholder = document.getElementById('test-drop-placeholder');
  const testPreview = document.getElementById('test-preview');
  const testAspect = document.getElementById('test-aspect');
  const testModel = document.getElementById('test-model');
  const testLang = document.getElementById('test-lang');
  const testPrompt = document.getElementById('test-prompt');
  const btnTestRun = document.getElementById('btn-test-run');
  const testStatusEl = document.getElementById('test-status');
  const testResultEl = document.getElementById('test-result');
  const testResultSrc = document.getElementById('test-result-src');
  const testResultOut = document.getElementById('test-result-out');

  let testImageData = null;

  testDropZone.addEventListener('click', () => testFile.click());
  testDropZone.addEventListener('dragover', (e) => { e.preventDefault(); testDropZone.classList.add('dragover'); });
  testDropZone.addEventListener('dragleave', () => testDropZone.classList.remove('dragover'));
  testDropZone.addEventListener('drop', (e) => {
    e.preventDefault();
    testDropZone.classList.remove('dragover');
    const file = e.dataTransfer.files[0];
    if (file && file.type.startsWith('image/')) loadTestImage(file);
  });
  testFile.addEventListener('change', () => {
    if (testFile.files[0]) loadTestImage(testFile.files[0]);
  });

  function loadTestImage(file) {
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        testImageData = { dataUrl: reader.result, width: img.width, height: img.height, name: file.name, type: file.type };
        testPreview.src = reader.result;
        testPreview.classList.remove('hidden');
        testDropPlaceholder.classList.add('hidden');
        btnTestRun.disabled = false;
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  }

  btnTestRun.addEventListener('click', async () => {
    if (!testImageData) return;
    btnTestRun.disabled = true;
    testStatusEl.classList.remove('hidden');
    testStatusEl.className = 'test-status running';
    testStatusEl.textContent = 'Translating...';
    testResultEl.classList.add('hidden');
    const startTime = Date.now();
    const timer = setInterval(() => {
      const sec = Math.round((Date.now() - startTime) / 1000);
      testStatusEl.textContent = `Translating... ${sec}s`;
    }, 1000);

    try {
      const result = await chrome.runtime.sendMessage({
        type: 'TEST_TRANSLATE',
        imageBase64: testImageData.dataUrl,
        fileName: testImageData.name,
        mimeType: testImageData.type,
        width: testImageData.width,
        height: testImageData.height,
        aspectRatio: testAspect.value,
        model: testModel.value,
        targetLanguage: testLang.value || 'Simplified Chinese',
        prompt: testPrompt.value || '',
      });
      clearInterval(timer);
      if (result.ok) {
        const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
        testStatusEl.className = 'test-status success';
        testStatusEl.textContent = `Done in ${elapsed}s`;
        testResultSrc.src = testImageData.dataUrl;
        testResultOut.src = result.resultDataUrl;
        testResultEl.classList.remove('hidden');
      } else {
        testStatusEl.className = 'test-status error';
        testStatusEl.textContent = 'Failed: ' + (result.error || 'Unknown error');
      }
    } catch (e) {
      clearInterval(timer);
      testStatusEl.className = 'test-status error';
      testStatusEl.textContent = 'Error: ' + e.message;
    }
    btnTestRun.disabled = false;
  });

  init();
})();
