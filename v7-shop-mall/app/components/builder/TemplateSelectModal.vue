<script setup lang="ts">
/**
 * TemplateSelectModal - 模板选择弹窗组件
 *
 * 用于从服务端获取主题模板列表，选择后应用到当前编辑器
 */

import { useIframeAuth } from "@/composables/base/useIframeAuth";

const props = defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  (e: "close"): void;
  (e: "apply", data: {
    themeConfig: any;
    variableSchema: any[];
    siteConfig: any;
    variableValues: any;
  }): void;
}>();

// 使用 iframe 认证信息
const { apiBaseUrl, authHeaders, isReady } = useIframeAuth();

// 状态
const searchKeyword = ref("");
const templateList = ref<any[]>([]);
const selectedTemplateId = ref<string | null>(null);
const isLoading = ref(false);
const isApplying = ref(false);
const errorMessage = ref<string | null>(null);

// 共享类型标签样式
const shareTypeStyles: Record<string, { label: string; class: string }> = {
  COMPANY: { label: "公司", class: "tag-danger" },
  DEPARTMENT: { label: "部门", class: "tag-warning" },
  PRIVATE: { label: "私有", class: "tag-info" },
};

function getShareTypeInfo(shareType: string | undefined) {
  return shareTypeStyles[shareType || "PRIVATE"] || shareTypeStyles.PRIVATE;
}

// 搜索模板列表
async function searchTemplates(keyword: string = "") {
  if (!isReady.value || !apiBaseUrl.value) {
    errorMessage.value = "未获取到认证信息，请刷新页面重试";
    return;
  }

  isLoading.value = true;
  errorMessage.value = null;

  try {
    const url = `${apiBaseUrl.value}/theme-templates/remoteQuery?query=${encodeURIComponent(keyword)}`;
    const response = await fetch(url, {
      method: "GET",
      headers: authHeaders.value,
    });

    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`);
    }

    const result = await response.json();
    templateList.value = result.data?.list || result.data || [];
  } catch (error: any) {
    errorMessage.value = error.message || "获取模板列表失败";
    templateList.value = [];
  } finally {
    isLoading.value = false;
  }
}

// 防抖搜索
let searchTimeout: ReturnType<typeof setTimeout> | null = null;
function handleSearchInput() {
  if (searchTimeout) {
    clearTimeout(searchTimeout);
  }
  searchTimeout = setTimeout(() => {
    searchTemplates(searchKeyword.value);
  }, 300);
}

// 选择模板
function selectTemplate(templateId: string) {
  selectedTemplateId.value = templateId;
}

// 解析 JSON 字段
function parseJsonField(value: any, defaultValue: any = null): any {
  if (value === null || value === undefined) {
    return defaultValue;
  }
  if (typeof value === "object") {
    return value;
  }
  if (typeof value === "string") {
    try {
      return JSON.parse(value);
    } catch {
      return defaultValue;
    }
  }
  return defaultValue;
}

// 确认应用模板
async function handleApply() {
  if (!selectedTemplateId.value) {
    errorMessage.value = "请先选择一个模板";
    return;
  }

  if (!isReady.value || !apiBaseUrl.value) {
    errorMessage.value = "未获取到认证信息，请刷新页面重试";
    return;
  }

  isApplying.value = true;
  errorMessage.value = null;

  try {
    const url = `${apiBaseUrl.value}/theme-templates/${selectedTemplateId.value}`;
    const response = await fetch(url, {
      method: "GET",
      headers: authHeaders.value,
    });

    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`);
    }

    const result = await response.json();
    const templateData = result.data;

    if (!templateData) {
      throw new Error("模板数据为空");
    }

    const themeConfig = parseJsonField(templateData.themeConfig);
    const variableSchema = parseJsonField(templateData.variableSchema, []);
    const siteConfig = parseJsonField(templateData.siteConfig, {});
    const variableValues = parseJsonField(templateData.variableValues, {});

    if (!themeConfig) {
      throw new Error("模板主题配置为空");
    }

    emit("apply", { themeConfig, variableSchema, siteConfig, variableValues });
  } catch (error: any) {
    errorMessage.value = error.message || "应用模板失败";
  } finally {
    isApplying.value = false;
  }
}

// 关闭弹窗
function handleClose() {
  searchKeyword.value = "";
  selectedTemplateId.value = null;
  errorMessage.value = null;
  emit("close");
}

