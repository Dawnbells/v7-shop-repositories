<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="`站点配置(${subDomainFullName}-${subDomainCountryName})`"
    width="1100px"
    @close="close"
  >
    <div class="product-manager">
      <!-- 顶部操作区 -->
      <div class="top-action-bar">
        <el-select
          ref="spuSelectRef"
          v-model="selectedSpuId"
          clearable
          filterable
          :loading="searchLoading"
          placeholder="搜索SPU名称或编码"
          remote
          :remote-method="remoteSearchSpu"
          style="width: 300px"
          @focus="handleSelectFocus"
        >
          <el-option v-for="item in spuOptions" :key="item.id" :label="item.name" :value="item.id">
            <span style="float: left">{{ item.name }}</span>
            <span style="float: right; font-size: 12px; color: var(--el-text-color-secondary)">
              {{ item.code }}
            </span>
          </el-option>
        </el-select>
        <el-button
          :disabled="!selectedSpuId"
          :loading="bindLoading"
          type="primary"
          @click="handleBindSpu"
        >
          绑定
        </el-button>
      </div>

      <!-- 左右布局主体 -->
      <div class="main-content">
        <!-- 左侧：已绑定SPU列表 -->
        <div class="left-panel">
          <div class="panel-header">
            <span class="panel-title">
              <el-icon class="title-icon"><Connection /></el-icon>
              已绑定SPU
            </span>
            <el-badge
              :hidden="boundSpuList.length === 0"
              :value="boundSpuList.length"
              type="primary"
            >
              <el-button :loading="refreshLoading" link type="primary" @click="refreshBoundSpus">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </el-badge>
          </div>
          <div class="filter-input">
            <el-input
              v-model="boundFilterKeyword"
              clearable
              placeholder="搜索已绑定SPU..."
              :prefix-icon="Search"
              size="small"
              @clear="handleFilterBoundSpus"
              @keyup.enter="handleFilterBoundSpus"
            />
          </div>
          <div v-loading="listLoading" class="spu-list-wrapper">
            <el-scrollbar v-if="boundSpuList.length > 0" class="spu-scrollbar">
              <div class="spu-list">
                <div
                  v-for="spu in boundSpuList"
                  :key="spu.id"
                  class="spu-item"
                  :class="{ active: activeSpuTab === String(spu.id) }"
                  @click="activeSpuTab = String(spu.id)"
                >
                  <div class="spu-info">
                    <div class="spu-name-row">
                      <el-tooltip
                        v-if="!spu.supportCurrentCountry"
                        :content="`未设置${subDomainCountryName || '该国家'}落地页`"
                        placement="top"
                      >
                        <span class="spu-name unsupported" :title="spu.name">{{ spu.name }}</span>
                      </el-tooltip>
                      <span v-else class="spu-name" :title="spu.name">{{ spu.name }}</span>
                    </div>
                    <div class="spu-meta">
                      <span class="spu-id copyable" @click.stop="copyText(spu.id)">
                        ID: {{ spu.id }}
                      </span>
                      <span
                        v-if="spu.code"
                        class="spu-code copyable"
                        @click.stop="copyText(spu.code)"
                      >
                        {{ spu.code }}
                      </span>
                    </div>
                  </div>
                  <el-button
                    class="delete-btn"
                    :icon="Close"
                    link
                    size="small"
                    type="danger"
                    @click.stop="handleUnbindSpu(spu)"
                  />
                </div>
              </div>
            </el-scrollbar>
            <el-empty v-else-if="!listLoading" :image-size="80" description="暂无绑定的SPU" />
          </div>
        </div>

        <!-- 右侧：详情内容区 -->
        <div class="right-panel">
          <el-empty v-if="!activeSpuTab" description="请选择左侧SPU查看详情" />
          <div v-else v-loading="detailLoading" class="spu-detail">
            <el-tabs v-model="activeDetailTab" class="detail-tabs">
              <!-- 落地页SPU配置 -->
              <el-tab-pane name="landing">
                <template #label>
                  <span class="tab-label">
                    <el-icon><Document /></el-icon>
                    落地页配置
                  </span>
                </template>
                <el-scrollbar class="tab-content-scrollbar">
                  <div class="tab-content">
                    <div class="landing-page-list">
                      <div class="landing-page-item">
                        <div class="landing-label">真实落地页</div>
                        <div class="landing-value">
                          <template v-if="spuDetail.realLandingPageSpu">
                            <el-tooltip
                              v-if="!spuDetail.realLandingPageSpu.supportCurrentCountry"
                              :content="`未设置${subDomainCountryName || '该国家'}落地页`"
                              placement="top"
                            >
                              <span
                                class="spu-link unsupported clickable"
                                @click="handlePreviewLanding('LAND')"
                              >
                                {{ spuDetail.realLandingPageSpu.code }}-{{
                                  spuDetail.realLandingPageSpu.name
                                }}
                              </span>
                            </el-tooltip>
                            <span
                              v-else
                              class="spu-link clickable"
                              @click="handlePreviewLanding('LAND')"
                            >
                              {{ spuDetail.realLandingPageSpu.code }}-{{
                                spuDetail.realLandingPageSpu.name
                              }}
                            </span>
                            <el-button
                              :icon="Link"
                              link
                              type="success"
                              @click="handleCopyAdLink(spuDetail.realLandingPageSpu.id)"
                            >
                              广告
                            </el-button>
                          </template>
                          <span
                            v-else
                            class="spu-link closed clickable"
                            @click="handlePreviewLanding('LAND')"
                          >
                            店铺已关闭
                          </span>
                          <el-button
                            :icon="Brush"
                            link
                            type="primary"
                            @click="handleEditLandingTheme('LAND')"
                          >
                            主题
                          </el-button>
                          <el-tooltip
                            :content="
                              spuDetail.realLandingPageProtocol
                                ? '已绑定：' + spuDetail.realLandingPageProtocol.protocolName
                                : '未绑定协议'
                            "
                            placement="top"
                          >
                            <el-button
                              :icon="Notebook"
                              link
                              :type="spuDetail.realLandingPageProtocol ? 'success' : 'info'"
                              @click="handleBindProtocol('LAND')"
                            >
                              协议
                            </el-button>
                          </el-tooltip>
                          <el-button
                            :icon="CopyDocument"
                            link
                            type="warning"
                            @click="handleApplyTemplate('LAND')"
                          >
                            模板
                          </el-button>
                          <el-button
                            :icon="Monitor"
                            link
                            type="primary"
                            @click="handleEditLandingSite('LAND')"
                          >
                            站点
                          </el-button>
                        </div>
                      </div>
                      <div class="landing-page-item">
                        <div class="landing-label">风险用户落地页</div>
                        <div class="landing-value">
                          <template v-if="spuDetail.riskUserLandingPageSpu">
                            <el-tooltip
                              v-if="!spuDetail.riskUserLandingPageSpu.supportCurrentCountry"
                              :content="`未设置${subDomainCountryName || '该国家'}落地页`"
                              placement="top"
                            >
                              <span
                                class="spu-link unsupported clickable"
                                @click="handlePreviewLanding('CLOAK')"
                              >
                                {{ spuDetail.riskUserLandingPageSpu.code }}-{{
                                  spuDetail.riskUserLandingPageSpu.name
                                }}
                              </span>
                            </el-tooltip>
                            <span
                              v-else
                              class="spu-link clickable"
                              @click="handlePreviewLanding('CLOAK')"
                            >
                              {{ spuDetail.riskUserLandingPageSpu.code }}-{{
                                spuDetail.riskUserLandingPageSpu.name
                              }}
                            </span>
                          </template>
                          <span
                            v-else
                            class="spu-link closed clickable"
                            @click="handlePreviewLanding('CLOAK')"
                          >
                            店铺已关闭
                          </span>
                          <el-button
                            :icon="Setting"
                            link
                            type="primary"
                            @click="handleConfigLandingPage('CLOAK')"
                          >
                            配置
                          </el-button>
                          <el-button
                            :icon="Brush"
                            link
                            type="primary"
                            @click="handleEditLandingTheme('CLOAK')"
                          >
                            主题
                          </el-button>
                          <el-tooltip
                            :content="
                              spuDetail.riskUserLandingPageProtocol
                                ? '已绑定：' + spuDetail.riskUserLandingPageProtocol.protocolName
                                : '未绑定协议'
                            "
                            placement="top"
                          >
                            <el-button
                              :icon="Notebook"
                              link
                              :type="spuDetail.riskUserLandingPageProtocol ? 'success' : 'info'"
                              @click="handleBindProtocol('CLOAK')"
                            >
                              协议
                            </el-button>
                          </el-tooltip>
                          <el-button
                            :icon="CopyDocument"
                            link
                            type="warning"
                            @click="handleApplyTemplate('CLOAK')"
                          >
                            模板
                          </el-button>
                          <el-button
                            :icon="Monitor"
                            link
                            type="primary"
                            @click="handleEditLandingSite('CLOAK')"
                          >
                            站点
                          </el-button>
                        </div>
                      </div>
                      <div class="landing-page-item">
                        <div class="landing-label">黑名单落地页</div>
                        <div class="landing-value">
                          <template v-if="spuDetail.blacklistLandingPageSpu">
                            <el-tooltip
                              v-if="!spuDetail.blacklistLandingPageSpu.supportCurrentCountry"
                              :content="`未设置${subDomainCountryName || '该国家'}落地页`"
                              placement="top"
                            >
                              <span
                                class="spu-link unsupported clickable"
                                @click="handlePreviewLanding('BLACKLISTED')"
                              >
                                {{ spuDetail.blacklistLandingPageSpu.code }}-{{
                                  spuDetail.blacklistLandingPageSpu.name
                                }}
                              </span>
                            </el-tooltip>
                            <span
                              v-else
                              class="spu-link clickable"
                              @click="handlePreviewLanding('BLACKLISTED')"
                            >
                              {{ spuDetail.blacklistLandingPageSpu.code }}-{{
                                spuDetail.blacklistLandingPageSpu.name
                              }}
                            </span>
                          </template>
                          <span
                            v-else
                            class="spu-link closed clickable"
                            @click="handlePreviewLanding('BLACKLISTED')"
                          >
                            店铺已关闭
                          </span>
                          <el-button
                            :icon="Setting"
                            link
                            type="primary"
                            disabled
                            @click="handleConfigLandingPage('BLACKLISTED')"
                          >
                            配置
                          </el-button>
                          <el-button
                            :icon="Brush"
                            link
                            type="primary"
                            @click="handleEditLandingTheme('BLACKLISTED')"
                          >
                            主题
                          </el-button>
                          <el-tooltip
                            :content="
                              spuDetail.blacklistLandingPageProtocol
                                ? '已绑定：' + spuDetail.blacklistLandingPageProtocol.protocolName
                                : '未绑定协议'
                            "
                            placement="top"
                          >
                            <el-button
                              :icon="Notebook"
                              link
                              :type="spuDetail.blacklistLandingPageProtocol ? 'success' : 'info'"
                              @click="handleBindProtocol('BLACKLISTED')"
                            >
                              协议
                            </el-button>
                          </el-tooltip>
                          <el-button
                            :icon="CopyDocument"
                            link
                            type="warning"
                            @click="handleApplyTemplate('BLACKLISTED')"
                          >
                            模板
                          </el-button>
                          <el-button
                            :icon="Monitor"
                            link
                            type="primary"
                            @click="handleEditLandingSite('BLACKLISTED')"
                          >
                            站点
                          </el-button>
                        </div>
                      </div>
                    </div>
                  </div>
                </el-scrollbar>
              </el-tab-pane>

              <!-- 像素配置 -->
              <el-tab-pane name="pixel">
                <template #label>
                  <span class="tab-label">
                    <el-icon><Aim /></el-icon>
                    像素配置
                    <el-badge
                      v-if="spuDetail.pixels && spuDetail.pixels.length > 0"
                      :value="spuDetail.pixels.length"
                      class="pixel-badge"
                    />
                  </span>
                </template>
                <el-scrollbar class="tab-content-scrollbar">
                  <div class="tab-content">
                    <div class="pixel-header">
                      <el-button :icon="Plus" type="primary" @click="handleAddPixel">
                        添加像素
                      </el-button>
                    </div>
                    <div class="pixel-list">
                      <template v-if="spuDetail.pixels && spuDetail.pixels.length > 0">
                        <div v-for="pixel in spuDetail.pixels" :key="pixel.id" class="pixel-item">
                          <div class="pixel-main">
                            <div class="pixel-info">
                              <span class="pixel-name">{{ pixel.name }}</span>
                              <span class="pixel-id">{{ pixel.pixelId }}</span>
                              <el-tag v-if="pixel.platform" effect="plain" size="small" type="info">
                                {{ pixel.platform }}
                              </el-tag>
                            </div>
                            <div v-if="pixel.conversionEvent" class="pixel-event">
                              <el-icon class="event-icon"><Flag /></el-icon>
                              <span class="event-label">转化事件:</span>
                              <el-tag effect="light" size="small" type="success">
                                {{ pixel.conversionEvent }}
                              </el-tag>
                            </div>
                          </div>
                          <el-button
                            :icon="Delete"
                            link
                            type="danger"
                            @click="handleRemovePixel(pixel)"
                          >
                            删除
                          </el-button>
                        </div>
                      </template>
                      <el-empty v-else :image-size="80" description="暂无绑定像素" />
                    </div>
                  </div>
                </el-scrollbar>
              </el-tab-pane>
            </el-tabs>
          </div>
        </div>
      </div>
    </div>
  </vab-dialog>

  <!-- 添加像素弹窗 -->
  <vab-dialog
    v-model="pixelDialogVisible"
    append-to-body
    title="添加像素"
    width="500px"
    @close="closePixelDialog"
  >
    <el-form label-width="80px">
      <el-form-item label="选择像素">
        <el-select
          v-model="selectedPixelId"
          clearable
          filterable
          :loading="pixelSearchLoading"
          placeholder="搜索并选择像素"
          remote
          :remote-method="remoteSearchPixel"
          style="width: 100%"
          @focus="handlePixelSelectFocus"
        >
          <el-option
            v-for="item in pixelOptions"
            :key="item.id"
            :disabled="isPixelAlreadyBound(item.id)"
            :label="item.pixelName"
            :value="item.id"
          >
            <div class="pixel-option">
              <span class="pixel-option-name">{{ item.pixelName }}</span>
              <span class="pixel-option-id">{{ item.pixelId }}</span>
              <el-tag v-if="isPixelAlreadyBound(item.id)" size="small" type="info">已绑定</el-tag>
            </div>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pixelDialogVisible = false">取消</el-button>
      <el-button
        :disabled="!selectedPixelId"
        :loading="addPixelLoading"
        type="primary"
        @click="confirmAddPixel"
      >
        确定
      </el-button>
    </template>
  </vab-dialog>

  <!-- 落地页SPU配置弹窗 -->
  <vab-dialog
    v-model="landingPageDialogVisible"
    append-to-body
    :title="`配置${landingPageTypeNames[currentLandingPageType]}`"
    width="500px"
    @close="closeLandingPageDialog"
  >
    <el-form label-width="80px">
      <el-form-item label="选择SPU">
        <el-select
          v-model="selectedLandingPageSpuId"
          clearable
          filterable
          :loading="landingPageSearchLoading"
          placeholder="搜索并选择SPU"
          remote
          :remote-method="remoteSearchLandingPageSpu"
          style="width: 100%"
          @focus="handleLandingPageSpuSelectFocus"
        >
          <el-option
            v-for="item in landingPageSpuOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          >
            <div class="spu-option">
              <span class="spu-option-name">{{ item.name }}</span>
              <span class="spu-option-code">{{ item.code }}</span>
            </div>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :loading="landingPageUnbindLoading" type="warning" @click="handleUseDefaultConfig">
        使用默认配置
      </el-button>
      <el-button
        :disabled="!selectedLandingPageSpuId"
        :loading="landingPageBindLoading"
        type="primary"
        @click="confirmBindLandingPageSpu"
      >
        确定
      </el-button>
    </template>
  </vab-dialog>

  <!-- 主题编辑器弹窗 -->
  <BuilderEditorDialog
    v-model:visible="themeEditorDialogVisible"
    :sub-domain-id="subDomainId"
    :spu-id="activeSpuTab"
    :landing-type="currentEditingLandingType"
    :sub-domain-name="subDomainFullName"
    :spu-name="currentSpuName"
    @close="handleThemeEditorClose"
    @save="handleThemeEditorSave"
  />

  <!-- 落地页协议绑定弹窗 -->
  <BindLandingPageProtocolEdit ref="protocolEditRef" @fetch-data="loadSpuDetail(activeSpuTab)" />

  <!-- 站点配置弹窗 -->
  <schema-form-dialog
    ref="siteConfigDialogRef"
    :schema="siteConfigSchema"
    :title="`${landingPageTypeNames[currentSiteLandingType]}站点配置`"
    @confirm="handleSiteConfigConfirm"
  />

  <!-- 应用模板弹窗 -->
  <el-dialog
    v-model="applyTemplateDialogVisible"
    :title="`应用主题模板 - ${landingPageTypeNames[applyTemplateLandingType]}`"
    width="600px"
    append-to-body
  >
    <el-alert type="warning" :closable="false" style="margin-bottom: 16px">
      应用模板后将覆盖当前落地页的主题配置，此操作不可撤销。
    </el-alert>
    <el-form label-width="100px">
      <el-form-item label="选择模板">
        <el-select
          v-model="selectedTemplateId"
          filterable
          placeholder="搜索并选择模板"
          remote
          :remote-method="remoteSearchTemplates"
          :loading="templateSearchLoading"
          style="width: 100%"
        >
          <el-option
            v-for="tpl in templateOptions"
            :key="tpl.id"
            :label="tpl.name"
            :value="Number(tpl.id)"
          >
            <div class="template-option">
              <span class="template-name">{{ tpl.name }}</span>
              <el-tag size="small" :type="getShareTypeTagType(tpl.shareType)">
                {{ tpl.shareTypeName || '私有' }}
              </el-tag>
            </div>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="applyTemplateDialogVisible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="applyTemplateLoading"
        :disabled="!selectedTemplateId"
        @click="handleApplyTemplateConfirm"
      >
        确定应用
      </el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import {
  Aim,
  Brush,
  Close,
  Connection,
  CopyDocument,
  Delete,
  Document,
  Flag,
  Link,
  Loading,
  Monitor,
  Notebook,
  Plus,
  Refresh,
  Search,
  Setting,
} from '@element-plus/icons-vue'
import SchemaFormDialog from './SchemaFormDialog.vue'
import BindLandingPageProtocolEdit from './BindLandingPageProtocolEdit.vue'
import BuilderEditorDialog from '/@/components/BuilderEditorDialog.vue'
import { getRemoteQuery as getRemoteQueryPixel } from '/@/api/pixelAccount'
import { getRemoteQuery } from '/@/api/spu'
import { remoteQuery as remoteQueryTemplates } from '/@/api/themeTemplate'
import {
  bindLandingPageSpu,
  bindSpu,
  bindSpuPixel,
  getBoundSpuDetail,
  getBoundSpus,
  unbindLandingPageSpu,
  unbindSpu,
  unbindSpuPixel,
} from '/@/api/subDomain'
import { getTicket } from '/@/api/user'
import { getToken } from '/@/utils/token'

