import { FLOW_TAB_URL_PATTERNS, FLOW_HOME_URL, buildFlowHomeUrl,
  isFlowHomeUrl, isFlowTabToClose } from './flow-sites.js';
import { openFlowHome, waitForFlowHomeReady, ensureFlowProjectOpen, clearFlowStorage,
  clearTokenCache, clearProjectIdCache, listAllUserProjects, deleteFlowProject } from './flow-api.js';

const DEFAULT_RESTART_API = { openFlowHome, waitForFlowHomeReady, ensureFlowProjectOpen, clearFlowStorage,
  clearTokenCache, clearProjectIdCache };

async function listFlowTabs(browser) {
  const tabs = await browser.tabs.query({ url: FLOW_TAB_URL_PATTERNS });
  return tabs.filter(tab => isFlowTabToClose(tab.pendingUrl || tab.url));
}

async function closeTabs(ids, browser) {
  for (const id of ids) {
    // Disappeared tabs are already closed; other remove failures are fatal.
    try { await browser.tabs.get(id); } catch { continue; }
    await browser.tabs.remove(id);
  }
}

/**
 * 换轮前在每个 Flow 标签页里清空 flow.google.com（及旧版 labs.google）源下的
 * localStorage 与 sessionStorage。localStorage 按源共享，sessionStorage 按标签页隔离，
 * 所以必须在关标签之前逐个标签清。单个标签清不了（已被 Chrome 丢弃、正在导航）不中断换轮，
 * 返回成功清空的标签数，调用方据此决定是否需要兜底。
 */
async function clearFlowTabsStorage(tabs, api, log) {
  let cleared = 0;
  for (const tab of tabs) {
    try {
      await api.clearFlowStorage(tab.id);
      cleared++;
    } catch (error) {
      log('warn', `could not clear storage in Flow tab ${tab.id} (${tab.pendingUrl || tab.url}): ${error.message}`);
    }
  }
  return cleared;
}

/**
 * 风控换轮：清 storage → 关 Flow 标签 → 重开首页并等可用 → 新建项目并等编辑器就绪。
 * 收尾（等现有任务结束）由 FlowRecoveryController 在调用前完成。
 */
export async function restartFlowProject(target, phase, browser = chrome, api = DEFAULT_RESTART_API,
  log = () => {}) {
  await phase('clearing');
  const tabs = await listFlowTabs(browser);
  const cleared = await clearFlowTabsStorage(tabs, api, log);
  log('info', `cleared local/session storage in ${cleared}/${tabs.length} Flow tab(s)`);
  await phase('closing');
  await closeTabs(tabs.map(tab => tab.id), browser);
  api.clearTokenCache();
  api.clearProjectIdCache();
  await phase('opening');
  const url = buildFlowHomeUrl(target?.homeUrl || FLOW_HOME_URL);
  if (!url) throw new Error('Invalid Flow recovery home');
  const openHome = async () => {
    const { tabId } = await api.openFlowHome({ url, windowId: target?.windowId });
    // 首页文档加载完不等于应用可用：等到 "New project" 按钮可点击才进入建项目阶段。
    await api.waitForFlowHomeReady(tabId);
    return tabId;
  };
  let tabId = await openHome();
  if (!cleared) {
    // 关标签前没有任何可清的 Flow 页（标签早已关闭或被丢弃）：localStorage 仍留在源里。
    // 用刚打开的首页清一次、关掉再重开，保证新项目一定建立在空 storage 上。
    log('warn', 'no Flow tab could be cleared before closing; clearing storage in the fresh home tab and reopening it');
    await phase('clearing');
    await api.clearFlowStorage(tabId);
    await phase('closing');
    await closeTabs([tabId], browser);
    await phase('opening');
    tabId = await openHome();
  }
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