// 监听弹窗显示
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      searchTemplates("");
    }
  }
);
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="handleClose">
        <div class="template-select-modal">
          <!-- 头部 -->
          <div class="modal-header">
            <h2 class="modal-title">
              <span class="i-carbon-template"></span>
              应用主题模板
            </h2>
            <button class="close-btn" @click="handleClose">
              <span class="i-carbon-close"></span>
            </button>
          </div>

          <!-- 搜索栏 -->
          <div class="search-bar">
            <div class="search-input-wrapper">
              <span class="i-carbon-search search-icon"></span>
              <input
                v-model="searchKeyword"
                type="text"
                class="search-input"
                placeholder="搜索模板名称..."
                @input="handleSearchInput"
              />
              <button
                v-if="searchKeyword"
                class="clear-btn"
                @click="searchKeyword = ''; searchTemplates('')"
              >
                <span class="i-carbon-close"></span>
              </button>
            </div>
          </div>

          <!-- 警告提示 -->
          <div class="warning-tip">
            <span class="i-carbon-warning"></span>
            应用模板后将覆盖当前编辑器的所有配置，此操作不可撤销
          </div>

          <!-- 错误提示 -->
          <div v-if="errorMessage" class="error-message">
            <span class="i-carbon-warning-alt"></span>
            {{ errorMessage }}
          </div>

          <!-- 内容区 -->
          <div class="modal-content">
            <div v-if="isLoading" class="loading-state">
              <span class="i-carbon-circle-dash animate-spin text-2xl"></span>
              <span>加载中...</span>
            </div>

            <div v-else-if="templateList.length === 0" class="empty-state">
              <span class="i-carbon-template text-4xl text-gray-600 mb-3"></span>
              <p class="text-gray-400">暂无可用模板</p>
            </div>

            <div v-else class="card-grid">
              <div
                v-for="template in templateList"
                :key="template.id"
                class="template-card"
                :class="{ selected: selectedTemplateId === template.id }"
                @click="selectTemplate(template.id)"
              >
                <div class="card-cover">
                  <img
                    v-if="template.coverImage"
                    :src="template.coverImage"
                    :alt="template.name"
                  />
                  <div v-else class="no-cover">
                    <span class="i-carbon-image"></span>
                    <span>暂无封面</span>
                  </div>
                  <span
                    class="share-badge"
                    :class="getShareTypeInfo(template.shareType).class"
                  >
                    {{ template.shareTypeName || getShareTypeInfo(template.shareType).label }}
                  </span>
                  <div
                    v-if="selectedTemplateId === template.id"
                    class="card-check"
                  >
                    <span class="i-carbon-checkmark-filled"></span>
                  </div>
                </div>
                <div class="card-content">
                  <div class="card-title" :title="template.name">
                    {{ template.name }}
                  </div>
                  <div class="card-desc" :title="template.description">
                    {{ template.description || '暂无描述' }}
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 底部 -->
          <div class="modal-footer">
            <button class="btn btn-secondary" @click="handleClose">
              取消
            </button>
            <button
              class="btn btn-primary"
              :disabled="!selectedTemplateId || isApplying"
              @click="handleApply"
            >
              <span v-if="isApplying" class="i-carbon-circle-dash animate-spin mr-1"></span>
              {{ isApplying ? "应用中..." : "确定应用" }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.template-select-modal {
  background: #1e293b;
  border-radius: 12px;
  width: 720px;
  max-width: 90vw;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  border: 1px solid #334155;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #334155;
}

.modal-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #f1f5f9;
  margin: 0;
}

.modal-title span {
  font-size: 18px;
  color: #3b82f6;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
}

.close-btn:hover {
  background: #334155;
  color: #f1f5f9;
}

.search-bar {
  padding: 12px 20px;
  border-bottom: 1px solid #334155;
}

.search-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 12px;
  color: #64748b;
  font-size: 16px;
}

.search-input {
  width: 100%;
  padding: 10px 36px;
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
  color: #f1f5f9;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.search-input:focus {
  border-color: #3b82f6;
}

.search-input::placeholder {
  color: #64748b;
}

.clear-btn {
  position: absolute;
  right: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  border-radius: 4px;
}

.clear-btn:hover {
  color: #f1f5f9;
  background: #334155;
}

.warning-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
  font-size: 13px;
  border-bottom: 1px solid #334155;
}

.error-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  font-size: 13px;
  border-bottom: 1px solid #334155;
}

.modal-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px 20px;
  min-height: 200px;
  max-height: 400px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px;
  color: #94a3b8;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  text-align: center;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.template-card {
  position: relative;
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
}

.template-card:hover {
  border-color: #475569;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.template-card.selected {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.05);
}

.card-cover {
  position: relative;
  height: 120px;
  background: #1e293b;
  overflow: hidden;
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card-cover .no-cover {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #475569;
  gap: 6px;
}

.card-cover .no-cover span:first-child {
  font-size: 28px;
}

.share-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 2px 8px;
  font-size: 10px;
  font-weight: 500;
  border-radius: 4px;
}

.tag-danger {
  background: rgba(239, 68, 68, 0.9);
  color: white;
}

.tag-warning {
  background: rgba(245, 158, 11, 0.9);
  color: white;
}

.tag-info {
  background: rgba(100, 116, 139, 0.9);
  color: white;
}

.card-check {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #3b82f6;
  border-radius: 50%;
  color: white;
  font-size: 14px;
}

.card-content {
  padding: 12px;
}

.card-title {
  font-size: 14px;
  font-weight: 500;
  color: #f1f5f9;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  font-size: 12px;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid #334155;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.btn-secondary {
  background: #334155;
  color: #f1f5f9;
}

.btn-secondary:hover {
  background: #475569;
}

.btn-primary {
  background: #3b82f6;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #2563eb;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.mr-1 {
  margin-right: 4px;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-active .template-select-modal,
.modal-leave-active .template-select-modal {
  transition: transform 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .template-select-modal,
.modal-leave-to .template-select-modal {
  transform: scale(0.95);
}

.animate-spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
