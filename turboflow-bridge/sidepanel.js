import { normalizeGenerationMode, generationModeLabel } from './generation-mode.js';

(function () {
  /* ── DOM refs ── */
  const bridgeIdEl = document.getElementById('bridge-id');
  const statusDot = document.getElementById('status-dot');
  const statusText = document.getElementById('status-text');
  const btnOpenFlow = document.getElementById('btn-open-flow');
  const btnRunNow = document.getElementById('btn-run-now');
  const btnDeleteProjects = document.getElementById('btn-delete-projects');
  const operationStatus = document.getElementById('flow-operation-status');
  const currentTaskEl = document.getElementById('current-task');
  const countdownEl = document.getElementById('countdown');
  const taskTbody = document.getElementById('task-tbody');
  const taskEmpty = document.getElementById('task-empty');
  const taskTable = document.getElementById('task-table');
  const paginationEl = document.getElementById('pagination');

  const servicesEl = document.getElementById('services');
  const generationModeEl = document.getElementById('generation-mode');
  const flowConcurrencyEl = document.getElementById('flow-concurrency');
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
  const statPolicyEl = document.getElementById('stat-policy');
  const statTotalAllEl = document.getElementById('stat-total-all');
  const statSuccessAllEl = document.getElementById('stat-success-all');
  const statFailedAllEl = document.getElementById('stat-failed-all');
  const statPolicyAllEl = document.getElementById('stat-policy-all');
  const reuseHintEl = document.getElementById('reuse-hint');
  const btnResetStats = document.getElementById('btn-reset-stats');

  /* ── State ── */
  // 历史上限已降到 10 条（background.js MAX_TASK_HISTORY），一页装得下
  const PAGE_SIZE = 10;
  let currentPage = 0;
  let totalTasks = 0;
  let connected = false;
  let paused = false;
  let autoCheckTimer = null;
  let countdownTimer = null;
  let nextPollAt = 0;
  let services = [];
  let generationMode = 'api-2.3.5.1';
  let panelWindowId;
  let cleanupTarget = null;
  let cleanupBusy = false;
  let recoveryBusy = false;
  let statusRefreshing = false;
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
  btnOpenFlow.addEventListener('click', async () => {
    btnOpenFlow.disabled = true;
    btnOpenFlow.textContent = 'Opening Flow...';
    log('info', 'Opening Flow');
    try {
      const response = await chrome.runtime.sendMessage({ type: 'OPEN_FLOW' });
      if (!response?.ok) throw new Error(response?.error || 'Could not open Flow');
      log('info', 'Flow opened. Create or select a project manually.');
    } catch (error) {
      log('error', `Open Flow failed: ${error.message}`);
    } finally {
      btnOpenFlow.disabled = false;
      btnOpenFlow.textContent = 'Open Flow';
    }
  });

  btnRunNow.addEventListener('click', async () => {
    btnRunNow.disabled = true;
    try {
      const response = await chrome.runtime.sendMessage({ type: 'RUN_NOW' });
      if (!response?.ok) throw new Error(response?.error || 'Could not resume');
      log('info', 'Run Now sent');
    } catch (error) { log('error', error.message); }
    finally { await refreshOperationStatus(); }
  });

  btnDeleteProjects.addEventListener('click', async () => {
    if (!cleanupTarget?.allowed || cleanupBusy || recoveryBusy) return;
    cleanupBusy = true;
    btnDeleteProjects.disabled = true;
    try {
      const result = await chrome.runtime.sendMessage({ type: 'DELETE_ALL_FLOW_PROJECTS',
        tabId: cleanupTarget.tabId, windowId: panelWindowId });
      if (!result?.ok) throw new Error(result?.error || '删除失败');
      showToast(`已删除 ${result.deleted}，失败 ${result.failed}，剩余 ${result.remaining}`);
    } catch (error) { showToast(error.message); log('error', error.message); }
    finally { cleanupBusy = false; await refreshOperationStatus(); }
  });

  chrome.runtime.onMessage.addListener((msg) => {
    if (msg.type === 'FLOW_RECOVERY_CHANGED') renderRecovery(msg.recovery);
    if (msg.type === 'FLOW_CLEANUP_PROGRESS') {
      cleanupBusy = !['idle', 'done'].includes(msg.phase);
      operationStatus.classList.toggle('hidden', msg.phase === 'idle');
      operationStatus.textContent = `删除项目：成功 ${msg.deleted || 0}，失败 ${msg.failed || 0}`;
      refreshOperationStatus();
    }
    if (msg.type === 'CONNECTION_CHANGED') {
      setConnection(msg.connected, msg.message || (msg.connected ? 'Connected' : 'Disconnected'), msg.projectId);
    }
    if (msg.type === 'BRIDGE_PAUSED') {
      setPaused(true);
    }
    if (msg.type === 'BRIDGE_RESUMED') {
      setPaused(false);
    }
    if (msg.type === 'TASK_CHANGED') {
      const tasks = Array.isArray(msg.currentTasks)
        ? msg.currentTasks
        : (msg.currentTask ? [msg.currentTask] : []);
      const completedOrFailed = activeTasks.some(previous =>
        !tasks.some(task => task.assignmentId === previous.assignmentId));
      renderCurrentTasks(tasks);
      if (completedOrFailed || tasks.length === 0) {
        loadTaskHistory();
        // STATS_UPDATED 负责实时推送；任务结束时再主动读取一次作为最终一致性兜底，
        // 避免侧边栏刚打开或 Chrome 恢复页面时漏掉广播后一直显示旧值。
        loadStats();
      }
    }
    if (msg.type === 'STATS_UPDATED') {
      renderStats(msg.stats);
    }
    if (msg.type === 'REUSE_SUMMARY_UPDATED') {
      renderReuseSummary(msg.reuseSummary);
    }
    if (msg.type === 'COUNTDOWN_UPDATE') {
      nextPollAt = msg.nextPollAt || 0;
    }
    if (msg.type === 'BRIDGE_LOG') {
      prependLogEntry(msg.level || 'info', msg.message || '', msg.time);
    }
  });

  function setPaused(value) {
    paused = !!value;
    btnRunNow.classList.toggle('hidden', !paused);
    if (paused) {
      btnOpenFlow.classList.add('hidden');
      btnRunNow.textContent = 'Run Now';
    } else {
      btnRunNow.textContent = 'Run Now';
    }
  }

  function renderRecovery(state, count = 0) {
    const labels = { initializing: '正在初始化', draining: `等待剩余任务收尾（${count}）`,
      clearing: '正在清空 Flow 站点存储', closing: '正在关闭 Flow 标签',
      opening: '正在重新打开 Flow 首页', creating: '正在创建项目' };
    recoveryBusy = !!labels[state?.phase];
    if (!cleanupBusy) {
      operationStatus.textContent = labels[state?.phase] || '';
      operationStatus.classList.toggle('hidden', !recoveryBusy);
    }
    btnRunNow.disabled = recoveryBusy || cleanupBusy;
    btnRunNow.classList.toggle('hidden', !paused || recoveryBusy);
    btnOpenFlow.disabled = recoveryBusy || cleanupBusy;
    if (recoveryBusy || cleanupBusy) btnDeleteProjects.disabled = true;
  }

  async function refreshOperationStatus() {
    if (statusRefreshing || !Number.isInteger(panelWindowId)) return;
    statusRefreshing = true;
    try {
      const [status, cleanup] = await Promise.all([
        chrome.runtime.sendMessage({ type: 'GET_STATUS' }),
        chrome.runtime.sendMessage({ type: 'GET_PROJECT_CLEANUP_STATUS', windowId: panelWindowId }),
      ]);
      if (status) {
        cleanupBusy = !!status.deletingProjects;
        setPaused(status.paused);
        renderRecovery(status.recovery, status.drainingCount);
        if (status.paused) { statusText.textContent = status.pauseReason; statusDot.className = 'dot disconnected'; }
      }
      cleanupTarget = cleanup;
      btnDeleteProjects.disabled = !cleanup?.allowed || cleanupBusy || recoveryBusy;
      btnDeleteProjects.title = cleanup?.allowed ? '删除当前账号全部 Flow 项目' : cleanup?.reason || '请先打开 Flow 项目首页';
    } catch {} finally { statusRefreshing = false; }
  }

  async function init() {
    panelWindowId = (await chrome.windows.getCurrent()).id;
    const config = await chrome.runtime.sendMessage({ type: 'GET_CONFIG' });
    bridgeIdEl.textContent = config.bridgeId || '-';
    generationMode = normalizeGenerationMode(config.generationMode);
    updateTestModeLabel();

    const status = await chrome.runtime.sendMessage({ type: 'GET_STATUS' });
    if (status) {
      renderCurrentTasks(Array.isArray(status.currentTasks)
        ? status.currentTasks
        : (status.currentTask ? [status.currentTask] : []));
      nextPollAt = status.nextPollAt || 0;
      if (status.lastStatus) {
        setConnection(status.lastStatus.connected, status.lastStatus.message, status.lastStatus.projectId);
      }
      setPaused(!!status.paused);
      renderReuseSummary(status.reuseSummary);
    }

    await doCheck();
    await refreshOperationStatus();
    loadTaskHistory();
    loadStats();
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
    if (recoveryBusy || cleanupBusy || paused) return;
    connected = isConnected;
    statusDot.className = 'dot ' + (isConnected ? 'connected' : 'disconnected');
    statusText.textContent = isConnected && projectId
      ? `Connected (${projectId.substring(0, 8)}…)`
      : message;
    // paused 状态下隐藏 Open Flow 按钮，由 Run Now 主导
    btnOpenFlow.classList.toggle('hidden', !!isConnected || paused);
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
    currentTaskEl.innerHTML = activeTasks.map((task) => {
      const standby = task.phase === 'standby';
      const phases = {
        downloading_source: ['standby', '获取中'],
        standby: ['standby', '预备'],
        preparing: ['standby', '预备'],
        submitting: ['standby', '上传中'],
        generating: ['running', '翻译中'],
        downloading_result: ['reporting', '下载中'],
        reporting: ['reporting', '回传中'],
        reporting_retry: ['reporting', `回传重试中(${Number(task.reportRetry) || 1})`],
      };
      const [statusClass, statusLabel] = phases[task.phase] || ['running', 'RUNNING'];
      const elapsed = Math.max(0, Math.round((Date.now() - (task.startedAt || task.preparedAt)) / 1000));
      const thumbSrc = task.sourceThumb || task.sourceImage;
      const previewSrc = task.sourceImage || task.sourceThumb;
      const imgHtml = thumbSrc
        ? `<div class="task-thumb-wrap"><img class="task-thumb" src="${thumbSrc}" alt="source"><div class="thumb-preview"><img src="${previewSrc}" alt="preview"></div></div>`
        : '';
      const promptHtml = task.targetLang
        ? `<div class="task-prompt" title="${escAttr(task.prompt || '')}">${esc(task.targetLang)}</div>`
        : '';
      return `
        <div class="task-card ${statusClass}">
          <span class="task-status-badge ${statusClass}">${statusLabel}</span>
          <span class="task-elapsed">${standby ? '图片已就绪' : elapsed + 's'}</span>
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
      } else {
        const remaining = Math.max(0, Math.ceil((nextPollAt - Date.now()) / 1000));
        countdownEl.textContent = `next poll ${remaining}s`;
      }
      // 暂停态：每秒刷新 Run Now 按钮的冷却剩余时间
      refreshOperationStatus();
    }, 1000);
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
      // 政策回退和译图复用都是 completed，但要能一眼区分出来
      const isPolicy = !!t.policyFallbackReason;
      const statusCls = t.status !== 'completed' ? 'failed' : (isPolicy ? 'policy' : 'completed');
      const statusLabel = t.status === 'completed' && t.reused ? 'completed · reused' : t.status;
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
      // 失败任务追加自定义 tooltip 显示 error.message；td-status 配合 overflow:visible 让 tooltip 不被裁剪
      let tooltipText = '';
      if (t.status !== 'completed' && t.error) tooltipText = t.error;
      else if (isPolicy) tooltipText = `内容政策限制，保留原图（${t.policyFallbackReason}）`;
      else if (t.reused) tooltipText = '复用了上一轮已翻译好的图，未再调用 Google';
      const errorTooltip = tooltipText
        ? `<span class="status-tooltip">${esc(tooltipText)}</span>`
        : '';
      tr.innerHTML = `
        <td><div class="td-imgs">${sourceThumbHtml}${resultThumbHtml}</div></td>
        <td class="td-status"><span class="status-tag ${statusCls}">${esc(statusLabel)}${errorTooltip}</span></td>
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
    generationMode = normalizeGenerationMode(config.generationMode);
    generationModeEl.value = generationMode;
    flowConcurrencyEl.value = String(config.flowConcurrency || 4);
    updateTestModeLabel();
    renderServices();
  }

  function updateTestModeLabel() {
    btnTestRun.textContent = `Translate via ${generationModeLabel(generationMode)}`;
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
    const response = await chrome.runtime.sendMessage({
      type: 'SAVE_CONFIG',
      config: { services, generationMode: generationModeEl.value, flowConcurrency: Number(flowConcurrencyEl.value) || 4 },
    });
    if (!response?.ok) throw new Error(response?.error || 'Could not save settings');
    generationMode = normalizeGenerationMode(generationModeEl.value);
    updateTestModeLabel();
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

  /* ── Stats（今日大号 / 累计小号） ── */
  async function loadStats() {
    const result = await chrome.runtime.sendMessage({ type: 'GET_STATS' });
    renderStats(result?.stats);
  }

  function renderStats(stats) {
    if (!stats) return;
    const today = stats.today || {};
    const allTime = stats.allTime || {};
    statTotalEl.textContent = today.total || 0;
    statSuccessEl.textContent = today.success || 0;
    statFailedEl.textContent = today.failed || 0;
    statPolicyEl.textContent = today.policy || 0;
    statTotalAllEl.textContent = allTime.total || 0;
    statSuccessAllEl.textContent = allTime.success || 0;
    statFailedAllEl.textContent = allTime.failed || 0;
    statPolicyAllEl.textContent = allTime.policy || 0;
  }

  /** 待重投译图提示：没有记录就完全不显示，不占常驻空间 */
  function renderReuseSummary(summary) {
    const count = summary?.count || 0;
    if (count <= 0) {
      reuseHintEl.classList.add('hidden');
      reuseHintEl.textContent = '';
      return;
    }
    const remaining = summary.minRemaining || 0;
    reuseHintEl.textContent = `♻️ ${count} 张译图待重投（最紧的还剩 ${remaining} 次匹配机会）`;
    reuseHintEl.classList.remove('hidden');
  }

  btnResetStats?.addEventListener('click', async () => {
    const result = await chrome.runtime.sendMessage({ type: 'RESET_STATS' });
    renderStats(result?.stats);
    showToast('Stats reset');
  });

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
  const testStealth = document.getElementById('test-stealth');
  const testAutoClearCache = document.getElementById('test-auto-clear-cache');
  const testDelayMin = document.getElementById('test-delay-min');
  const testDelayMax = document.getElementById('test-delay-max');
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

  async function runTestTranslate(type, label) {
    if (!testImageData) return;
    btnTestRun.disabled = true;
    testStatusEl.classList.remove('hidden');
    testStatusEl.className = 'test-status running';
    testStatusEl.textContent = `${label}...`;
    testResultEl.classList.add('hidden');
    const startTime = Date.now();
    const timer = setInterval(() => {
      const sec = Math.round((Date.now() - startTime) / 1000);
      testStatusEl.textContent = `${label}... ${sec}s`;
    }, 1000);

    try {
      const result = await chrome.runtime.sendMessage({
        type,
        imageBase64: testImageData.dataUrl,
        fileName: testImageData.name,
        mimeType: testImageData.type,
        width: testImageData.width,
        height: testImageData.height,
        aspectRatio: testAspect.value,
        model: testModel.value,
        targetLanguage: testLang.value || 'Simplified Chinese',
        stealthMode: !!testStealth.checked,
        autoClearCache: !!testAutoClearCache.checked,
        delayMin: Number(testDelayMin.value || 0),
        delayMax: Number(testDelayMax.value || 0),
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
  }

  btnTestRun.addEventListener('click', () => runTestTranslate(
    'TEST_TRANSLATE', `Translating through ${generationModeLabel(generationMode)}`,
  ));

  init();
})();
