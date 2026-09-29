(function () {
  'use strict';

  const VERSION = 26;
  const DOM_TRANSLATE_PORT = 'TURBOFLOW_DOM_V26';
  const previous = window.__turboFlowDomMethod;
  if (previous?.version === VERSION) {
    try { previous.refreshListeners?.(); } catch {}
    return;
  }
  if (previous?.listener) {
    try { chrome.runtime.onMessage.removeListener(previous.listener); } catch {}
  }
  if (previous?.portListener) {
    try { chrome.runtime.onConnect.removeListener(previous.portListener); } catch {}
  }

  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const FILE_INJECT_GAP_MS = 500;
  const UPLOAD_READY_TIMEOUT_MS = 45000;
  const ATTACH_READY_TIMEOUT_MS = 20000;
  const UPLOAD_READY_STABLE_MS = 1200;
  const SEARCH_RETRY_MIN_MS = 1000;
  const SEARCH_RETRY_MAX_MS = 2000;
  const PICKER_TIMEOUT_MS = 8000;
  const PICKER_CLOSE_TIMEOUT_MS = 8000;
  const REQUEST_RESULT_TTL_MS = 2 * 60 * 1000;
  let uiQueueTail = Promise.resolve();
  const requestStates = new Map();

  async function withUiLock(work) {
    const previous = uiQueueTail;
    let release;
    uiQueueTail = new Promise((resolve) => { release = resolve; });
    await previous;
    try {
      return await work();
    } finally {
      release();
    }
  }

  // Flow 已下线 Imagen 4，模型菜单只剩 Nano Banana Pro / 2 / 2 Lite。
  const MODEL_LABELS = {
    GEM_PIX_2: 'Nano Banana Pro',
    NARWHAL: 'Nano Banana 2',
    nano_banana_pro: 'Nano Banana Pro',
    nano_banana2: 'Nano Banana 2',
  };

  const ASPECT_CONFIG = {
    IMAGE_ASPECT_RATIO_LANDSCAPE: { icon: 'crop_16_9', label: '16:9' },
    IMAGE_ASPECT_RATIO_LANDSCAPE_FOUR_THREE: { icon: 'crop_landscape', label: '4:3' },
    IMAGE_ASPECT_RATIO_SQUARE: { icon: 'crop_square', label: '1:1' },
    IMAGE_ASPECT_RATIO_PORTRAIT_THREE_FOUR: { icon: 'crop_portrait', label: '3:4' },
    IMAGE_ASPECT_RATIO_PORTRAIT: { icon: 'crop_9_16', label: '9:16' },
    landscape: { icon: 'crop_16_9', label: '16:9' },
    widescreen: { icon: 'crop_landscape', label: '4:3' },
    square: { icon: 'crop_square', label: '1:1' },
    tallscreen: { icon: 'crop_portrait', label: '3:4' },
    portrait: { icon: 'crop_9_16', label: '9:16' },
  };

  function randomBetween(min, max) {
    const a = Math.min(Number(min) || 0, Number(max) || 0);
    const b = Math.max(Number(min) || 0, Number(max) || 0);
    return a + Math.random() * (b - a);
  }

  async function randomDelay(task, label) {
    const min = Math.max(0, Number(task.delayMin || 0));
    const max = Math.max(0, Number(task.delayMax || min));
    if (max <= 0) return;
    const ms = Math.round(randomBetween(min, max) * 1000);
    console.log(`[TurboFlow DOM] Random delay before ${label}: ${ms}ms`);
    await sleep(ms);
  }

  function xPath(path) {
    try {
      return document.evaluate(path, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
    } catch (error) {
      console.warn('[TurboFlow DOM] XPath error:', path, error);
      return null;
    }
  }

  async function waitFor(fn, timeoutMs, intervalMs = 150) {
    const started = Date.now();
    while (Date.now() - started < timeoutMs) {
      const value = await fn();
      if (value) return value;
      await sleep(intervalMs);
    }
    return null;
  }

  function clickDom(el) {
    if (!el) return false;
    el.scrollIntoView({ block: 'center', inline: 'center' });
    el.click();
    return true;
  }

  function stealthClick(el) {
    if (!el) return false;
    el.scrollIntoView({ block: 'center', inline: 'center' });
    el.click();
    return true;
  }

  function click(el, task = {}) {
    return task.stealthMode ? stealthClick(el) : clickDom(el);
  }

  function pressEscape() {
    const event = new KeyboardEvent('keydown', {
      key: 'Escape',
      code: 'Escape',
      keyCode: 27,
      which: 27,
      bubbles: true,
      cancelable: true,
      composed: true,
    });
    // CDK overlay 的键盘监听挂在 body 上；派发到 document 冒泡不到，弹层关不掉。
    (document.activeElement || document.body).dispatchEvent(event);
  }

  // 按钮文字 = 图标 ligature + 标签（如 "imageImage"），去掉图标部分只留标签。
  function controlLabel(control) {
    const text = control?.textContent || '';
    const iconText = Array.from(control?.querySelectorAll?.('i, mat-icon') || [])
      .map((icon) => icon.textContent || '')
      .join('');
    return (iconText && text.startsWith(iconText) ? text.slice(iconText.length) : text)
      .replace(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/gu, '')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function findAgentModeChip() {
    return document.querySelector('flow-base-prompt-box button.agent-mode-chip, button.agent-mode-chip');
  }

  // Agent 模式下 prompt 会进 Agent 对话（默认还要二次确认），不会直接出生成 Tile。
  async function ensureAgentModeOff() {
    const chip = findAgentModeChip();
    if (!chip || chip.getAttribute('aria-pressed') !== 'true') return false;
    clickDom(chip);
    const off = await waitFor(() => findAgentModeChip()?.getAttribute('aria-pressed') !== 'true', 3000, 100);
    if (!off) throw new Error('Flow Agent mode could not be turned off');
    console.log('[TurboFlow DOM] Agent mode turned off for direct generation');
    return true;
  }

  function setNativeInputValue(input, value) {
    const descriptor = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
    if (descriptor?.set) descriptor.set.call(input, value);
    else input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function findAddReferenceTrigger() {
    return document.querySelector('button[aria-label="Add ingredients to the prompt box"]')
      || xPath("//button[.//i[normalize-space(text())='add_2']]");
  }

  function findPickerDialog() {
    return document.querySelector('.cdk-overlay-pane flow-add-menu-popover-content')
      || document.querySelector('.cdk-overlay-pane .add-menu-popover-container')
      || Array.from(document.querySelectorAll('[role="dialog"], .cdk-overlay-pane'))
        .find((dialog) =>
          dialog.querySelector('input[aria-label="Search assets"], input[placeholder="Search assets"]')
          || Array.from(dialog.querySelectorAll('button')).some((button) =>
            (button.textContent || '').trim() === 'Upload media'
          )
        )
      || null;
  }

  async function waitForPickerDialog() {
    return await waitFor(findPickerDialog, PICKER_TIMEOUT_MS, 150);
  }

  async function openPicker(task) {
    const existing = findPickerDialog();
    if (existing) return existing;
    const trigger = findAddReferenceTrigger();
    if (!trigger) throw new Error('Add ingredients button not found');
    click(trigger, task);
    const dialog = await waitForPickerDialog();
    if (dialog) return dialog;
    const expanded = trigger.getAttribute('aria-expanded');
    throw new Error(`Image picker did not open (aria-expanded=${expanded || 'missing'})`);
  }

  async function waitForPickerClosed() {
    return !!(await waitFor(() => !findPickerDialog(), PICKER_CLOSE_TIMEOUT_MS, 200));
  }

  async function closePicker(task) {
    if (!findPickerDialog()) return true;
    const trigger = findAddReferenceTrigger();
    if (trigger?.getAttribute('aria-expanded') === 'true') {
      click(trigger, task);
    } else {
      pressEscape();
    }
    return await waitForPickerClosed();
  }

  function findPickerImage(fileName) {
    const target = String(fileName || '').trim().toLowerCase();
    const dialog = findPickerDialog();
    if (!dialog) return null;
    const currentOptions = Array.from(dialog.querySelectorAll('button[role="option"], [role="option"]'));
    const current = currentOptions.find((option) => {
      const title = option.querySelector('.asset-title')?.textContent || option.textContent || '';
      return String(title).trim().toLowerCase() === target;
    });
    if (current) return current;
    const images = Array.from(document.querySelectorAll('[data-testid="virtuoso-item-list"] img[alt]'));
    return images.find((img) => String(img.getAttribute('alt') || '').trim().toLowerCase() === target) || null;
  }

  function pickerAssetRoot(result) {
    if (!result) return null;
    return result.matches?.('[role="option"]')
      ? result
      : result.closest?.('[role="option"], button') || result.parentElement || result;
  }

  function pickerAssetIsReady(result) {
    const root = pickerAssetRoot(result);
    if (!root || root.getAttribute?.('aria-disabled') === 'true') return false;
    if (root.matches?.('[aria-busy="true"], [data-state="loading"], [data-state="uploading"]')) return false;
    if (root.querySelector?.([
      '[aria-busy="true"]',
      '[role="progressbar"]',
      'progress',
      'mat-progress-spinner',
      'mat-spinner',
      'mat-progress-bar',
      '.mat-mdc-progress-spinner',
      '.mat-mdc-progress-bar',
      '[data-state="loading"]',
      '[data-state="uploading"]',
      '[class*="upload-progress"]',
      '[class~="uploading"]',
    ].join(','))) return false;

    const statusText = (root.textContent || '').replace(/\s+/g, ' ').trim().toLowerCase();
    if (/\b(uploading|processing|preparing)\b|正在上传|上传中|处理中|准备中/.test(statusText)) return false;

    const images = root.matches?.('img') ? [root] : Array.from(root.querySelectorAll?.('img') || []);
    // Some Flow builds render the asset preview as a CSS background instead
    // of an <img>. In that case the stable, non-busy asset row itself is the
    // completion signal.
    if (!images.length) return true;
    return images.some((image) => image.complete && image.naturalWidth > 0 && !!(image.currentSrc || image.src));
  }

  async function waitForUploadedAssetReady(fileName, task, timeoutMs = UPLOAD_READY_TIMEOUT_MS) {
    let dialog = findPickerDialog();
    let configuredDialog = null;
    let stableSince = 0;
    let stableSignature = '';
    let nextSearchRetryAt = 0;
    const scheduleSearchRetry = () => {
      nextSearchRetryAt = Date.now() + SEARCH_RETRY_MIN_MS
        + Math.random() * (SEARCH_RETRY_MAX_MS - SEARCH_RETRY_MIN_MS);
    };
    const triggerFreshSearch = async (activeDialog) => {
      const input = activeDialog?.querySelector('input[aria-label="Search assets"], input[placeholder="Search assets"], input[type="text"]');
      if (!input) {
        scheduleSearchRetry();
        return false;
      }
      input.focus();
      setNativeInputValue(input, '');
      await sleep(100);
      setNativeInputValue(input, fileName || '');
      scheduleSearchRetry();
      return true;
    };
    const started = Date.now();
    while (Date.now() - started < timeoutMs) {
      dialog = findPickerDialog();
      if (!dialog) {
        // Flow may close the picker immediately after file selection while the
        // upload continues. Reopen it and keep searching for the same uniquely
        // named asset instead of waiting forever on a closed dialog.
        try {
          dialog = await openPicker(task);
        } catch {
          await sleep(500);
          continue;
        }
      }
      if (dialog !== configuredDialog) {
        await triggerFreshSearch(dialog);
        configuredDialog = dialog;
        stableSince = 0;
        stableSignature = '';
      }
      const result = findPickerImage(fileName);
      if (!result && Date.now() >= nextSearchRetryAt) {
        console.log(`[TurboFlow DOM] Asset not indexed yet; retrying search: ${fileName}`);
        await triggerFreshSearch(dialog);
        stableSince = 0;
        stableSignature = '';
        await sleep(150);
        continue;
      }
      if (!pickerAssetIsReady(result)) {
        stableSince = 0;
        stableSignature = '';
        await sleep(250);
        continue;
      }
      const root = pickerAssetRoot(result);
      const image = root.matches?.('img') ? root : root.querySelector?.('img');
      const signature = `${fileName}|${image?.currentSrc || image?.src || ''}`;
      if (signature !== stableSignature) {
        stableSignature = signature;
        stableSince = Date.now();
        await sleep(250);
        continue;
      }
      if (Date.now() - stableSince >= UPLOAD_READY_STABLE_MS) {
        console.log(`[TurboFlow DOM] Upload completed and asset is ready: ${fileName}`);
        return result;
      }
      await sleep(250);
    }
    throw new Error(`Timed out waiting for ${fileName} upload to complete`);
  }

  function closestClickable(el) {
    return el?.closest('button, [role="button"]') || el?.parentElement || el;
  }

  async function clearAttachedReferences(task) {
    const chips = findAttachedReferenceChips();
    if (!chips.length) {
      console.log('[TurboFlow DOM] Reference area already clean');
      return false;
    }
    // 点击 Ingredient chip 本身即移除该参考图。
    for (const chip of chips) {
      click(chip, task);
      await sleep(200);
    }
    const cleared = await waitFor(() => attachedReferenceCount() === 0, 5000, 150);
    if (!cleared) throw new Error('Existing prompt image references could not be cleared');
    return true;
  }

  // 参考图渲染为 flow-image-ingredient-chip > button[aria-label="Ingredient"]（悬浮图标 cancel）。
  // 不能按 close 图标计数：那是有内容时才出现的 "Clear prompt" 按钮，只写了文字也会出现。
  function findAttachedReferenceChips(promptBox = document.querySelector('.base-prompt-box, flow-base-prompt-box')) {
    const chips = Array.from(promptBox?.querySelectorAll('flow-image-ingredient-chip button, button[aria-label="Ingredient"]') || []);
    return Array.from(new Set(chips));
  }

  function attachedReferenceCount() {
    return findAttachedReferenceChips().length;
  }

  async function requireAttachedReferences(expectedCount) {
    const attached = await waitFor(() => attachedReferenceCount() >= expectedCount, 5000, 150);
    if (!attached) {
      throw new Error(`Source image was not added to prompt (expected ${expectedCount}, found ${attachedReferenceCount()})`);
    }
    return true;
  }

  async function injectUploadThroughPicker(image, fileName) {
    const result = await new Promise((resolve) => {
      chrome.runtime.sendMessage({
        action: 'injectFlowUpload',
        dataUrl: image.data,
        fileName,
        mimeType: image.mimeType || 'image/png',
      }, (response) => {
        if (chrome.runtime.lastError) {
          resolve({ success: false, error: chrome.runtime.lastError.message });
          return;
        }
        resolve(response || { success: false, error: 'No upload response' });
      });
    });
    const uploadResult = result.result;
    const uploadConfirmed = uploadResult === 'ok' || uploadResult?.status === 'ok';
    if (!result.success || !uploadConfirmed) {
      throw new Error(result.error || uploadResult?.error || uploadResult || 'Flow upload injection failed');
    }
    console.log(`[TurboFlow DOM] Upload RPC completed: ${uploadResult?.rpcId || 'legacy'} HTTP ${uploadResult?.httpStatus || 200} media=${uploadResult?.mediaId || '?'}`);
    return uploadResult?.mediaId || null;
  }

  // 返回每张源图的 Flow media id，用于在并发时核对 ogiZ0b 请求引用的正是本任务的源图。
  async function uploadAllImages(images, task) {
    const mediaIds = [];
    if (!images.length) return mediaIds;
    // 上传文件名按 assignmentId 唯一生成，素材库里不可能已有同名图；不再预先搜索，
    // 否则每个任务都要白等一次搜索超时。
    for (let i = 0; i < images.length; i++) {
      const image = images[i];
      const name = image.name || `reference_${i + 1}.png`;
      await openPicker(task);
      const mediaId = await injectUploadThroughPicker(image, name);
      if (mediaId) mediaIds.push(mediaId);
      await waitForUploadedAssetReady(name, task);
      await closePicker(task);
      if (i < images.length - 1) await sleep(FILE_INJECT_GAP_MS);
    }

    return mediaIds;
  }

  async function attachOneImage(fileName, task, targetReferenceCount) {
    if (attachedReferenceCount() >= targetReferenceCount) return true;
    await openPicker(task);

    // A newly uploaded asset can appear in search before its upload finishes.
    // Keep retrying the filename search until the thumbnail is fully ready;
    // do not fail early on a temporarily empty search result.
    const readyResult = await waitForUploadedAssetReady(fileName, task, ATTACH_READY_TIMEOUT_MS);

    const row = readyResult.matches?.('[role="option"]') ? readyResult : closestClickable(readyResult);
    if (!row) {
      pressEscape();
      throw new Error(`Search result row for ${fileName} not found`);
    }

    if (task.stealthMode) await sleep(150 + Math.random() * 200);
    click(row, task);
    const addButton = await waitFor(() => {
      const activeDialog = findPickerDialog();
      return Array.from(activeDialog?.querySelectorAll('button') || []).find((button) =>
        (button.textContent || '').replace(/\s+/g, ' ').trim() === 'Add to prompt'
        && !button.disabled
        && button.getAttribute('aria-disabled') !== 'true'
      ) || null;
    }, ATTACH_READY_TIMEOUT_MS, 150);
    if (!addButton) {
      throw new Error(`Add to prompt button did not become available for ${fileName}`);
    }
    click(addButton, task);
    const attached = await waitFor(() => attachedReferenceCount() >= targetReferenceCount, 5000, 150);
    if (!attached) {
      throw new Error(`Flow did not attach ${fileName} to the prompt`);
    }
    if (!await waitForPickerClosed()) {
      pressEscape();
      if (!await waitForPickerClosed()) {
        throw new Error(`Image picker stayed open after Add to prompt for ${fileName}`);
      }
    }
  }

  async function attachAllImages(images, task) {
    const initialReferenceCount = attachedReferenceCount();
    for (let i = 0; i < images.length; i++) {
      const name = images[i].name || `reference_${i + 1}.png`;
      const targetReferenceCount = initialReferenceCount + i + 1;
      let lastError = null;
      for (let attempt = 1; attempt <= 2; attempt++) {
        try {
          await attachOneImage(name, task, targetReferenceCount);
          lastError = null;
          break;
        } catch (error) {
          // Add to prompt may already have committed even if picker cleanup or
          // its final UI event failed. Treat the increased reference count as
          // success so a retry cannot search for and attach the same image again.
          if (attachedReferenceCount() >= targetReferenceCount) {
            if (!await closePicker(task)) {
              throw new Error(`Flow attached ${name}, but the image picker could not be closed`);
            }
            console.log(`[TurboFlow DOM] Attachment already committed; continuing without retry: ${name}`);
            lastError = null;
            break;
          }
          lastError = error;
          console.warn(`[TurboFlow DOM] Attach retry ${attempt}/2 for ${name}: ${error.message}`);
          pressEscape();
          await sleep(2500);
        }
      }
      if (lastError) throw lastError;
    }
  }

  function isVisibleSettingsElement(element) {
    return !!element?.isConnected && element.getClientRects().length > 0
      && !element.closest('[hidden], [aria-hidden="true"]')
      && getComputedStyle(element).visibility !== 'hidden';
  }

  function findSettingsTrigger() {
    const usable = (button) => isVisibleSettingsElement(button)
      && !button.disabled && button.getAttribute('aria-disabled') !== 'true';
    const promptBoxes = Array.from(document.querySelectorAll('flow-base-prompt-box, .base-prompt-box'));
    const promptButtons = Array.from(new Set(promptBoxes.flatMap((box) =>
      Array.from(box.querySelectorAll('button, [role="button"]'))))).filter(usable);
    const explicit = (button) => button.getAttribute('aria-label') === 'Settings trigger';
    const named = promptButtons.find(explicit)
      || Array.from(document.querySelectorAll('button[aria-label="Settings trigger"]')).find(usable);
    if (named) return named;

    // Labels and nested text vary between Flow layouts. Limit icon-based
    // matching to the prompt box so project/card settings cannot be clicked.
    const hasSettingsSummary = (button) => {
      const icons = Array.from(button.querySelectorAll('i, mat-icon'))
        .map((icon) => (icon.textContent || '').trim());
      return icons.some((icon) => Object.values(ASPECT_CONFIG).some((aspect) => aspect.icon === icon))
        && /Nano Banana|Veo|(?:^|\s)x[1-4](?:\s|$)/i.test(controlLabel(button));
    };
    const semantic = promptButtons.find((button) =>
      !button.closest('.cdk-overlay-pane, [role="menu"], [role="dialog"]')
      && (Array.from(button.querySelectorAll('i, mat-icon'))
        .some((icon) => ['tune', 'settings'].includes((icon.textContent || '').trim()))
        || hasSettingsSummary(button)));
    if (semantic) return semantic;

    // Legacy menu triggers may wrap their label in spans rather than direct
    // text nodes. Still require a generation summary, not just any popup.
    return Array.from(document.querySelectorAll('button[aria-haspopup="menu"]'))
      .find((button) => usable(button) && button.querySelector('[data-type="button-overlay"]')
        && hasSettingsSummary(button)) || null;
  }

  async function openSettingsPanel(findSettingsPanel) {
    let settingsPanel = findSettingsPanel();
    let lastTriggerState = 'not-found';
    let clicks = 0;
    for (let attempt = 1; !settingsPanel && attempt <= 3; attempt++) {
      // Poll both: a late panel must be reused, not toggled closed on retry.
      const ready = await waitFor(() => {
        const panel = findSettingsPanel();
        if (panel) return { panel };
        const trigger = findSettingsTrigger();
        return trigger ? { trigger } : null;
      }, 4000, 100);
      settingsPanel = ready?.panel || findSettingsPanel();
      if (settingsPanel) break;
      const trigger = ready?.trigger;
      if (trigger && isVisibleSettingsElement(trigger)) {
        clickDom(trigger);
        clicks++;
        settingsPanel = await waitFor(findSettingsPanel, 4000, 100);
        if (settingsPanel) break;
        lastTriggerState = findSettingsTrigger()?.getAttribute('aria-expanded') || 'missing';
      } else {
        lastTriggerState = 'not-found';
      }
      console.warn(`[TurboFlow DOM] Settings panel open retry ${attempt}/3 (trigger=${lastTriggerState}, clicks=${clicks})`);
      if (attempt < 3) {
        pressEscape();
        await sleep(300);
      }
    }
    if (!settingsPanel) {
      throw new Error(`Flow settings panel did not open after 3 attempts (trigger=${lastTriggerState}, clicks=${clicks})`);
    }
    return settingsPanel;
  }

  async function applySettings(task) {
    const settings = {
      count: '1',
      model: task.model || 'NARWHAL',
      aspectRatio: task.aspectRatio || 'IMAGE_ASPECT_RATIO_LANDSCAPE',
    };

    const aspect = ASPECT_CONFIG[settings.aspectRatio] || ASPECT_CONFIG.IMAGE_ASPECT_RATIO_LANDSCAPE;
    const settingControls = (root) => Array.from(root?.querySelectorAll([
      'button',
      '[role="radio"]',
      '[role="tab"]',
      '[role="menuitem"]',
      '[data-value]',
      '[aria-label]',
      '[title]',
    ].join(',')) || []);
    const clickableSettingControl = (control) => control?.matches?.('button, [role="radio"], [role="tab"], [role="menuitem"]')
      ? control
      : control?.closest?.('button, [role="radio"], [role="tab"], [role="menuitem"]') || control;
    const controlIcons = (control) => Array.from(control?.querySelectorAll?.('i, mat-icon') || [])
      .map((icon) => (icon.textContent || '').trim());
    // 只在叶子选项（radio / button）里找，并按标签或图标精确匹配。按 textContent 包含匹配时，
    // 会先命中包住全部 5 个比例的外层容器（文本含所有比例），点击无效且无选中状态。
    const findAspectControl = (root) => {
      const options = Array.from(root?.querySelectorAll('[role="radio"], [role="tab"], button') || []);
      return options.find((control) => controlLabel(control) === aspect.label)
        || options.find((control) => controlIcons(control).includes(aspect.icon))
        || null;
    };
    const visibleOverlayPanes = () => Array.from(new Set(document.querySelectorAll([
      '.cdk-overlay-pane',
      '[role="menu"][data-state="open"]',
    ].join(','))))
      .filter(isVisibleSettingsElement);
    const findSettingsPanel = () => visibleOverlayPanes().reverse().find((panel) =>
      panel.querySelector('button[aria-label="Select model family"]')
      || Array.from(panel.querySelectorAll('[role="radio"], [role="tab"], button'))
        .some((control) => Object.values(ASPECT_CONFIG).some((candidate) =>
          controlLabel(control) === candidate.label || controlIcons(control).includes(candidate.icon)))
    ) || null;
    const settingsPanel = await openSettingsPanel(findSettingsPanel);

    const isSelected = (control) => control?.getAttribute('aria-checked') === 'true'
      || control?.getAttribute('aria-selected') === 'true'
      || control?.getAttribute('data-state') === 'active';
    // Image / Video 切换按钮的 textContent 是 "imageImage"，按去掉图标后的标签精确匹配。
    const findImageTab = () => settingControls(findSettingsPanel() || settingsPanel)
      .map(clickableSettingControl)
      .find((button) => controlLabel(button).toLowerCase() === 'image');
    const imageTab = findImageTab();
    if (imageTab && !isSelected(imageTab)) {
      clickDom(imageTab);
      if (!await waitFor(() => isSelected(findImageTab()), 3000, 100)) {
        throw new Error('Flow Image generation tab was not selected');
      }
    }

    const findAspectTab = () => findAspectControl(findSettingsPanel() || settingsPanel);
    const aspectTab = findAspectTab();
    if (!aspectTab) {
      throw new Error(`Flow aspect ratio option not found: ${aspect.label}`);
    }

    // Flow remembers the previous task's setting. Re-click the best matching
    // aspect ratio for every source image so a stale selection cannot leak
    // into the next translation.
    if (!isSelected(aspectTab)) clickDom(aspectTab);
    // 必须看到明确的选中状态；不暴露状态的元素不算选中，避免误报成功。
    const aspectSelected = await waitFor(() => isSelected(findAspectTab()), 3000, 100);
    if (!aspectSelected) {
      throw new Error(`Flow aspect ratio was not selected: ${aspect.label}`);
    }
    console.log(`[TurboFlow DOM] Aspect ratio reselected for this task: ${aspect.label}`);

    const findCountTab = () => Array.from((findSettingsPanel() || settingsPanel)
      ?.querySelectorAll('[role="radio"], [role="tab"], button') || [])
      .find((button) => controlLabel(button) === 'x1');
    const countTab = findCountTab();
    if (countTab && !isSelected(countTab)) {
      clickDom(countTab);
      if (!await waitFor(() => isSelected(findCountTab()), 3000, 100)) {
        throw new Error('Flow output count x1 was not selected');
      }
    }

    const modelLabel = MODEL_LABELS[settings.model] || MODEL_LABELS.NARWHAL;
    const activeSettingsPanel = findSettingsPanel() || settingsPanel;
    const modelTrigger = activeSettingsPanel.querySelector('button[aria-label="Select model family"]')
      || xPath("//div[@role='menu' and @data-state='open']//button[@aria-haspopup='menu' and .//div[@data-type='button-overlay']]");
    if (modelTrigger) {
      clickDom(modelTrigger);
      await sleep(500);
      // 精确匹配：includes 会让 "Nano Banana 2" 命中 "Nano Banana 2 Lite"。
      const modelOption = Array.from(document.querySelectorAll('[role="menuitem"]'))
        .find((option) => controlLabel(option) === modelLabel)
        || xPath(`//div[@role='menuitem']//button[.//span[normalize-space(text())='${modelLabel}']]`);
      if (modelOption) {
        clickDom(modelOption);
        await sleep(400);
      } else {
        pressEscape();
        throw new Error(`Flow model option not found: ${modelLabel}`);
      }
    }

    if (findSettingsPanel()) {
      const closeTrigger = findSettingsTrigger();
      if (closeTrigger) clickDom(closeTrigger);
      else pressEscape();
      if (!await waitFor(() => !findSettingsPanel(), 3000, 100)) {
        pressEscape();
        await waitFor(() => !findSettingsPanel(), 2000, 100);
      }
    }
    // 面板关闭后，触发按钮上显示的是当前生效的比例图标（如 crop_9_16），再核对一次。
    const appliedTrigger = findSettingsTrigger();
    // Icon-only settings buttons have no ratio summary; selection was already
    // verified on the panel's radio above.
    const hasAspectSummary = controlIcons(appliedTrigger)
      .some((icon) => Object.values(ASPECT_CONFIG).some((candidate) => candidate.icon === icon));
    if (hasAspectSummary && !controlIcons(appliedTrigger).includes(aspect.icon)) {
      throw new Error(`Flow aspect ratio did not apply: expected ${aspect.label}, trigger shows ${controlIcons(appliedTrigger).join(' ')}`);
    }
    return true;
  }

  function getEditor() {
    return document.querySelector('.ProseMirror[contenteditable="true"], [data-slate-editor="true"]');
  }

  async function fastInjectPrompt(prompt) {
    const editor = getEditor();
    if (!editor) return false;
    clickDom(editor);
    editor.focus();
    const selection = window.getSelection();
    const range = document.createRange();
    range.selectNodeContents(editor);
    selection.removeAllRanges();
    selection.addRange(range);
    let inserted = false;
    try {
      inserted = document.execCommand('insertText', false, prompt);
    } catch {}
    if (!inserted) {
      editor.textContent = prompt;
      editor.dispatchEvent(new InputEvent('input', {
        bubbles: true,
        composed: true,
        inputType: 'insertText',
        data: prompt,
      }));
    }
    return Boolean(await waitFor(() => {
      const text = editor.textContent.trim();
      return text === prompt || text.includes(prompt.substring(0, 20));
    }, 2000, 50));
  }

  async function injectPrompt(prompt) {
    if (!getEditor()) throw new Error('Flow prompt editor not found');
    if (!await fastInjectPrompt(prompt)) throw new Error('Prompt injection failed');
  }

  const START_GENERATION_SELECTOR = 'button[aria-label="Start generation"]';

  // Start generation 只认 isTrusted 点击，el.click() 会被静默忽略。交给 background：
  // 先挂 ogiZ0b 监听，再用 chrome.debugger 真实点击，以请求真的发出为提交成功（必要时重点）。
  // 返回的 generateToken 用于 background 读取本次生成的响应。
  async function submitPrompt(_task, referenceMediaIds = []) {
    const button = await waitFor(() =>
      document.querySelector(`${START_GENERATION_SELECTOR}:not(:disabled):not([aria-disabled="true"])`)
    , 10000, 200);
    if (!button) throw new Error('Flow Start generation button did not become enabled');
    const response = await chrome.runtime.sendMessage({ type: 'FLOW_TRUSTED_SUBMIT', selector: START_GENERATION_SELECTOR, referenceMediaIds });
    if (!response?.ok || !response.generateToken) throw new Error(response?.error || 'Flow generation submit failed');
    return response.generateToken;
  }

  async function runDomTranslate(task) {
    const imageName = task.fileName || 'reference_1.png';
    const images = [{
      data: task.imageBase64,
      name: imageName,
      mimeType: task.mimeType || 'image/png',
    }];

    const generateToken = await withUiLock(async () => {
      await ensureAgentModeOff();
      await applySettings(task);
      await clearAttachedReferences(task);
      await randomDelay(task, 'reference upload');
      const referenceMediaIds = await uploadAllImages(images, task);
      await attachAllImages(images, task);
      await requireAttachedReferences(images.length);
      // Once Add to prompt has attached the source image, continue directly:
      // write the translation prompt, verify the reference, and submit.
      await injectPrompt(task.prompt || '');
      await requireAttachedReferences(images.length);
      const gate = await chrome.runtime.sendMessage({ type: 'FLOW_DOM_BEFORE_SUBMIT' });
      if (!gate?.ok) throw new Error(gate?.error || 'New Flow submissions are paused');
      return await submitPrompt(task, referenceMediaIds);
    });

    try {
      await chrome.runtime.sendMessage({
        type: 'FLOW_DOM_TRANSLATION_SUBMITTED',
        assignmentId: task.assignmentId || null,
      });
    } catch {}

    // 页面职责到此为止：生成结果由 background 从 ogiZ0b 响应解析并下载，
    // 不再从 Tile 网格猜测（网格重渲染会让旧 Tile 看起来像新 Tile）。
    return { generateToken };
  }

  const errorMessage = (error) => error?.message || String(error || 'Unknown Flow page automation error');

  const listener = (msg, _sender, sendResponse) => {
    if (msg.type !== 'RUN_DOM_TRANSLATE_V26') return false;
    runDomTranslate(msg.task || {})
      .then((result) => sendResponse({ ok: true, ...result }))
      .catch((error) => sendResponse({ ok: false, error: errorMessage(error) }));
    return true;
  };

  const portListener = (port) => {
    if (port.name !== DOM_TRANSLATE_PORT) return;
    let started = false;
    let subscribedRequestId = null;
    const safePost = (message) => {
      try {
        port.postMessage(message);
        return true;
      } catch {
        return false;
      }
    };
    port.onDisconnect.addListener(() => {
      if (!subscribedRequestId) return;
      requestStates.get(subscribedRequestId)?.ports.delete(port);
    });
    port.onMessage.addListener((msg) => {
      if (started || msg?.type !== 'RUN_DOM_TRANSLATE_V26') return;
      started = true;
      const requestId = msg.requestId || null;
      if (!requestId) {
        safePost({ type: 'FLOW_DOM_TRANSLATION_RESULT', requestId, ok: false, error: 'Missing Flow requestId' });
        return;
      }
      subscribedRequestId = requestId;
      const existing = requestStates.get(requestId);
      if (existing) {
        existing.ports.add(port);
        safePost({ type: 'FLOW_DOM_TRANSLATION_ACCEPTED', requestId, ok: true, resumed: true });
        if (existing.status === 'complete' && existing.response) safePost(existing.response);
        return;
      }

      const state = { status: 'running', ports: new Set([port]), response: null };
      requestStates.set(requestId, state);
      safePost({ type: 'FLOW_DOM_TRANSLATION_ACCEPTED', requestId, ok: true });
      const complete = (response) => {
        state.status = 'complete';
        state.response = response;
        for (const subscriber of state.ports) {
          try { subscriber.postMessage(response); } catch {}
        }
        setTimeout(() => {
          if (requestStates.get(requestId) === state) requestStates.delete(requestId);
        }, REQUEST_RESULT_TTL_MS);
      };
      runDomTranslate(msg.task || {})
        .then((result) => {
          complete({ type: 'FLOW_DOM_TRANSLATION_RESULT', requestId, ok: true, ...result });
        })
        .catch((error) => {
          complete({
            type: 'FLOW_DOM_TRANSLATION_RESULT',
            requestId,
            ok: false,
            error: errorMessage(error),
          });
        });
    });
  };

  const refreshListeners = () => {
    try { chrome.runtime.onMessage.removeListener(listener); } catch {}
    try { chrome.runtime.onConnect.removeListener(portListener); } catch {}
    chrome.runtime.onMessage.addListener(listener);
    chrome.runtime.onConnect.addListener(portListener);
  };

  refreshListeners();
  window.__turboFlowDomMethod = { version: VERSION, listener, portListener, refreshListeners };
})();
