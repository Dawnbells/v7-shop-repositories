<script setup lang="ts">
/**
 * ImagePicker - 图片资源选择器
 * 
 * 功能：
 * - 文件夹树导航
 * - 图片网格展示
 * - 支持单选/多选模式
 * - 本地上传
 * - 使用 useIframeAuth 进行 API 鉴权
 */

import { useIframeAuth } from '~/composables/useIframeAuth';

// Props
const props = withDefaults(defineProps<{
  visible: boolean;
  multiple?: boolean;  // 是否多选
  maxCount?: number;   // 最大选择数量（多选时生效）
}>(), {
  multiple: false,
  maxCount: 10,
});

// Emits
const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'select', images: ImageItem[]): void;
}>();

// 图片项类型
interface ImageItem {
  id: string;
  name: string;
  relativePath: string;
  absolutionPath?: string;
  mediaType?: string;
  selectedIndex?: number;
}

// 文件夹类型
interface FolderItem {
  id: string;
  compactId: string;
  name: string;
  sensitive?: boolean;
  children?: FolderItem[];
}

// 认证状态
const { isReady, authHeaders, apiBaseUrl, buildImageUrl, token } = useIframeAuth();

// 状态
const treeData = ref<FolderItem[]>([]);
const imageList = ref<ImageItem[]>([]);
const selectedImages = ref<ImageItem[]>([]);
const currentFolderId = ref<string>('10000'); // 默认"所有文件"
const filterText = ref('');
const searchKeyword = ref('');

// 加载状态
const treeLoading = ref(false);
const listLoading = ref(false);
const uploading = ref(false);

// 分页
const pagination = reactive({
  pageNo: 1,
  pageSize: 24,
  total: 0,
});

// 上传相关
const fileInputRef = ref<HTMLInputElement | null>(null);

// 根目录节点
const rootFolders: FolderItem[] = [
  { id: '10000', compactId: '10000', name: '所有文件', sensitive: false },
  { id: '10001', compactId: '10001', name: '根目录', sensitive: false },
];

// 获取文件夹树
async function fetchFolderTree() {
  if (!isReady.value) return;
  
  treeLoading.value = true;
  try {
    const response = await fetch(`${apiBaseUrl.value}/folder/tree`, {
      method: 'GET',
      headers: {
        ...authHeaders.value,
      },
    });
    const result = await response.json();
    if (result.data?.list) {
      treeData.value = [...rootFolders, ...result.data.list];
    } else {
      treeData.value = [...rootFolders];
    }
  } catch (error) {
    console.error('[ImagePicker] 获取文件夹树失败:', error);
    treeData.value = [...rootFolders];
  } finally {
    treeLoading.value = false;
  }
}

// 获取图片列表
async function fetchImageList() {
  if (!isReady.value) return;
  
  listLoading.value = true;
  try {
    const response = await fetch(`${apiBaseUrl.value}/multimedia-file/page`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...authHeaders.value,
      },
      body: JSON.stringify({
        folderId: currentFolderId.value,
        title: searchKeyword.value || undefined,
        pageNo: pagination.pageNo,
        pageSize: pagination.pageSize,
      }),
    });
    const result = await response.json();
    if (result.data) {
      imageList.value = result.data.list || [];
      pagination.total = result.data.total || 0;
    }
  } catch (error) {
    console.error('[ImagePicker] 获取图片列表失败:', error);
    imageList.value = [];
  } finally {
    listLoading.value = false;
  }
}

// 切换文件夹
function handleFolderClick(folder: FolderItem) {
  currentFolderId.value = folder.compactId || folder.id;
  pagination.pageNo = 1;
  fetchImageList();
}

// 过滤文件夹树
function filterNode(value: string, data: FolderItem): boolean {
  if (!value) return true;
  return data.name.toLowerCase().includes(value.toLowerCase());
}