defineOptions({
  name: 'BindSubDomainProduct',
})

const emit = defineEmits(['fetch-data', 'close'])
const $baseMessage = inject<any>('$baseMessage')
const $baseConfirm = inject<any>('$baseConfirm')

const dialogFormVisible = ref<boolean>(false)
const searchLoading = ref<boolean>(false)
const bindLoading = ref<boolean>(false)
const listLoading = ref<boolean>(false)
const refreshLoading = ref<boolean>(false)
const detailLoading = ref<boolean>(false)

const spuSelectRef = ref<any>(null)
const subDomainId = ref<number | string>('')
const subDomainFullName = ref<string>('')
const subDomainCountryName = ref<string>('')
const selectedSpuId = ref<number | string | null>(null)
const activeSpuTab = ref<string>('')
const activeDetailTab = ref<string>('landing')
const spuOptions = ref<any[]>([])
const boundSpuList = ref<any[]>([])
const boundFilterKeyword = ref<string>('')

// 落地页SPU类型
interface LandingPageSpu {
  id: number
  name: string
  code: string
  previewUrl: string
  supportCurrentCountry?: boolean
}

// 协议信息类型
interface ProtocolInfo {
  protocolId: number | null
  protocolName: string | null
  placeholderValues: Record<string, string> | null
}

