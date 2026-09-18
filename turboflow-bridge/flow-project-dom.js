// DOM adapter for the current flow.google.com project home page.
// These functions are passed directly to chrome.scripting.executeScript, so
// they must remain self-contained and only depend on their arguments + DOM.

export function inspectModernFlowProjectPage() {
  const readProjectId = (value) => {
    const match = String(value || '').match(/(?:^|\/)project\/([a-z0-9-]+)/i);
    return match?.[1] || null;
  };
  const createButton = document.querySelector('button.new-project-button');
  const cards = Array.from(document.querySelectorAll('flow-project-card'));
  const projects = cards.map((card) => {
    const link = card.querySelector('a[href*="/project/"]');
    const projectId = readProjectId(link?.getAttribute('href'));
    const title = card.querySelector('.project-title-label')?.childNodes?.[0]?.textContent?.trim()
      || card.querySelector('.project-title-label')?.textContent?.replace(/\b(edit|delete)\b/gi, '').trim()
      || '';
    return projectId ? { projectId, title } : null;
  }).filter(Boolean);
  if (projects.length !== cards.length) {
    return { ready: true, canCreate: !!createButton, projects: [], error: 'Invalid Flow project card' };
  }
  return {
    ready: !!createButton && !createButton.disabled,
    canCreate: !!createButton,
    projects,
  };
}

export function clickModernFlowNewProject() {
  const button = document.querySelector('button.new-project-button');
  if (!button) return { error: 'Flow New project button not found' };
  if (button.disabled) return { error: 'Flow New project button is still disabled' };
  button.click();
  return { clicked: true };
}

export async function deleteModernFlowProject(projectId, timeoutMs = 30000, pollMs = 100) {
  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const readProjectId = (value) => {
    const match = String(value || '').match(/(?:^|\/)project\/([a-z0-9-]+)/i);
    return match?.[1] || null;
  };
  const findButtonWithIcon = (root, iconName) => Array.from(root?.querySelectorAll?.('button') || [])
    .find((button) => Array.from(button.querySelectorAll?.('mat-icon') || [])
      .some((icon) => icon.textContent?.trim() === iconName));
  const findCard = () => Array.from(document.querySelectorAll('flow-project-card')).find((card) => {
    const link = card.querySelector('a[href*="/project/"]');
    return readProjectId(link?.getAttribute('href')) === projectId;
  });

  const card = findCard();
  if (!card) return { error: `Flow project card not found: ${projectId}` };
  const deleteButton = findButtonWithIcon(card, 'delete');
  if (!deleteButton) return { error: `Flow project delete button not found: ${projectId}` };
  deleteButton.click();

  const deadline = Date.now() + timeoutMs;
  let dialog = null;
  while (Date.now() < deadline) {
    dialog = document.querySelector('mat-dialog-container[role="dialog"]');
    if (dialog) break;
    await sleep(pollMs);
  }
  if (!dialog) return { error: `Flow delete confirmation did not open: ${projectId}` };

  const actionButtons = Array.from(dialog.querySelectorAll('mat-dialog-actions button'));
  const confirmButton = actionButtons.at(-1);
  if (!confirmButton) return { error: `Flow delete confirmation button not found: ${projectId}` };
  confirmButton.click();

  while (Date.now() < deadline) {
    if (!findCard()) return { deleted: true, projectId };
    await sleep(pollMs);
  }
  return { error: `Flow project deletion timed out: ${projectId}` };
}