// 切换图片选中状态
function toggleImageSelection(image: ImageItem) {
  const index = selectedImages.value.findIndex(img => img.id === image.id);
  
  if (index > -1) {
    // 取消选中
    selectedImages.value.splice(index, 1);
    // 更新选中序号
    selectedImages.value.forEach((img, i) => {
      img.selectedIndex = i + 1;
    });
    // 更新列表中的状态
    const listItem = imageList.value.find(img => img.id === image.id);
    if (listItem) {
      listItem.selectedIndex = undefined;
    }
  } else {
    // 选中
    if (!props.multiple) {
      // 单选模式，清除之前的选中
      selectedImages.value.forEach(img => {
        const listItem = imageList.value.find(i => i.id === img.id);
        if (listItem) {
          listItem.selectedIndex = undefined;
        }
      });
      selectedImages.value = [];
    } else if (selectedImages.value.length >= props.maxCount) {
      // 多选模式，超出最大数量
      alert(`最多只能选择 ${props.maxCount} 张图片`);
      return;
    }
    
    const newImage = { ...image, selectedIndex: selectedImages.value.length + 1 };
    selectedImages.value.push(newImage);
    
    // 更新列表中的状态
    const listItem = imageList.value.find(img => img.id === image.id);
    if (listItem) {
      listItem.selectedIndex = newImage.selectedIndex;
    }
  }
}

// 清空选中
function clearSelection() {
  selectedImages.value.forEach(img => {
    const listItem = imageList.value.find(i => i.id === img.id);
    if (listItem) {
      listItem.selectedIndex = undefined;
    }
  });
  selectedImages.value = [];
}

// 确认选择
function handleConfirm() {
  if (selectedImages.value.length === 0) {
    alert('请先选择图片');
    return;
  }
  emit('select', selectedImages.value);
  handleClose();
}

// 关闭弹窗
function handleClose() {
  clearSelection();
  emit('close');
}

// 搜索
function handleSearch() {
  pagination.pageNo = 1;
  fetchImageList();
}

// 分页变化
function handlePageChange(page: number) {
  pagination.pageNo = page;
  fetchImageList();
}

// 触发文件选择
function triggerUpload() {
  fileInputRef.value?.click();
}

// 处理文件上传
async function handleFileUpload(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = input.files;
  if (!files || files.length === 0) return;
  
  uploading.value = true;
  
  try {
    const formData = new FormData();
    for (let i = 0; i < files.length; i++) {
      formData.append('files', files[i]);
    }
    
    const folderId = currentFolderId.value === '10000' ? 'root' : currentFolderId.value;
    const response = await fetch(`${apiBaseUrl.value}/multimedia-file/uploadFiles/${folderId}`, {
      method: 'POST',
      headers: {
        ...authHeaders.value,
      },
      body: formData,
    });
    
    const result = await response.json();
    if (result.code === '0' || result.success) {
      // 刷新列表
      await fetchImageList();
      
      // 自动选中新上传的图片
      if (result.data?.list) {
        result.data.list.forEach((item: ImageItem) => {
          const listItem = imageList.value.find(img => img.id === item.id);
          if (listItem) {
            toggleImageSelection(listItem);
          }
        });
      }
    } else {
      alert('上传失败: ' + (result.msg || result.message || '未知错误'));
    }
  } catch (error) {
    console.error('[ImagePicker] 上传失败:', error);
    alert('上传失败，请重试');
  } finally {
    uploading.value = false;
    // 清空 input
    if (input) {
      input.value = '';
    }
  }
}

// 获取图片显示 URL
function getImageUrl(image: ImageItem): string {
  if (image.absolutionPath) {
    return image.absolutionPath;
  }
  if (image.relativePath) {
    return buildImageUrl(image.relativePath);
  }
  return '';
}

// 监听弹窗显示
watch(() => props.visible, (visible) => {
  if (visible && isReady.value) {
    fetchFolderTree();
    fetchImageList();
  }
});