// SPU详情数据
const spuDetail = ref<{
  realLandingPageSpu: LandingPageSpu | null
  crawlerLandingPageSpu: LandingPageSpu | null
  riskUserLandingPageSpu: LandingPageSpu | null
  blacklistLandingPageSpu: LandingPageSpu | null
  theme: { id: number; name: string } | null
  themeEditorUrl: string | null
  pixels: Array<{
    id: number
    name: string
    pixelId: string
    platform: string
    conversionEvent?: string
  }>
  realLandingPageProtocol: ProtocolInfo | null
  riskUserLandingPageProtocol: ProtocolInfo | null
  blacklistLandingPageProtocol: ProtocolInfo | null
}>({
  realLandingPageSpu: null,
  crawlerLandingPageSpu: null,
  riskUserLandingPageSpu: null,
  blacklistLandingPageSpu: null,
  theme: null,
  themeEditorUrl: null,
  pixels: [],
  realLandingPageProtocol: null,
  riskUserLandingPageProtocol: null,
  blacklistLandingPageProtocol: null,
})

// 像素弹窗相关
const pixelDialogVisible = ref<boolean>(false)
const pixelSearchLoading = ref<boolean>(false)
const addPixelLoading = ref<boolean>(false)
const selectedPixelId = ref<number | string | null>(null)
const pixelOptions = ref<any[]>([])

