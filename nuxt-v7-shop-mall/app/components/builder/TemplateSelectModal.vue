<script setup lang="ts">
/**
 * 模板选择弹窗组件
 * 用于从服务端获取主题模板列表，选择后应用到当前编辑器
 */

// Props
const props = defineProps<{
  visible: boolean;
}>();

// Emits
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

// 获取共享类型显示信息
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
    console.error("[TemplateSelect] 搜索模板失败:", error);
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

// 解析 JSON 字段（可能是字符串或对象）
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
      console.warn("[TemplateSelect] JSON 解析失败");
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
    // 获取模板详情
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

    // 解析 JSON 字段
    const themeConfig = parseJsonField(templateData.themeConfig);
    const variableSchema = parseJsonField(templateData.variableSchema, []);
    const siteConfig = parseJsonField(templateData.siteConfig, {});
    const variableValues = parseJsonField(templateData.variableValues, {});

    if (!themeConfig) {
      throw new Error("模板主题配置为空");
    }

    // 触发应用事件
    emit("apply", {
      themeConfig,
      variableSchema,
      siteConfig,
      variableValues,
    });
  } catch (error: any) {
    console.error("[TemplateSelect] 应用模板失败:", error);
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

// 监听弹窗显示，自动加载模板列表
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

          <!-- 提示信息 -->
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
            <!-- 加载状态 -->
            <div v-if="isLoading" class="loading-state">
              <span class="i-carbon-circle-dash animate-spin text-2xl"></span>
              <span>加载中...</span>
            </div>

            <!-- 空状态 -->
            <div v-else-if="templateList.length === 0" class="empty-state">
              <span class="i-carbon-template text-4xl text-gray-600 mb-3"></span>
              <p class="text-gray-400">暂无可用模板</p>
              <p class="text-sm text-gray-600">请先在管理后台创建主题模板</p>
            </div>

            <!-- 模板列表 -->
            <div v-else class="template-list">
              <div
                v-for="template in templateList"
                :key="template.id"
                class="template-item"
                :class="{ selected: selectedTemplateId === template.id }"
                @click="selectTemplate(template.id)"
              >
                <div class="template-info">
                  <div class="template-name">
                    {{ template.name }}
                    <span
                      class="share-tag"
                      :class="getShareTypeInfo(template.shareType).class"
                    >
                      {{ template.shareTypeName || getShareTypeInfo(template.shareType).label }}
                    </span>
                  </div>
                  <div v-if="template.description" class="template-desc">
                    {{ template.description }}
                  </div>
                  <div class="template-meta">
                    <span v-if="template.ownerName" class="meta-item">
                      <span class="i-carbon-user"></span>
                      {{ template.ownerName }}
                    </span>
                  </div>
                </div>
                <div class="template-check">
                  <span
                    v-if="selectedTemplateId === template.id"
                    class="i-carbon-checkmark-filled"
                  ></span>
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
/* 弹窗遮罩 */
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

/* 弹窗主体 */
.template-select-modal {
  background: #1e293b;
  border-radius: 12px;
  width: 560px;
  max-width: 90vw;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  border: 1px solid #334155;
}

/* 头部 */
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

/* 搜索栏 */
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

/* 警告提示 */
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

/* 错误提示 */
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

/* 内容区 */
.modal-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px 20px;
  min-height: 200px;
  max-height: 400px;
}

/* 加载状态 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px;
  color: #94a3b8;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  text-align: center;
}

/* 模板列表 */
.template-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.template-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.template-item:hover {
  border-color: #475569;
  background: #1e293b;
}

.template-item.selected {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
}

.template-info {
  flex: 1;
  min-width: 0;
}

.template-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  color: #f1f5f9;
  margin-bottom: 4px;
}

.share-tag {
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 4px;
}

.tag-danger {
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
}

.tag-warning {
  background: rgba(245, 158, 11, 0.2);
  color: #f59e0b;
}

.tag-info {
  background: rgba(100, 116, 139, 0.2);
  color: #94a3b8;
}

.template-desc {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-meta {
  display: flex;
  gap: 12px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #64748b;
}

.template-check {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #3b82f6;
  font-size: 20px;
}

/* 底部 */
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid #334155;
}

/* 按钮 */
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

/* 动画 */
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

/* 动画类 */
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