// 监听认证状态就绪
watch(isReady, (ready) => {
  if (ready && props.visible) {
    fetchFolderTree();
    fetchImageList();
  }
});
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="picker-overlay" @click.self="handleClose">
        <div class="image-picker">
          <!-- 头部 -->
          <div class="picker-header">
            <h3 class="picker-title">
              <span class="i-carbon-image"></span>
              选择图片
            </h3>
            <button class="close-btn" @click="handleClose">
              <span class="i-carbon-close"></span>
            </button>
          </div>

          <!-- 未就绪提示 -->
          <div v-if="!isReady" class="not-ready-notice">
            <span class="i-carbon-warning text-2xl text-yellow-500 mb-2"></span>
            <p>等待认证信息...</p>
            <p class="text-sm text-gray-500 mt-1">请确保在 Admin 系统中正确加载</p>
          </div>

          <!-- 主内容区 -->
          <div v-else class="picker-body">
            <!-- 左侧文件夹树 -->
            <div class="folder-tree">
              <input
                v-model="filterText"
                type="text"
                class="tree-filter"
                placeholder="搜索文件夹..."
              />
              <div v-if="treeLoading" class="tree-loading">
                <span class="i-carbon-circle-dash animate-spin"></span>
              </div>
              <div v-else class="tree-list">
                <div
                  v-for="folder in treeData"
                  :key="folder.id"
                  class="tree-node"
                  :class="{ active: currentFolderId === folder.compactId || currentFolderId === folder.id }"
                  @click="handleFolderClick(folder)"
                >
                  <span class="i-carbon-folder" :style="{ color: folder.sensitive ? '#008077' : '#94a3b8' }"></span>
                  <span class="folder-name">{{ folder.name }}</span>
                </div>
              </div>
            </div>

            <!-- 右侧图片列表 -->
            <div class="image-list-container">
              <!-- 工具栏 -->
              <div class="list-toolbar">
                <div class="search-box">
                  <input
                    v-model="searchKeyword"
                    type="text"
                    class="search-input"
                    placeholder="搜索图片..."
                    @keyup.enter="handleSearch"
                  />
                  <button class="search-btn" @click="handleSearch">
                    <span class="i-carbon-search"></span>
                  </button>
                </div>
                <div class="toolbar-actions">
                  <input
                    ref="fileInputRef"
                    type="file"
                    accept="image/png,image/jpeg,image/gif,image/webp"
                    multiple
                    style="display: none;"
                    @change="handleFileUpload"
                  />
                  <button class="upload-btn" :disabled="uploading" @click="triggerUpload">
                    <span v-if="uploading" class="i-carbon-circle-dash animate-spin"></span>
                    <span v-else class="i-carbon-upload"></span>
                    本地上传
                  </button>
                  <button v-if="selectedImages.length > 0" class="clear-btn" @click="clearSelection">
                    清空选中 ({{ selectedImages.length }})
                  </button>
                </div>
              </div>

              <!-- 图片网格 -->
              <div v-if="listLoading" class="list-loading">
                <span class="i-carbon-circle-dash animate-spin text-3xl"></span>
                <p>加载中...</p>
              </div>
              <div v-else-if="imageList.length === 0" class="empty-list">
                <span class="i-carbon-no-image text-4xl text-gray-600 mb-2"></span>
                <p>暂无图片</p>
              </div>
              <div v-else class="image-grid">
                <div
                  v-for="image in imageList"
                  :key="image.id"
                  class="image-item"
                  :class="{ selected: image.selectedIndex }"
                  @click="toggleImageSelection(image)"
                >
                  <div class="image-wrapper">
                    <img :src="getImageUrl(image)" :alt="image.name" loading="lazy" />
                    <div v-if="image.selectedIndex" class="select-badge">
                      {{ image.selectedIndex }}
                    </div>
                  </div>
                  <div class="image-name">{{ image.name }}</div>
                </div>
              </div>

              <!-- 分页 -->
              <div v-if="pagination.total > pagination.pageSize" class="pagination">
                <button
                  class="page-btn"
                  :disabled="pagination.pageNo === 1"
                  @click="handlePageChange(pagination.pageNo - 1)"
                >
                  <span class="i-carbon-chevron-left"></span>
                </button>
                <span class="page-info">
                  {{ pagination.pageNo }} / {{ Math.ceil(pagination.total / pagination.pageSize) }}
                </span>
                <button
                  class="page-btn"
                  :disabled="pagination.pageNo >= Math.ceil(pagination.total / pagination.pageSize)"
                  @click="handlePageChange(pagination.pageNo + 1)"
                >
                  <span class="i-carbon-chevron-right"></span>
                </button>
              </div>
            </div>
          </div>

          <!-- 底部操作栏 -->
          <div class="picker-footer">
            <span class="select-count">
              已选择 {{ selectedImages.length }} 张{{ multiple ? ` / 最多 ${maxCount} 张` : '' }}
            </span>
            <div class="footer-actions">
              <button class="btn btn-secondary" @click="handleClose">取消</button>
              <button class="btn btn-primary" :disabled="selectedImages.length === 0" @click="handleConfirm">
                确认选择
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.picker-overlay {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: 20px;
}

.image-picker {
  width: 100%;
  max-width: 1000px;
  max-height: 85vh;
  background-color: #1e293b;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 头部 */
.picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #334155;
  flex-shrink: 0;
}

.picker-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #f1f5f9;
}

.picker-title span {
  font-size: 22px;
  color: #3b82f6;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: #94a3b8;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.close-btn:hover {
  color: #f1f5f9;
  background-color: #334155;
}