// 落地页SPU配置弹窗相关
const landingPageDialogVisible = ref<boolean>(false)
const landingPageSearchLoading = ref<boolean>(false)
const landingPageBindLoading = ref<boolean>(false)
const landingPageUnbindLoading = ref<boolean>(false)
const currentLandingPageType = ref<'LAND' | 'CLOAK' | 'BLACKLISTED'>('LAND')
const selectedLandingPageSpuId = ref<number | string | null>(null)
const landingPageSpuOptions = ref<any[]>([])

// 主题编辑器弹窗相关
const themeEditorDialogVisible = ref<boolean>(false)

// 当前 SPU 名称（用于传递给 BuilderEditorDialog）
const currentSpuName = computed(() => {
  const currentSpu = boundSpuList.value.find((spu) => String(spu.id) === activeSpuTab.value)
  return currentSpu?.name || ''
})

// 应用模板弹窗相关
const applyTemplateDialogVisible = ref<boolean>(false)
const applyTemplateLandingType = ref<'LAND' | 'CLOAK' | 'BLACKLISTED'>('LAND')
const selectedTemplateId = ref<number | null>(null)
const templateSearchLoading = ref<boolean>(false)
const applyTemplateLoading = ref<boolean>(false)
const templateOptions = ref<any[]>([])

// 协议绑定弹窗相关
const protocolEditRef = ref<InstanceType<typeof BindLandingPageProtocolEdit> | null>(null)

