(function () {
  'use strict';

  const VERSION = 20;
  const DOM_TRANSLATE_PORT = 'TURBOFLOW_DOM_V20';
  const previous = window.__turboFlowDomMethod;
  if (previous?.version === VERSION) return;
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
  const SEARCH_TIMEOUT_MS = 15000;
  const PICKER_CLOSE_TIMEOUT_MS = 8000;
  const RESULT_TIMEOUT_MS = 180000;
  const RESULT_SCAN_MS = 1000;
  const GENERATION_TILE_TIMEOUT_MS = 30000;
  let uiQueueTail = Promise.resolve();
  const claimedTiles = new WeakSet();
  const claimedTileKeys = new Map();

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

  const MODEL_LABELS = {
    GEM_PIX_2: 'Nano Banana Pro',
    NARWHAL: 'Nano Banana 2',
    IMAGEN_3_5: 'Imagen 4',
    nano_banana_pro: 'Nano Banana Pro',
    nano_banana2: 'Nano Banana 2',
    imagen4: 'Imagen 4',
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
    document.dispatchEvent(event);
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

  async function waitForPickerImage(fileName) {
    return await waitFor(() => findPickerImage(fileName), SEARCH_TIMEOUT_MS, 300);
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

  async function searchPicker(dialog, fileName, task) {
    const input = dialog.querySelector('input[aria-label="Search assets"], input[placeholder="Search assets"], input[type="text"]');
    if (!input) throw new Error('Image picker search input not found');
    if (task.stealthMode) await sleep(100 + Math.random() * 300);
    input.focus();
    setNativeInputValue(input, fileName || '');
    return await waitForPickerImage(fileName);
  }

  async function checkImagesInLibrary(fileNames, task) {
    const found = new Set();
    let dialog;
    try {
      dialog = await openPicker(task);
    } catch {
      return found;
    }

    for (const name of fileNames) {
      const input = dialog.querySelector('input[type="text"]');
      if (!input) break;
      setNativeInputValue(input, '');
      await sleep(120);
      const result = await searchPicker(dialog, name, task);
      if (result) found.add(name);
      await sleep(200);
    }

    await closePicker(task);
    await sleep(300);
    return found;
  }

  async function clearAttachedReferences(task) {
    const promptBox = document.querySelector('.base-prompt-box, flow-base-prompt-box');
    const clearButtons = findAttachedReferenceClearButtons(promptBox);
    if (!clearButtons.length) {
      console.log('[TurboFlow DOM] Reference area already clean');
      return false;
    }
    for (const button of clearButtons) {
      click(button, task);
      await sleep(200);
    }
    const cleared = await waitFor(() => attachedReferenceCount() === 0, 5000, 150);
    if (!cleared) throw new Error('Existing prompt image references could not be cleared');
    return true;
  }

  function findAttachedReferenceClearButtons(promptBox = document.querySelector('.base-prompt-box, flow-base-prompt-box')) {
    const buttons = Array.from(promptBox?.querySelectorAll('button') || []);
    return buttons.filter((button) => {
      if (button.matches('[aria-label="Add ingredients to the prompt box"], [aria-label="Settings trigger"], [aria-label="Start generation"]')) return false;
      const icon = button.querySelector('i.google-symbols, i, mat-icon');
      return icon?.textContent?.trim() === 'close';
    });
  }

  function attachedReferenceCount() {
    return findAttachedReferenceClearButtons().length;
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
    console.log(`[TurboFlow DOM] Upload RPC completed: ${uploadResult?.rpcId || 'legacy'} HTTP ${uploadResult?.httpStatus || 200}`);
  }

  async function uploadAllImages(images, task) {
    if (!images.length) return true;
    const names = images.map((image, index) => image.name || `reference_${index + 1}.png`);
    const alreadyInLibrary = await checkImagesInLibrary(names, task);
    const missingCount = images.length - alreadyInLibrary.size;
    let injected = 0;

    for (let i = 0; i < images.length; i++) {
      const image = images[i];
      const name = names[i];
      if (alreadyInLibrary.has(name)) continue;
      await openPicker(task);
      await injectUploadThroughPicker(image, name);
      injected++;
      await waitForUploadedAssetReady(name, task);
      await closePicker(task);
      if (injected < missingCount) await sleep(FILE_INJECT_GAP_MS);
    }

    return true;
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
    const settingControlText = (control) => [
      control?.textContent,
      control?.getAttribute?.('aria-label'),
      control?.getAttribute?.('title'),
      control?.getAttribute?.('data-value'),
      control?.getAttribute?.('value'),
    ].filter(Boolean).join(' ').replace(/\s+/g, '').toLowerCase();
    const clickableSettingControl = (control) => control?.matches?.('button, [role="radio"], [role="tab"], [role="menuitem"]')
      ? control
      : control?.closest?.('button, [role="radio"], [role="tab"], [role="menuitem"]') || control;
    const findAspectControl = (root) => {
      const icon = aspect.icon.toLowerCase();
      const label = aspect.label.replace(/\s+/g, '').toLowerCase();
      const match = settingControls(root).find((control) => {
        const text = settingControlText(control);
        return text.includes(icon) || text.includes(label);
      });
      return clickableSettingControl(match);
    };
    const visibleOverlayPanes = () => Array.from(new Set(document.querySelectorAll([
      '.cdk-overlay-pane',
      '[role="menu"][data-state="open"]',
    ].join(','))))
      .filter((panel) => panel.isConnected && panel.getClientRects().length > 0);
    const findSettingsPanel = () => visibleOverlayPanes().reverse().find((panel) =>
      panel.querySelector('button[aria-label="Select model family"]')
      || findAspectControl(panel)
    ) || null;
    const findSettingsTrigger = () => Array.from(document.querySelectorAll('button[aria-label="Settings trigger"]'))
      .find((button) => button.isConnected
        && button.getClientRects().length > 0
        && !button.disabled
        && button.getAttribute('aria-disabled') !== 'true')
      || xPath("//button[@aria-haspopup='menu' and .//div[@data-type='button-overlay'] and text()[normalize-space() != '']]");

    // A previous task can leave this panel open or halfway through its close
    // animation. Reuse an already-open panel; otherwise reacquire and retry the
    // trigger so a stale toggle cannot turn the panel off and cause a timeout.
    let settingsPanel = findSettingsPanel();
    let lastTriggerState = 'missing';
    for (let attempt = 1; !settingsPanel && attempt <= 3; attempt++) {
      const trigger = await waitFor(findSettingsTrigger, 4000, 100);
      if (!trigger) break;
      lastTriggerState = trigger.getAttribute('aria-expanded') || 'unknown';
      clickDom(trigger);
      settingsPanel = await waitFor(findSettingsPanel, 4000, 100);
      if (settingsPanel) break;

      console.warn(`[TurboFlow DOM] Settings panel open retry ${attempt}/3 (aria-expanded=${lastTriggerState})`);
      pressEscape();
      await sleep(300);
    }
    if (!settingsPanel) {
      throw new Error(`Flow settings panel did not open after 3 attempts (aria-expanded=${lastTriggerState})`);
    }

    const imageTab = settingControls(settingsPanel)
      .map(clickableSettingControl)
      .find((button) => /(^|\s)Image(\s|$)/i.test((button?.textContent || '').trim()));
    if (imageTab && imageTab.getAttribute('aria-checked') !== 'true'
        && imageTab.getAttribute('aria-selected') !== 'true'
        && imageTab.getAttribute('data-state') !== 'active') {
      clickDom(imageTab);
      await sleep(400);
    }

    const findAspectTab = () => findAspectControl(findSettingsPanel() || settingsPanel);
    const aspectTab = findAspectTab();
    if (!aspectTab) {
      throw new Error(`Flow aspect ratio option not found: ${aspect.label}`);
    }

    // Flow remembers the previous task's setting. Re-click the best matching
    // aspect ratio for every source image so a stale selection cannot leak
    // into the next translation.
    clickDom(aspectTab);
    const aspectSelected = await waitFor(() => {
      const current = findAspectTab();
      if (!current) return false;
      const exposesSelectionState = current.hasAttribute('aria-checked')
        || current.hasAttribute('aria-selected')
        || current.hasAttribute('data-state');
      if (!exposesSelectionState) return true;
      return current.getAttribute('aria-checked') === 'true'
        || current.getAttribute('aria-selected') === 'true'
        || current.getAttribute('data-state') === 'active';
    }, 3000, 100);
    if (!aspectSelected) {
      throw new Error(`Flow aspect ratio was not selected: ${aspect.label}`);
    }
    console.log(`[TurboFlow DOM] Aspect ratio reselected for this task: ${aspect.label}`);

    const countTab = settingControls(findSettingsPanel() || settingsPanel)
      .map(clickableSettingControl)
      .find((button) => (button?.textContent || '').trim() === 'x1');
    if (countTab && countTab.getAttribute('aria-checked') !== 'true'
        && countTab.getAttribute('aria-selected') !== 'true'
        && countTab.getAttribute('data-state') !== 'active') {
      clickDom(countTab);
      await sleep(300);
    }

    const modelLabel = MODEL_LABELS[settings.model] || MODEL_LABELS.NARWHAL;
    const activeSettingsPanel = findSettingsPanel() || settingsPanel;
    const modelTrigger = activeSettingsPanel.querySelector('button[aria-label="Select model family"]')
      || xPath("//div[@role='menu' and @data-state='open']//button[@aria-haspopup='menu' and .//div[@data-type='button-overlay']]");
    if (modelTrigger) {
      clickDom(modelTrigger);
      await sleep(500);
      const modelOption = Array.from(document.querySelectorAll('[role="menuitem"]'))
        .find((option) => (option.textContent || '').includes(modelLabel))
        || xPath(`//div[@role='menuitem']//button[.//span[contains(normalize-space(text()),'${modelLabel}')]]`);
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

  async function submitPrompt(task) {
    const button = await waitFor(() =>
      document.querySelector('button[aria-label="Start generation"]:not(:disabled):not([aria-disabled="true"])')
      || xPath("(//button[.//i[normalize-space()='arrow_forward'] and not(@disabled) and not(@aria-disabled='true')])[last()]")
    , 10000, 200);
    if (!button) return false;
    click(button, task);
    return true;
  }

  function resultImages(scope = document) {
    return Array.from(scope.querySelectorAll(
      'img[alt="Tile displaying a user\'s image"][data-media-id], '
      + 'flow-grid-tile-container img[data-media-id], '
      + 'img[src*="media.getMediaUrlRedirect"], '
      + 'img[src*="flow-content.google/image/"], '
      + 'img[src*="flow.google.com/asb/"]',
    ));
  }

  function resultImageKey(img) {
    return img?.getAttribute('data-media-id') || img?.currentSrc || img?.src || '';
  }

  function snapshotImageIds() {
    const ids = new Set();
    resultImages().forEach((img) => {
      const id = resultImageKey(img);
      if (id) ids.add(id);
    });
    return ids;
  }

  function generationTiles() {
    const currentTiles = Array.from(document.querySelectorAll('flow-grid-tile-container'));
    if (currentTiles.length) return currentTiles;
    return Array.from(document.querySelectorAll('[data-tile-id]'));
  }

  function snapshotGenerationTiles() {
    const tiles = generationTiles();
    return {
      elements: new Set(tiles),
      stableKeys: new Set(tiles.map(tileStableKey).filter(Boolean)),
    };
  }

  function tileStableKey(tile) {
    if (!tile) return '';
    for (const name of ['data-tile-id', 'data-generation-id', 'data-workflow-id', 'data-id', 'id']) {
      const value = tile.getAttribute?.(name);
      if (value) return `${name}:${value}`;
    }
    return '';
  }

  function tileAnchor(tile) {
    return tile?.closest('.virtual-item-container, .tile-row') || tile?.parentElement || null;
  }

  function tilePositionInAnchor(tile, anchor) {
    if (!tile || !anchor) return 0;
    const tiles = Array.from(anchor.querySelectorAll('flow-grid-tile-container, [data-tile-id]'));
    const position = tiles.indexOf(tile);
    return position >= 0 ? position : 0;
  }

  function tileLabel(tile) {
    return tile?.getAttribute('aria-label') || tile?.getAttribute('title') || '';
  }

  function createTileClaim(tile, preSubmitImageIds, assignmentId) {
    if (!tile || claimedTiles.has(tile)) return null;
    const tileKey = tileStableKey(tile);
    if (tileKey && claimedTileKeys.has(tileKey)) return null;
    claimedTiles.add(tile);
    if (assignmentId) tile.setAttribute('data-turboflow-assignment-id', assignmentId);
    if (tileKey) claimedTileKeys.set(tileKey, assignmentId || tileKey);
    const anchor = tileAnchor(tile);
    return {
      tile,
      tileKey,
      anchor,
      anchorKey: tileStableKey(anchor),
      position: tilePositionInAnchor(tile, anchor),
      label: tileLabel(tile),
      assignmentId: assignmentId || '',
      preSubmitImageIds,
    };
  }

  function findClaimReplacement(claim) {
    const tiles = generationTiles();
    const eligible = (tile) => {
      const owner = tile.getAttribute('data-turboflow-assignment-id');
      return !owner || !claim.assignmentId || owner === claim.assignmentId;
    };
    if (claim.assignmentId) {
      const marked = tiles.filter((tile) =>
        tile.getAttribute('data-turboflow-assignment-id') === claim.assignmentId);
      if (marked.length === 1) return marked[0];
    }
    if (claim.tileKey) {
      const keyed = tiles.filter((tile) => eligible(tile) && tileStableKey(tile) === claim.tileKey);
      if (keyed.length === 1) return keyed[0];
    }
    if (claim.anchorKey && claim.label) {
      const anchored = tiles.filter((tile) =>
        eligible(tile)
        && tileStableKey(tileAnchor(tile)) === claim.anchorKey
        && tileLabel(tile) === claim.label);
      if (anchored.length === 1) return anchored[0];
    }
    if (claim.label) {
      const labelled = tiles.filter((tile) => eligible(tile) && tileLabel(tile) === claim.label);
      if (labelled.length === 1) return labelled[0];
    }
    return null;
  }

  function findTileError(tile) {
    if (!tile) return null;
    const icons = Array.from(tile.querySelectorAll('i')).map((icon) => icon.textContent.trim());
    if (!icons.includes('warning')) return null;
    if (Array.from(tile.querySelectorAll('a[href]')).some((a) => {
      const href = a.getAttribute('href') || '';
      return href.includes('/faq') || href.includes('/policies') || href.includes('policy');
    })) return 'Prompt flagged by content policy';
    if (icons.includes('refresh')) return 'Generation failed - Flow encountered an error';
    return 'Generation error detected';
  }

  async function imageToDataUrl(src) {
    return await new Promise((resolve) => {
      const timeout = setTimeout(() => resolve(null), 30000);
      const img = new Image();
      img.crossOrigin = 'anonymous';
      img.onload = () => {
        try {
          const canvas = document.createElement('canvas');
          canvas.width = img.naturalWidth;
          canvas.height = img.naturalHeight;
          canvas.getContext('2d').drawImage(img, 0, 0);
          clearTimeout(timeout);
          resolve(canvas.toDataURL('image/png'));
        } catch {
          clearTimeout(timeout);
          resolve(null);
        }
      };
      img.onerror = () => {
        clearTimeout(timeout);
        resolve(null);
      };
      img.src = src;
    });
  }

  async function claimGenerationTile(preSubmitTiles, preSubmitImageIds, assignmentId) {
    const started = Date.now();
    while (Date.now() - started < GENERATION_TILE_TIMEOUT_MS) {
      const newTiles = generationTiles().filter((tile) => {
        if (claimedTiles.has(tile) || preSubmitTiles.elements.has(tile)) return false;
        const key = tileStableKey(tile);
        return !key || (!preSubmitTiles.stableKeys.has(key) && !claimedTileKeys.has(key));
      });
      if (newTiles.length > 1) {
        throw new Error(`Flow created ${newTiles.length} unclaimed generation tiles; refusing ambiguous result mapping`);
      }
      const newTile = newTiles[0];
      if (newTile) {
        const error = findTileError(newTile);
        if (error) throw new Error(error);
        const claim = createTileClaim(newTile, preSubmitImageIds, assignmentId);
        if (claim) return claim;
      }
      await sleep(200);
    }
    throw new Error('Flow did not create a generation tile after submit');
  }

  async function waitForGeneratedImage(claim) {
    const started = Date.now();
    while (Date.now() - started < RESULT_TIMEOUT_MS) {
      if (!claim.tile?.isConnected) {
        const replacement = findClaimReplacement(claim);
        if (replacement) {
          const replacementOwner = replacement.getAttribute?.('data-turboflow-assignment-id');
          if (replacementOwner && claim.assignmentId && replacementOwner !== claim.assignmentId) {
            throw new Error(`Replacement Flow tile belongs to ${replacementOwner}, not ${claim.assignmentId}`);
          }
          claim.tile = replacement;
          claimedTiles.add(replacement);
          if (claim.assignmentId) {
            replacement.setAttribute('data-turboflow-assignment-id', claim.assignmentId);
          }
        }
        if (!claim.tile?.isConnected) {
          await sleep(RESULT_SCAN_MS);
          continue;
        }
      }
      const claimedAssignment = claim.tile.getAttribute?.('data-turboflow-assignment-id');
      if (claimedAssignment && claim.assignmentId && claimedAssignment !== claim.assignmentId) {
        throw new Error(`Claimed Flow tile changed owner from ${claim.assignmentId} to ${claimedAssignment}`);
      }
      const error = findTileError(claim.tile);
      if (error) throw new Error(error);
      for (const img of resultImages(claim.tile)) {
        const id = resultImageKey(img);
        if (!id || claim.preSubmitImageIds.has(id)) continue;
        if (img?.src) {
          const resultDataUrl = await imageToDataUrl(img.src);
          return { resultUrl: img.src, resultDataUrl };
        }
      }
      await sleep(RESULT_SCAN_MS);
    }
    throw new Error('Timed out waiting for generated image tile');
  }

  async function runDomTranslate(task) {
    const imageName = task.fileName || 'reference_1.png';
    const images = [{
      data: task.imageBase64,
      name: imageName,
      mimeType: task.mimeType || 'image/png',
    }];

    const claim = await withUiLock(async () => {
      await applySettings(task);
      await clearAttachedReferences(task);
      await randomDelay(task, 'reference upload');
      await uploadAllImages(images, task);
      await attachAllImages(images, task);
      await requireAttachedReferences(images.length);
      // Once Add to prompt has attached the source image, continue directly:
      // write the translation prompt, verify the reference, and submit.
      await injectPrompt(task.prompt || '');
      await requireAttachedReferences(images.length);
      const preSubmitTiles = snapshotGenerationTiles();
      const preSubmitImageIds = snapshotImageIds();
      if (!await submitPrompt(task)) throw new Error('Submit failed');
      return await claimGenerationTile(preSubmitTiles, preSubmitImageIds, task.assignmentId || '');
    });

    // The serial UI section is complete and this request now owns one exact
    // Flow tile. Background may submit the next task while this tile generates.
    try {
      await chrome.runtime.sendMessage({
        type: 'FLOW_DOM_TRANSLATION_SUBMITTED',
        assignmentId: task.assignmentId || null,
      });
    } catch {}

    return await waitForGeneratedImage(claim);
  }

  const errorMessage = (error) => error?.message || String(error || 'Unknown Flow page automation error');

  const listener = (msg, _sender, sendResponse) => {
    if (msg.type !== 'RUN_DOM_TRANSLATE_V20') return false;
    runDomTranslate(msg.task || {})
      .then((result) => sendResponse({ ok: true, ...result }))
      .catch((error) => sendResponse({ ok: false, error: errorMessage(error) }));
    return true;
  };

  const portListener = (port) => {
    if (port.name !== DOM_TRANSLATE_PORT) return;
    let started = false;
    port.onMessage.addListener((msg) => {
      if (started || msg?.type !== 'RUN_DOM_TRANSLATE_V20') return;
      started = true;
      const requestId = msg.requestId || null;
      try {
        port.postMessage({ type: 'FLOW_DOM_TRANSLATION_ACCEPTED', requestId, ok: true });
      } catch {
        return;
      }
      runDomTranslate(msg.task || {})
        .then((result) => {
          try {
            port.postMessage({ type: 'FLOW_DOM_TRANSLATION_RESULT', requestId, ok: true, ...result });
          } catch {}
        })
        .catch((error) => {
          try {
            port.postMessage({
              type: 'FLOW_DOM_TRANSLATION_RESULT',
              requestId,
              ok: false,
              error: errorMessage(error),
            });
          } catch {}
        });
    });
  };

  chrome.runtime.onMessage.addListener(listener);
  chrome.runtime.onConnect.addListener(portListener);
  window.__turboFlowDomMethod = { version: VERSION, listener, portListener };
})();
