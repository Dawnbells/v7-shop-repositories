import { FLOW_TAB_URL_PATTERNS, FLOW_HOME_URL, buildFlowHomeUrl,
  isFlowHomeUrl, isFlowTabToClose } from './flow-sites.js';
import { openFlowHome, ensureFlowProjectOpen, clearTokenCache, clearProjectIdCache,
  listAllUserProjects, deleteFlowProject } from './flow-api.js';

export async function restartFlowProject(target, phase, browser = chrome,
  api = { openFlowHome, ensureFlowProjectOpen, clearTokenCache, clearProjectIdCache }) {
  await phase('closing');
  const tabs = await browser.tabs.query({ url: FLOW_TAB_URL_PATTERNS });
  const ids = tabs.filter(tab => isFlowTabToClose(tab.pendingUrl || tab.url)).map(tab => tab.id);
  for (const id of ids) {
    // Disappeared tabs are already closed; other remove failures are fatal.
    try { await browser.tabs.get(id); } catch { continue; }
    await browser.tabs.remove(id);
  }
  api.clearTokenCache();
  api.clearProjectIdCache();
  await phase('opening');
  const url = buildFlowHomeUrl(target?.homeUrl || FLOW_HOME_URL);
  if (!url) throw new Error('Invalid Flow recovery home');
  const { tabId } = await api.openFlowHome({ url, windowId: target?.windowId });
  await phase('creating');
  const projectId = await api.ensureFlowProjectOpen(tabId);
  if (!projectId) throw new Error('Flow did not create a new project');
  return { tabId, projectId };
}

export async function getProjectCleanupTarget(windowId, browser = chrome) {
  const [tab] = await browser.tabs.query({ active: true,
    ...(Number.isInteger(windowId) ? { windowId } : { lastFocusedWindow: true }) });
  if (!tab || tab.status !== 'complete' || !isFlowHomeUrl(tab.pendingUrl || tab.url)) {
    return { allowed: false, reason: '请先打开 Google Flow 项目首页' };
  }
  return { allowed: true, tabId: tab.id, windowId: tab.windowId, homeUrl: buildFlowHomeUrl(tab.url) };
}

export async function deleteProjectsAtHome(target, progress, browser = chrome,
  api = { listAllUserProjects, deleteFlowProject }) {
  const validate = async () => {
    const tab = await browser.tabs.get(target.tabId);
    if (!tab.active || tab.status !== 'complete' || !isFlowHomeUrl(tab.pendingUrl || tab.url)
      || buildFlowHomeUrl(tab.url) !== target.homeUrl) throw new Error('Flow 首页或账号已改变，删除已中止');
  };
  const attempted = new Set();
  let deleted = 0, failed = 0;
  // Re-enumerate after each batch, including cards revealed by previous deletions.
  for (let page = 0; page < 1000; page++) {
    await validate();
    const projects = await api.listAllUserProjects(target.tabId);
    const next = projects.filter(project => !attempted.has(project.projectId));
    if (!next.length) {
      const result = { deleted, failed, remaining: projects.length };
      progress({ phase: 'done', ...result });
      return result;
    }
    for (const project of next) {
      await validate();
      attempted.add(project.projectId);
      try {
        await api.deleteFlowProject(target.tabId, project.projectId, target.homeUrl);
        deleted++;
      } catch (error) {
        await validate(); // Navigation/closed tabs abort, not just count as failed.
        failed++;
      }
      progress({ phase: 'progress', deleted, failed, total: attempted.size + next.filter(p => !attempted.has(p.projectId)).length });
    }
  }
  throw new Error('Flow project list exceeded the cleanup limit');
}