// 站点配置弹窗相关
const siteConfigDialogRef = ref<InstanceType<typeof SchemaFormDialog> | null>(null)
const currentSiteLandingType = ref<'LAND' | 'CLOAK' | 'BLACKLISTED'>('LAND')

// 站点配置 Schema（示例，实际应从接口获取或根据需求定义）
const siteConfigSchema = ref<Record<string, any>>({
  siteName: {
    type: 'string',
    label: '站点名称',
    required: true,
  },
  siteDescription: {
    type: 'string',
    label: '站点描述',
  },
  logoUrl: {
    type: 'image',
    label: '站点Logo',
  },
  primaryColor: {
    type: 'color',
    label: '主色调',
  },
  enableDarkMode: {
    type: 'boolean',
    label: '启用暗黑模式',
  },
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  subDomainId.value = row.id
  subDomainFullName.value = row.fullName || row.name
  subDomainCountryName.value = row.country?.name || ''
  selectedSpuId.value = null
  activeSpuTab.value = ''
  spuOptions.value = []
  boundFilterKeyword.value = ''
  // 加载已绑定的SPU列表
  loadBoundSpus()
}

defineExpose({
  showEdit,
})

const close = () => {
  selectedSpuId.value = null
  activeSpuTab.value = ''
  spuOptions.value = []
  boundSpuList.value = []
  boundFilterKeyword.value = ''
  emit('close')
  emit('fetch-data')
}

// 输入框获取焦点时加载初始数据
const handleSelectFocus = () => {
  if (spuOptions.value.length === 0) {
    remoteSearchSpu('')
  }
}

// 远程搜索SPU
const remoteSearchSpu = async (query: string) => {
  searchLoading.value = true
  try {
    const { data } = await getRemoteQuery(query || '')
    spuOptions.value = data.list || data || []
  } catch (error) {
    console.error('搜索SPU失败:', error)
    spuOptions.value = []
  } finally {
    searchLoading.value = false
  }
}

// 加载已绑定的SPU列表
const loadBoundSpus = async (keyword?: string) => {
  listLoading.value = true
  try {
    const { data } = await getBoundSpus(subDomainId.value, keyword)
    // 兼容 data 是数组或 { list: [] } 格式
    boundSpuList.value = Array.isArray(data) ? data : data?.list || []
    // 如果有数据，默认选中第一个
    if (boundSpuList.value.length > 0) {
      activeSpuTab.value = String(boundSpuList.value[0].id)
    } else {
      activeSpuTab.value = ''
    }
  } catch (error) {
    console.error('加载已绑定SPU失败:', error)
    boundSpuList.value = []
  } finally {
    listLoading.value = false
  }
}

// 过滤已绑定的SPU列表
const handleFilterBoundSpus = () => {
  loadBoundSpus(boundFilterKeyword.value || undefined)
}

// 刷新已绑定的SPU列表
const refreshBoundSpus = async () => {
  refreshLoading.value = true
  try {
    await loadBoundSpus()
    $baseMessage('刷新成功', 'success', 'hey')
  } finally {
    refreshLoading.value = false
  }
}

// 绑定SPU
const handleBindSpu = async () => {
  if (selectedSpuId.value === null || selectedSpuId.value === '') {
    $baseMessage('请先选择要绑定的SPU', 'warning', 'hey')
    return
  }

  // 检查是否已绑定
  const list = Array.isArray(boundSpuList.value) ? boundSpuList.value : []
  const isAlreadyBound = list.some((spu) => String(spu.id) === String(selectedSpuId.value))
  if (isAlreadyBound) {
    $baseMessage('该SPU已绑定', 'warning', 'hey')
    return
  }

  bindLoading.value = true
  try {
    const { msg, data }: any = await bindSpu(subDomainId.value, selectedSpuId.value)
    $baseMessage(msg || '绑定成功', 'success', 'hey')

    // 将后端返回的SPU数据直接添加到列表首项
    if (data) {
      boundSpuList.value.unshift(data)
      activeSpuTab.value = String(data.id)
    } else {
      // 如果后端未返回数据，重新加载列表
      await loadBoundSpus(boundFilterKeyword.value || undefined)
    }

    // 清空选择和搜索框内容
    selectedSpuId.value = null
    spuOptions.value = []
    // 清空 el-select 的搜索关键字
    if (spuSelectRef.value) {
      spuSelectRef.value.query = ''
    }
  } catch (error) {
    console.error('绑定SPU失败:', error)
  } finally {
    bindLoading.value = false
  }
}

// 复制文本
const copyText = async (text: string | number) => {
  try {
    await navigator.clipboard.writeText(String(text))
    $baseMessage('已复制到剪贴板', 'success', 'hey')
  } catch (error) {
    console.error('复制失败:', error)
    $baseMessage('复制失败', 'error', 'hey')
  }
}

// 解绑SPU
const handleUnbindSpu = (spu: any) => {
  $baseConfirm(`确定要解除绑定「${spu.name}」吗？`, null, async () => {
    try {
      const { msg }: any = await unbindSpu(subDomainId.value, spu.id)
      $baseMessage(msg || '解绑成功', 'success', 'hey')

      // 从列表中移除
      const index = boundSpuList.value.findIndex((item) => String(item.id) === String(spu.id))
      if (index > -1) {
        boundSpuList.value.splice(index, 1)
      }

      // 如果删除的是当前选中的，切换到第一个
      if (activeSpuTab.value === String(spu.id)) {
        activeSpuTab.value = boundSpuList.value.length > 0 ? String(boundSpuList.value[0].id) : ''
      }
    } catch (error) {
      console.error('解绑SPU失败:', error)
    }
  })
}

// 监听选中SPU变化，加载详情
watch(activeSpuTab, async (newVal) => {
  if (newVal) {
    await loadSpuDetail(newVal)
  } else {
    // 重置详情数据
    spuDetail.value = {
      realLandingPageSpu: null,
      crawlerLandingPageSpu: null,
      riskUserLandingPageSpu: null,
      blacklistLandingPageSpu: null,
      theme: null,
      themeEditorUrl: null,
      pixels: [],
      realLandingPageProtocol: null,
      riskUserLandingPageProtocol: null,
      blacklistLandingPageProtocol: null,
    }
  }
})

// 加载SPU详情
const loadSpuDetail = async (spuId: string) => {
  detailLoading.value = true
  try {
    const { data } = await getBoundSpuDetail(subDomainId.value, spuId)
    spuDetail.value = {
      realLandingPageSpu: data?.realLandingPageSpu || null,
      crawlerLandingPageSpu: data?.crawlerLandingPageSpu || null,
      riskUserLandingPageSpu: data?.riskUserLandingPageSpu || null,
      blacklistLandingPageSpu: data?.blacklistLandingPageSpu || null,
      theme: data?.theme || null,
      themeEditorUrl: data?.themeEditorUrl || null,
      pixels: data?.pixels || [],
      realLandingPageProtocol: data?.realLandingPageProtocol || null,
      riskUserLandingPageProtocol: data?.riskUserLandingPageProtocol || null,
      blacklistLandingPageProtocol: data?.blacklistLandingPageProtocol || null,
    }
  } catch (error) {
    console.error('加载SPU详情失败:', error)
  } finally {
    detailLoading.value = false
  }
}

// 预览落地页（统一使用真实落地页SPU ID）
// 真实落地页使用ticket，其他类型使用preview参数
const handlePreviewLanding = async (previewType: 'LAND' | 'CLOAK' | 'BLACKLISTED') => {
  const realSpuId = spuDetail.value.realLandingPageSpu?.id
  if (!realSpuId) {
    $baseMessage('未配置真实落地页，无法预览', 'warning', 'hey')
    return
  }

  if (previewType === 'LAND') {
    // 真实落地页使用ticket
    try {
      const res = await getTicket()
      const url = `https://${subDomainFullName.value}/product/${realSpuId}?ticket=${encodeURIComponent(res.data.ticket)}`
      window.open(url, '_blank')
    } catch (error) {
      console.error('获取ticket失败:', error)
      $baseMessage('获取预览凭证失败', 'error', 'hey')
    }
  } else {
    // 其他类型使用preview参数
    const url = `https://${subDomainFullName.value}/product/${realSpuId}?preview=${previewType}`
    window.open(url, '_blank')
  }
}

// 复制广告链接（不带ticket）
const handleCopyAdLink = async (spuId: number) => {
  const url = `https://${subDomainFullName.value}/product/${spuId}`
  try {
    await navigator.clipboard.writeText(url)
    $baseMessage('广告链接已复制到剪贴板', 'success', 'hey')
  } catch (error) {
    console.error('复制失败:', error)
    $baseMessage('复制失败', 'error', 'hey')
  }
}

// 当前编辑的落地页类型
const currentEditingLandingType = ref<'LAND' | 'CLOAK' | 'BLACKLISTED'>('LAND')

// 编辑落地页主题 - 打开 BuilderEditorDialog
const handleEditLandingTheme = (landingType: 'LAND' | 'CLOAK' | 'BLACKLISTED') => {
  currentEditingLandingType.value = landingType
  themeEditorDialogVisible.value = true
}

// 主题编辑器关闭
const handleThemeEditorClose = () => {
  loadSpuDetail(activeSpuTab.value)
}

// 主题编辑器保存
const handleThemeEditorSave = () => {
  loadSpuDetail(activeSpuTab.value)
}

// 绑定落地页协议
const handleBindProtocol = (landingType: 'LAND' | 'CLOAK' | 'BLACKLISTED') => {
  // 根据落地页类型获取当前的协议信息
  const protocolFieldMap: Record<string, string> = {
    LAND: 'realLandingPageProtocol',
    CLOAK: 'riskUserLandingPageProtocol',
    BLACKLISTED: 'blacklistLandingPageProtocol',
  }
  const protocolInfo = (spuDetail.value as any)[protocolFieldMap[landingType]] || null
  protocolEditRef.value?.showEdit({
    subDomainId: subDomainId.value,
    spuId: activeSpuTab.value,
    landingPageType: landingType,
    protocolId: protocolInfo?.protocolId || '',
    protocolName: protocolInfo?.protocolName || '',
    placeholderValues: protocolInfo?.placeholderValues || {},
  })
}

// 编辑落地页站点配置
const handleEditLandingSite = (landingType: 'LAND' | 'CLOAK' | 'BLACKLISTED') => {
  currentSiteLandingType.value = landingType
  // TODO: 从接口获取当前落地页类型的站点配置值
  const initialData = {} // 可以根据 landingType 获取对应的配置值
  siteConfigDialogRef.value?.open(initialData)
}

// 站点配置确认
const handleSiteConfigConfirm = (data: Record<string, any>) => {
  console.log('站点配置数据:', data, '落地页类型:', currentSiteLandingType.value)
  // TODO: 调用接口保存站点配置
  $baseMessage('站点配置保存成功', 'success', 'hey')
  // 刷新详情
  loadSpuDetail(activeSpuTab.value)
}

// 应用模板 - 打开弹窗
const handleApplyTemplate = (landingType: 'LAND' | 'CLOAK' | 'BLACKLISTED') => {
  applyTemplateLandingType.value = landingType
  selectedTemplateId.value = null
  templateOptions.value = []
  applyTemplateDialogVisible.value = true
  // 预加载模板列表
  remoteSearchTemplates('')
}

// 搜索模板
const remoteSearchTemplates = async (query: string) => {
  templateSearchLoading.value = true
  try {
    const { data } = await remoteQueryTemplates(query)
    templateOptions.value = data.list || []
  } catch (error) {
    console.error('搜索模板失败:', error)
    templateOptions.value = []
  } finally {
    templateSearchLoading.value = false
  }
}

// 确认应用模板
const handleApplyTemplateConfirm = async () => {
  if (!selectedTemplateId.value) {
    $baseMessage('请选择模板', 'warning', 'hey')
    return
  }

  applyTemplateLoading.value = true
  try {
    // 调用 Nuxt API 应用模板
    const response = await fetch(
      `${import.meta.env.VITE_NUXT_BUILDER_URL?.replace('/builder', '') || 'http://localhost:3000'}/api/builder/apply-template`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${getToken()}`,
        },
        body: JSON.stringify({
          subDomainId: subDomainId.value,
          spuId: activeSpuTab.value,
          landingType: applyTemplateLandingType.value,
          templateId: selectedTemplateId.value,
        }),
      }
    )

    const result = await response.json()

    if (result.success) {
      $baseMessage('模板应用成功', 'success', 'hey')
      applyTemplateDialogVisible.value = false
      // 刷新详情
      loadSpuDetail(activeSpuTab.value)
    } else {
      $baseMessage(result.message || '应用模板失败', 'error', 'hey')
    }
  } catch (error: any) {
    console.error('应用模板失败:', error)
    $baseMessage('应用模板失败，请重试', 'error', 'hey')
  } finally {
    applyTemplateLoading.value = false
  }
}

// 获取共享类型的标签类型
const getShareTypeTagType = (shareType: string) => {
  switch (shareType) {
    case 'COMPANY':
      return 'danger'
    case 'DEPARTMENT':
      return 'warning'
    default:
      return 'info'
  }
}

// 添加像素 - 打开弹窗
const handleAddPixel = () => {
  pixelDialogVisible.value = true
  selectedPixelId.value = null
  pixelOptions.value = []
}

// 关闭像素弹窗
const closePixelDialog = () => {
  selectedPixelId.value = null
  pixelOptions.value = []
}

// 像素选择框获取焦点时加载初始数据
const handlePixelSelectFocus = () => {
  if (pixelOptions.value.length === 0) {
    remoteSearchPixel('')
  }
}

// 远程搜索像素
const remoteSearchPixel = async (query: string) => {
  pixelSearchLoading.value = true
  try {
    const { data } = await getRemoteQueryPixel(query || '')
    pixelOptions.value = (data.list || []).map((item: any) => ({
      ...item,
      id: String(item.id),
    }))
  } catch (error) {
    console.error('搜索像素失败:', error)
    pixelOptions.value = []
  } finally {
    pixelSearchLoading.value = false
  }
}

// 检查像素是否已绑定
const isPixelAlreadyBound = (pixelId: string | number) => {
  return spuDetail.value.pixels.some((p) => String(p.id) === String(pixelId))
}

// 确认添加像素
const confirmAddPixel = async () => {
  if (!selectedPixelId.value) {
    $baseMessage('请选择要添加的像素', 'warning', 'hey')
    return
  }

  addPixelLoading.value = true
  try {
    const { msg }: any = await bindSpuPixel({
      subDomainId: subDomainId.value,
      spuId: activeSpuTab.value,
      pixelId: selectedPixelId.value,
    })
    $baseMessage(msg || '添加成功', 'success', 'hey')

    // 将新添加的像素加入列表
    const addedPixel = pixelOptions.value.find(
      (p) => String(p.id) === String(selectedPixelId.value)
    )
    if (addedPixel) {
      spuDetail.value.pixels.push({
        id: Number(addedPixel.id),
        name: addedPixel.pixelName,
        pixelId: addedPixel.pixelId,
        platform: addedPixel.platform || '',
      })
    } else {
      // 如果找不到，重新加载详情
      await loadSpuDetail(activeSpuTab.value)
    }

    pixelDialogVisible.value = false
  } catch (error) {
    console.error('添加像素失败:', error)
  } finally {
    addPixelLoading.value = false
  }
}

// 删除像素
const handleRemovePixel = (pixel: any) => {
  $baseConfirm(`确定要删除像素「${pixel.name}」吗？`, null, async () => {
    try {
      const { msg }: any = await unbindSpuPixel({
        subDomainId: subDomainId.value,
        spuId: activeSpuTab.value,
        pixelId: pixel.id,
      })
      $baseMessage(msg || '删除成功', 'success', 'hey')
      // 从列表中移除
      const index = spuDetail.value.pixels.findIndex((item) => item.id === pixel.id)
      if (index > -1) {
        spuDetail.value.pixels.splice(index, 1)
      }
    } catch (error) {
      console.error('删除像素失败:', error)
    }
  })
}

// 落地页类型名称映射
const landingPageTypeNames: Record<string, string> = {
  LAND: '真实落地页',
  CLOAK: '风险用户落地页',
  BLACKLISTED: '黑名单落地页',
}

// 打开落地页SPU配置弹窗
const handleConfigLandingPage = (type: 'LAND' | 'CLOAK' | 'BLACKLISTED') => {
  currentLandingPageType.value = type
  selectedLandingPageSpuId.value = null
  landingPageSpuOptions.value = []
  landingPageDialogVisible.value = true
}

// 关闭落地页SPU配置弹窗
const closeLandingPageDialog = () => {
  selectedLandingPageSpuId.value = null
  landingPageSpuOptions.value = []
}

// 落地页SPU选择框获取焦点时加载初始数据
const handleLandingPageSpuSelectFocus = () => {
  if (landingPageSpuOptions.value.length === 0) {
    remoteSearchLandingPageSpu('')
  }
}

// 远程搜索落地页SPU
const remoteSearchLandingPageSpu = async (query: string) => {
  landingPageSearchLoading.value = true
  try {
    const { data } = await getRemoteQuery(query || '')
    landingPageSpuOptions.value = data?.list || []
  } catch (error) {
    console.error('搜索SPU失败:', error)
    landingPageSpuOptions.value = []
  } finally {
    landingPageSearchLoading.value = false
  }
}

// 确认绑定落地页SPU
const confirmBindLandingPageSpu = async () => {
  if (!selectedLandingPageSpuId.value) {
    $baseMessage('请选择要绑定的SPU', 'warning', 'hey')
    return
  }

  landingPageBindLoading.value = true
  try {
    const { msg }: any = await bindLandingPageSpu({
      subDomainId: subDomainId.value,
      spuId: activeSpuTab.value,
      landingSpuId: selectedLandingPageSpuId.value,
      landingPageType: currentLandingPageType.value,
    })

    $baseMessage(msg || '配置成功', 'success', 'hey')

    // 重新加载详情
    await loadSpuDetail(activeSpuTab.value)

    landingPageDialogVisible.value = false
  } catch (error) {
    console.error('配置落地页SPU失败:', error)
  } finally {
    landingPageBindLoading.value = false
  }
}

// 使用默认配置（删除个性化配置）
const handleUseDefaultConfig = () => {
  $baseConfirm(
    `确定要删除${landingPageTypeNames[currentLandingPageType.value]}的个性化配置吗？删除后将显示店铺已关闭。`,
    null,
    async () => {
      landingPageUnbindLoading.value = true
      try {
        const { msg }: any = await unbindLandingPageSpu({
          subDomainId: subDomainId.value,
          spuId: activeSpuTab.value,
          landingPageType: currentLandingPageType.value,
        })

        $baseMessage(msg || '已恢复默认配置', 'success', 'hey')

        // 重新加载详情
        await loadSpuDetail(activeSpuTab.value)

        landingPageDialogVisible.value = false
      } catch (error) {
        console.error('删除个性化配置失败:', error)
      } finally {
        landingPageUnbindLoading.value = false
      }
    }
  )
}
</script>

<style lang="scss" scoped>
.product-manager {
  display: flex;
  flex-direction: column;
  height: 500px;
}

.top-action-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.main-content {
  display: flex;
  flex: 1;
  gap: 16px;
  min-height: 0;
}

.left-panel {
  display: flex;
  flex-direction: column;
  width: 300px;
  min-width: 300px;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgb(0 0 0 / 5%);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, var(--el-bg-color) 100%);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.panel-title {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);

  .title-icon {
    color: var(--el-color-primary);
  }
}

.filter-input {
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.spu-list-wrapper {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.spu-scrollbar {
  flex: 1;
}

.spu-list {
  padding: 8px;
}

.spu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  margin-bottom: 6px;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 1px solid transparent;
  border-radius: 6px;
  transition: all 0.2s ease;

  &:last-child {
    margin-bottom: 0;
  }

  &:hover {
    background: var(--el-fill-color-light);
    border-color: var(--el-border-color-light);

    .delete-btn {
      opacity: 1;
    }
  }

  &.active {
    background: var(--el-color-primary-light-9);
    border-color: var(--el-color-primary-light-5);

    .spu-name {
      font-weight: 500;
      color: var(--el-color-primary);

      &.unsupported {
        color: var(--el-color-danger);
      }
    }

    &:has(.spu-name.unsupported) {
      background: var(--el-color-danger-light-9);
      border-color: var(--el-color-danger-light-5);
    }
  }
}

.spu-info {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.spu-name-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.spu-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  line-height: 1.4;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  transition: color 0.2s;

  &.unsupported {
    color: var(--el-color-danger);
  }
}

.spu-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.spu-id {
  flex-shrink: 0;
}

.spu-code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copyable {
  cursor: pointer;
  border-radius: 2px;
  transition: all 0.2s;

  &:hover {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }
}

.delete-btn {
  flex-shrink: 0;
  margin-left: 8px;
  opacity: 0;
  transition: opacity 0.2s;
}

.right-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 0;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgb(0 0 0 / 5%);
}

.spu-detail {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
}

.detail-tabs {
  display: flex;
  flex-direction: column;
  height: 100%;

  :deep(.el-tabs__header) {
    flex-shrink: 0;
    padding: 0 16px;
    margin: 0;
    background: var(--el-fill-color-lighter);
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  :deep(.el-tabs__content) {
    flex: 1;
    min-height: 0;
    padding: 0;
  }

  :deep(.el-tab-pane) {
    height: 100%;
  }
}

.tab-label {
  display: flex;
  gap: 6px;
  align-items: center;

  .el-icon {
    font-size: 16px;
  }

  .pixel-badge {
    margin-left: 4px;

    :deep(.el-badge__content) {
      top: -2px;
    }
  }
}

.tab-content-scrollbar {
  height: 100%;
}

.tab-content {
  padding: 16px;
}

.landing-page-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.landing-page-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
  transition: background 0.2s;

  &:hover {
    background: var(--el-fill-color-light);
  }
}

.landing-label {
  flex-shrink: 0;
  width: 110px;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.landing-value {
  display: flex;
  flex: 1;
  gap: 12px;
  align-items: center;
  justify-content: flex-end;
}

.spu-link {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--el-text-color-primary);
  white-space: nowrap;

  &.unsupported {
    color: var(--el-color-danger);
  }

  &.clickable {
    cursor: pointer;
    color: var(--el-color-primary);

    &:hover {
      text-decoration: underline;
    }

    &.unsupported {
      color: var(--el-color-danger);
    }
  }

  &.closed {
    color: var(--el-text-color-secondary);
    font-style: italic;
  }
}

.empty-text {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

.pixel-header {
  margin-bottom: 16px;
}

.pixel-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pixel-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
  transition: background 0.2s;

  &:hover {
    background: var(--el-fill-color-light);
  }
}

.pixel-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.pixel-info {
  display: flex;
  gap: 12px;
  align-items: center;
}

.pixel-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.pixel-id {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pixel-event {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 12px;

  .event-icon {
    color: var(--el-color-success);
  }

  .event-label {
    color: var(--el-text-color-secondary);
  }
}

// 像素选择弹窗样式
.pixel-option {
  display: flex;
  gap: 12px;
  align-items: center;
  width: 100%;
}

.pixel-option-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pixel-option-id {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

// 落地页SPU选择弹窗样式
.spu-option {
  display: flex;
  gap: 12px;
  align-items: center;
  width: 100%;
}

.spu-option-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.spu-option-code {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
