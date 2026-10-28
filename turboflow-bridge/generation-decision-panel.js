export function renderGenerationDecisionPanel(container, records, busy, choose, now = Date.now()) {
  container.replaceChildren();
  container.classList.toggle('hidden', records.length === 0);
  const doc = container.ownerDocument;
  for (const record of records) {
    const row = doc.createElement('div');
    row.className = 'generation-decision';
    row.title = record.error || '';
    if (record.sourceThumb) {
      const image = doc.createElement('img');
      image.src = record.sourceThumb;
      image.alt = '待处理原图';
      row.appendChild(image);
    }
    const body = doc.createElement('div');
    body.className = 'generation-decision-body';
    const title = doc.createElement('strong');
    title.textContent = `生成被安全策略拒绝 · ${record.targetLang || ''}`;
    const detail = doc.createElement('p');
    detail.textContent = record.returned
      ? `任务 ${record.subTaskId} 已退回。选择原图后，本 Bridge 再次收到该图时按原图完成。`
      : `任务 ${record.subTaskId} 等待选择；使用原图将按现有原图策略完成。`;
    if (!record.action && Number.isFinite(record.deadlineAt)) {
      const seconds = Math.max(0, Math.ceil((record.deadlineAt - now) / 1000));
      detail.textContent += seconds > 0 ? ` ${seconds} 秒内未选择将自动使用原图。` : ' 正在等待自动使用原图。';
    }
    const actions = doc.createElement('div');
    actions.className = 'generation-decision-actions';
    for (const [action, label] of [['retry', '重试'], ['original', '使用原图']]) {
      const button = doc.createElement('button');
      button.className = 'btn-open-flow';
      button.textContent = record.action === action ? `继续${label}` : label;
      button.disabled = busy || (!!record.action && record.action !== action);
      button.addEventListener('click', () => choose(record.id, action));
      actions.appendChild(button);
    }
    body.append(title, detail, actions);
    row.appendChild(body);
    container.appendChild(row);
  }
}