/* 未就绪提示 */
.not-ready-notice {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #94a3b8;
  text-align: center;
}

/* 主内容区 */
.picker-body {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* 左侧文件夹树 */
.folder-tree {
  width: 200px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #334155;
  background-color: #0f172a;
}

.tree-filter {
  margin: 12px;
  padding: 8px 12px;
  font-size: 13px;
  color: #e2e8f0;
  background-color: #1e293b;
  border: 1px solid #334155;
  border-radius: 6px;
  outline: none;
}

.tree-filter:focus {
  border-color: #3b82f6;
}

.tree-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  color: #64748b;
}

.tree-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 12px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  font-size: 13px;
  color: #94a3b8;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.tree-node:hover {
  color: #e2e8f0;
  background-color: #1e293b;
}

.tree-node.active {
  color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.1);
}

.folder-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 右侧图片列表 */
.image-list-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #334155;
  flex-shrink: 0;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 0;
}

.search-input {
  padding: 8px 12px;
  font-size: 13px;
  color: #e2e8f0;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-right: none;
  border-radius: 6px 0 0 6px;
  outline: none;
  width: 200px;
}

.search-input:focus {
  border-color: #3b82f6;
}

.search-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 12px;
  color: #94a3b8;
  background-color: #334155;
  border: 1px solid #334155;
  border-radius: 0 6px 6px 0;
  cursor: pointer;
  transition: all 0.2s;
}

.search-btn:hover {
  color: #e2e8f0;
  background-color: #475569;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.upload-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  font-size: 13px;
  color: white;
  background-color: #3b82f6;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.upload-btn:hover:not(:disabled) {
  background-color: #2563eb;
}

.upload-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.clear-btn {
  padding: 8px 14px;
  font-size: 13px;
  color: #f97316;
  background-color: rgba(249, 115, 22, 0.1);
  border: 1px solid #f97316;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.clear-btn:hover {
  background-color: rgba(249, 115, 22, 0.2);
}

/* 加载状态 */
.list-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: #64748b;
  gap: 12px;
}

/* 空状态 */
.empty-list {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: #64748b;
}

/* 图片网格 */
.image-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
  padding: 16px;
  overflow-y: auto;
  align-content: start;
}

.image-item {
  display: flex;
  flex-direction: column;
  border-radius: 8px;
  overflow: hidden;
  background-color: #0f172a;
  border: 2px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
}

.image-item:hover {
  border-color: #475569;
}

.image-item.selected {
  border-color: #3b82f6;
}

.image-wrapper {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
}

.image-wrapper img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.2s;
}

.image-item:hover .image-wrapper img {
  transform: scale(1.05);
}

.select-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: white;
  background-color: #22c55e;
  border-radius: 50%;
}

.image-name {
  padding: 8px;
  font-size: 12px;
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: center;
}

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 12px;
  border-top: 1px solid #334155;
  flex-shrink: 0;
}

.page-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: #94a3b8;
  background-color: #334155;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  color: #e2e8f0;
  background-color: #475569;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 13px;
  color: #94a3b8;
}

/* 底部操作栏 */
.picker-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-top: 1px solid #334155;
  flex-shrink: 0;
}

.select-count {
  font-size: 13px;
  color: #94a3b8;
}

.footer-actions {
  display: flex;
  gap: 8px;
}

.btn {
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  color: white;
  background-color: #3b82f6;
}

.btn-primary:hover:not(:disabled) {
  background-color: #2563eb;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  color: #e2e8f0;
  background-color: #334155;
}

.btn-secondary:hover {
  background-color: #475569;
}

/* 动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-active .image-picker,
.modal-leave-active .image-picker {
  transition: transform 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .image-picker,
.modal-leave-to .image-picker {
  transform: scale(0.95);
}

/* 滚动条样式 */
.tree-list::-webkit-scrollbar,
.image-grid::-webkit-scrollbar {
  width: 8px;
}

.tree-list::-webkit-scrollbar-track,
.image-grid::-webkit-scrollbar-track {
  background: transparent;
}

.tree-list::-webkit-scrollbar-thumb,
.image-grid::-webkit-scrollbar-thumb {
  background-color: #475569;
  border-radius: 4px;
}

.tree-list::-webkit-scrollbar-thumb:hover,
.image-grid::-webkit-scrollbar-thumb:hover {
  background-color: #64748b;
}

/* 旋转动画 */
@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.animate-spin {
  animation: spin 1s linear infinite;
}
</style>
