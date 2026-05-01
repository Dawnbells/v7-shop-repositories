(function () {
  const bridgeIdEl = document.getElementById('bridge-id');
  const statusDot = document.getElementById('status-dot');
  const statusText = document.getElementById('status-text');
  const btnCheck = document.getElementById('btn-check');
  const btnOpenFlow = document.getElementById('btn-open-flow');
  const servicesEl = document.getElementById('services');
  const btnAddService = document.getElementById('btn-add-service');
  const btnSave = document.getElementById('btn-save');
  const taskState = document.getElementById('task-state');
  const logs = document.getElementById('logs');

  let services = [];

  btnCheck.addEventListener('click', checkConnection);
  btnOpenFlow.addEventListener('click', () => chrome.runtime.sendMessage({ type: 'OPEN_FLOW' }));
  btnAddService.addEventListener('click', () => {
    services.push({ baseUrl: '', token: '', enabled: true });
    renderServices();
  });
  btnSave.addEventListener('click', saveConfig);

  chrome.runtime.onMessage.addListener((msg) => {
    if (msg.type === 'CONNECTION_CHANGED') {
      setConnection(msg.connected, msg.message || (msg.connected ? 'Connected' : 'Disconnected'), msg.projectId);
    }
    if (msg.type === 'TASK_CHANGED') {
      taskState.textContent = msg.currentTask ? JSON.stringify(msg.currentTask, null, 2) : 'Idle';
    }
    if (msg.type === 'BRIDGE_LOG') {
      appendLog(msg.level || 'info', msg.message || '');
    }
  });

  async function init() {
    const config = await chrome.runtime.sendMessage({ type: 'GET_CONFIG' });
    bridgeIdEl.textContent = config.bridgeId || '-';
    services = config.services || [];
    renderServices();
    const status = await chrome.runtime.sendMessage({ type: 'GET_STATUS' });
    if (status?.currentTask) taskState.textContent = JSON.stringify(status.currentTask, null, 2);
    await checkConnection();
  }

  async function checkConnection() {
    setConnection(false, 'Checking...');
    const response = await chrome.runtime.sendMessage({ type: 'CHECK_CONNECTION' });
    setConnection(response.connected, response.reason || 'Connected', response.projectId);
  }

  function setConnection(connected, message, projectId) {
    statusDot.className = 'dot ' + (connected ? 'connected' : 'disconnected');
    statusText.textContent = connected && projectId
      ? `Connected to Flow (${projectId.substring(0, 8)}...)`
      : message;
    btnOpenFlow.classList.toggle('hidden', !!connected);
  }

  function renderServices() {
    servicesEl.innerHTML = '';
    if (services.length === 0) {
      const empty = document.createElement('div');
      empty.className = 'empty';
      empty.textContent = 'No services configured.';
      servicesEl.appendChild(empty);
      return;
    }
    services.forEach((service, index) => {
      const row = document.createElement('div');
      row.className = 'service-row';
      row.innerHTML = `
        <label><input type="checkbox" class="enabled" ${service.enabled !== false ? 'checked' : ''}> Enabled</label>
        <input class="base-url" placeholder="https://admin.example.com" value="${escapeHtml(service.baseUrl || '')}">
        <input class="token" placeholder="TurboFlow AI Account API Key" type="password" value="${escapeHtml(service.token || '')}">
        <button class="remove">Remove</button>
      `;
      row.querySelector('.enabled').addEventListener('change', (e) => service.enabled = e.target.checked);
      row.querySelector('.base-url').addEventListener('input', (e) => service.baseUrl = e.target.value);
      row.querySelector('.token').addEventListener('input', (e) => service.token = e.target.value);
      row.querySelector('.remove').addEventListener('click', () => {
        services.splice(index, 1);
        renderServices();
      });
      servicesEl.appendChild(row);
    });
  }

  async function saveConfig() {
    await chrome.runtime.sendMessage({ type: 'SAVE_CONFIG', config: { services } });
    appendLog('info', 'Config saved');
  }

  function appendLog(level, message) {
    const item = document.createElement('div');
    item.className = 'log ' + level;
    item.textContent = `[${new Date().toLocaleTimeString()}] ${message}`;
    logs.prepend(item);
    while (logs.children.length > 80) logs.lastChild.remove();
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
