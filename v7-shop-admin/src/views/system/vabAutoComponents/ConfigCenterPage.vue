<template>
  <div class="config-center-page">
    <vab-card>
      <template #header>
        <div class="header">
          <div class="title">
            <span>{{ pageTitle }}</span>
            <span v-if="pageDescription" class="desc">{{ pageDescription }}</span>
            <span v-if="editorDisabled" class="desc danger">未获取到部门信息，已禁用编辑</span>
          </div>
          <div class="actions">
            <el-button :loading="loading" @click="refreshAndFetch">刷新</el-button>
            <el-button :disabled="loading || editorDisabled" @click="resetToDefault">
              恢复默认
            </el-button>
            <el-button
              :disabled="editorDisabled"
              :loading="saveLoading"
              type="primary"
              @click="save"
            >
              保存
            </el-button>
          </div>
        </div>
      </template>

      <div v-loading="loading">
        <el-empty v-if="tabs.length === 0" description="无可配置项" />

        <el-tabs v-else v-model="activeTab">
          <el-tab-pane
            v-for="tab in tabs"
            :key="getTabKey(tab)"
            :label="tab.label || getTabKey(tab)"
            :name="getTabKey(tab)"
          >
            <div v-if="tab.description" class="tab-desc">{{ tab.description }}</div>

            <div
              v-if="
                !getTabSchema(getTabKey(tab)) ||
                Object.keys(getTabSchema(getTabKey(tab))).length === 0
              "
            >
              <el-empty description="无可配置项" />
            </div>

            <!-- i18n 前缀：按语言维护配置 -->
            <div
              v-else-if="isI18nTab(tab)"
              class="editor-area"
              :class="{ disabled: editorDisabled }"
            >
              <div class="i18n-header">
                <el-button
                  :disabled="editorDisabled"
                  type="primary"
                  @click="openAddLanguage(getTabKey(tab))"
                >
                  添加语言
                </el-button>
              </div>

              <div v-if="getI18nLanguages(getTabKey(tab)).length === 0" class="empty-schema">
                <el-empty description="请先添加语言" />
              </div>

              <el-tabs
                v-else
                :model-value="getActiveI18nLanguage(getTabKey(tab))"
                tab-position="left"
                @tab-remove="(name) => removeI18nLanguage(getTabKey(tab), String(name))"
                @update:model-value="(val) => setActiveI18nLanguage(getTabKey(tab), String(val))"
              >
                <el-tab-pane
                  v-for="lang in getI18nLanguages(getTabKey(tab))"
                  :key="lang.code"
                  closable
                  :label="lang.cname || lang.code"
                  :name="lang.code"
                >
                  <el-form
                    :ref="setFormRefFor(`${getTabKey(tab)}__${lang.code}`)"
                    label-width="150px"
                    :model="getI18nFormModel(getTabKey(tab), lang.code)"
                  >
                    <template v-for="(fieldSchema, key) in getTabSchema(getTabKey(tab))" :key="key">
                      <dynamic-form-field
                        :field-key="String(key)"
                        :field-schema="fieldSchema"
                        :model-value="form[getTabKey(tab)]?.[lang.code]?.[key]"
                        @update:model-value="
                          onI18nFieldUpdate(getTabKey(tab), lang.code, String(key), $event)
                        "
                      />
                    </template>
                  </el-form>
                </el-tab-pane>
              </el-tabs>

              <div v-if="editorDisabled" class="editor-mask" />
            </div>

            <div v-else class="editor-area" :class="{ disabled: editorDisabled }">
              <el-form
                v-if="!editorDisabled"
                :ref="setFormRefFor(getTabKey(tab))"
                label-width="150px"
                :model="form[getTabKey(tab)]"
              >
                <template v-for="(fieldSchema, key) in getTabSchema(getTabKey(tab))" :key="key">
                  <dynamic-form-field
                    :field-key="String(key)"
                    :field-schema="fieldSchema"
                    :model-value="form[getTabKey(tab)]?.[key]"
                    @update:model-value="onFieldUpdate(getTabKey(tab), String(key), $event)"
                  />
                </template>
              </el-form>

              <!-- 禁用状态下仍显示表单结构，但通过遮罩层阻止编辑 -->
              <el-form
                v-else
                :ref="setFormRefFor(getTabKey(tab))"
                label-width="150px"
                :model="form[getTabKey(tab)]"
              >
                <template v-for="(fieldSchema, key) in getTabSchema(getTabKey(tab))" :key="key">
                  <dynamic-form-field
                    :field-key="String(key)"
                    :field-schema="fieldSchema"
                    :model-value="form[getTabKey(tab)]?.[key]"
                    @update:model-value="onFieldUpdate(getTabKey(tab), String(key), $event)"
                  />
                </template>
              </el-form>
              <div v-if="editorDisabled" class="editor-mask" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </vab-card>

    <file-chooser ref="fileChooserRef" :z-index="5000" />

    <!-- 添加语言对话框 -->
    <vab-dialog v-model="showAddLanguageDialog" append-to-body title="添加语言" width="500px">
      <el-select
        v-model="selectedLanguage"
        filterable
        :loading="languageLoading"
        placeholder="请搜索并选择语言"
        remote
        :remote-method="remoteQueryLanguage"
        style="width: 100%"
        value-key="id"
      >
        <el-option
          v-for="item in languageOptions"
          :key="item.id"
          :disabled="getI18nLanguages(i18nTargetKey).some((l) => l.code === item.code)"
          :label="item.cname"
          :value="item"
        >
          <span style="float: left">{{ item.cname }}</span>
          <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
            {{ item.code }}
          </span>
        </el-option>
      </el-select>
      <template #footer>
        <el-button @click="showAddLanguageDialog = false">取消</el-button>
        <el-button type="primary" @click="addI18nLanguage">添加</el-button>
      </template>
    </vab-dialog>
  </div>
</template>

<script lang="ts" setup>
import {
  getConfigCenterSchema,
  getConfigCenterValue,
  refreshConfigCenterSchema,
  saveConfigCenter,
  type ConfigCenterName,
} from '/@/api/configCenter'
import { getRemoteQueryLanguage } from '/@/api/language'
import FileChooser from '/@/views/product/vabAutoComponents/FileChooser.vue'
import DynamicFormField from '/@/views/website/vabAutoComponents/DynamicFormField.vue'

defineOptions({
  name: 'ConfigCenterPage',
})

type ConfigTab = {
  name?: string
  label?: string
  description?: string
  // 新需求：不再使用 prefix，统一用 tabs.name 作为配置 key（prefix 仅作兼容兜底）
  prefix?: string
  // 新格式：default-values 直接是该 tab 的默认值对象（不再按 prefix 嵌套）
  ['default-values']?: any
  i18n?: boolean
}

const props = withDefaults(
  defineProps<{
    configName: ConfigCenterName
    departmentId?: number
    editorDisabled?: boolean
    pageTitle?: string
    pageDescription?: string
  }>(),
  {
    editorDisabled: false,
    pageTitle: '配置中心',
  }
)

const emit = defineEmits<{
  (e: 'refresh'): void
}>()

const $baseMessage = inject<any>('$baseMessage')
const $baseConfirm = inject<any>('$baseConfirm')

const editorDisabled = computed(() => !!props.editorDisabled)

const loading = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const schema = ref<any>(null)
const tabs = ref<ConfigTab[]>([])
const activeTab = ref<string>('')
const formRefs = ref<Record<string, any>>({})
const form = reactive<Record<string, any>>({})
const savedValue = ref<any>(null)

// i18n tab state - 每个 tab 独立维护语言列表
const showAddLanguageDialog = ref<boolean>(false)
const selectedLanguage = ref<any>(null)
const languageLoading = ref<boolean>(false)
const languageOptions = ref<any[]>([])
// 按 tabKey 存储每个 i18n tab 的语言列表: { [tabKey]: Language[] }
const i18nLanguagesMap = reactive<Record<string, any[]>>({})
// 按 tabKey 存储每个 i18n tab 当前激活的语言: { [tabKey]: string }
const activeI18nLanguageMap = reactive<Record<string, string>>({})
const i18nTargetKey = ref<string>('') // 当前正在操作的 i18n tab key(name)

// 获取指定 tab 的语言列表
const getI18nLanguages = (tabKey: string) => {
  return i18nLanguagesMap[tabKey] || []
}

// 获取指定 tab 当前激活的语言
const getActiveI18nLanguage = (tabKey: string) => {
  return activeI18nLanguageMap[tabKey] || ''
}

// 设置指定 tab 当前激活的语言
const setActiveI18nLanguage = (tabKey: string, langCode: string) => {
  activeI18nLanguageMap[tabKey] = langCode
}

const fileChooserRef = ref<any>(null)
provide('fileChooserRef', fileChooserRef)

const pageDescription = computed(() => {
  if (props.pageDescription !== undefined) return props.pageDescription
  return ''
})

const deepClone = (obj: any) => JSON.parse(JSON.stringify(obj ?? {}))

const normalizeSchemaPayload = (res: any) => {
  // request.ts 通常返回 { code, data, msg }
  const payload = res?.data ?? res
  // 兼容后端包一层 schema 的情况：{ schema: {...} }
  return payload?.schema ?? payload
}

const getTabKey = (tab: ConfigTab) => tab.name || tab.prefix || ''

const getTabSchema = (tabKey: string) => {
  if (!schema.value) return {}
  return schema.value?.[tabKey] || {}
}

const isI18nTab = (tab: ConfigTab) => tab.i18n === true || getTabKey(tab) === 'i18n'

const openAddLanguage = (tabKey: string) => {
  i18nTargetKey.value = tabKey
  showAddLanguageDialog.value = true
}

const setFormRef = (name: string, el: any) => {
  if (el) {
    formRefs.value[name] = el
  } else {
    // 当组件卸载时，el 为 null，需要清除旧引用
    delete formRefs.value[name]
  }
}

const setFormRefFor = (name: string) => (el: any) => setFormRef(name, el)

const updateField = (tabKey: string, key: string, val: any) => {
  if (!form[tabKey]) form[tabKey] = {}
  form[tabKey][key] = val
}

const onFieldUpdate = (tabKey: string, key: string, val: any) => updateField(tabKey, key, val)

const onI18nFieldUpdate = (tabKey: string, langCode: string, key: string, val: any) => {
  if (!form[tabKey]) form[tabKey] = {}
  if (!form[tabKey][langCode]) form[tabKey][langCode] = {}
  form[tabKey][langCode][key] = val
}

// 获取 i18n 表单的 model，确保不返回 undefined
const getI18nFormModel = (tabKey: string, langCode: string) => {
  if (!form[tabKey]) form[tabKey] = {}
  if (!form[tabKey][langCode]) form[tabKey][langCode] = {}
  return form[tabKey][langCode]
}

const initFormFromSchema = () => {
  // 清空旧数据
  Object.keys(form).forEach((k) => delete form[k])
  formRefs.value = {}
  // 清空所有 i18n 状态
  Object.keys(i18nLanguagesMap).forEach((k) => delete i18nLanguagesMap[k])
  Object.keys(activeI18nLanguageMap).forEach((k) => delete activeI18nLanguageMap[k])

  const t = tabs.value || []
  if (t.length > 0) {
    activeTab.value = getTabKey(t[0]) || ''
  } else {
    activeTab.value = ''
  }

  // 已保存配置值优先，其次兼容 schema 内置 values/config/current-values，最后使用 default-values
  const values =
    savedValue.value ||
    schema.value?.values ||
    schema.value?.config ||
    schema.value?.['current-values'] ||
    null

  t.forEach((tab: ConfigTab) => {
    const tabKey = getTabKey(tab)
    if (!tabKey) return
    const defaultValues = tab['default-values']
    const initVal = values?.[tabKey] ?? (isI18nTab(tab) ? {} : (defaultValues ?? {}))
    form[tabKey] = deepClone(initVal)

    // i18n tab：values 期望结构为 { [langCode]: { ...fields } }
    if (isI18nTab(tab)) {
      const langCodes = Object.keys(form[tabKey] || {})
      // 为每个 i18n tab 独立维护语言列表
      i18nLanguagesMap[tabKey] = langCodes
        .map((code) => {
          const lang = languageOptions.value.find((l: any) => l.code === code)
          return lang || { code, cname: code }
        })
        .filter(Boolean)
      activeI18nLanguageMap[tabKey] = i18nLanguagesMap[tabKey][0]?.code || ''
    }
  })
}

const remoteQueryLanguage = async (query: string) => {
  languageLoading.value = true
  try {
    const { data }: any = await getRemoteQueryLanguage(query || '')
    languageOptions.value = data?.list || []
  } finally {
    languageLoading.value = false
  }
}

const ensureLanguageOptionsLoaded = async () => {
  if (languageOptions.value.length > 0) return
  await remoteQueryLanguage('')
}

const addI18nLanguage = () => {
  if (!selectedLanguage.value) {
    $baseMessage('请选择语言', 'warning', 'hey')
    return
  }

  const tabKey = i18nTargetKey.value || 'i18n'
  const languages = getI18nLanguages(tabKey)

  if (languages.some((l) => l.code === selectedLanguage.value.code)) {
    $baseMessage('该语言已添加', 'warning', 'hey')
    return
  }

  const tab = tabs.value.find((t) => getTabKey(t) === tabKey)
  const defaultValues = tab?.['default-values'] ?? {}
  if (!form[tabKey]) form[tabKey] = {}
  if (!form[tabKey][selectedLanguage.value.code]) {
    // 默认值优先，其次按 schema 初始化空字符串
    form[tabKey][selectedLanguage.value.code] = deepClone(defaultValues)
    const tabSchema = getTabSchema(tabKey)
    Object.keys(tabSchema || {}).forEach((k) => {
      if (form[tabKey][selectedLanguage.value.code][k] === undefined) {
        form[tabKey][selectedLanguage.value.code][k] = ''
      }
    })
  }

  // 初始化该 tab 的语言列表（如果不存在）
  if (!i18nLanguagesMap[tabKey]) {
    i18nLanguagesMap[tabKey] = []
  }
  i18nLanguagesMap[tabKey].push(selectedLanguage.value)
  activeI18nLanguageMap[tabKey] = selectedLanguage.value.code

  showAddLanguageDialog.value = false
  selectedLanguage.value = null
}

const removeI18nLanguage = (tabKey: string, code: string) => {
  const languages = i18nLanguagesMap[tabKey] || []
  const index = languages.findIndex((l) => l.code === code)
  if (index === -1) return

  const activeCode = activeI18nLanguageMap[tabKey]
  if (activeCode === code) {
    if (languages.length > 1) {
      const nextIndex = index < languages.length - 1 ? index : index - 1
      activeI18nLanguageMap[tabKey] = languages[nextIndex].code
    } else {
      activeI18nLanguageMap[tabKey] = ''
    }
  }

  languages.splice(index, 1)
  if (form[tabKey]) {
    delete form[tabKey][code]
  }
  delete formRefs.value[`${tabKey}__${code}`]
}

const fetchSavedValue = async () => {
  try {
    const res: any = await getConfigCenterValue(props.configName, {
      departmentId: props.departmentId,
    })
    // 返回为 JSONObject，正常情况下在 data 里
    savedValue.value = res?.data ?? res ?? null
  } catch (error) {
    // 没有已保存值 / 后端报错时不阻断页面，继续使用默认值
    savedValue.value = null
  }
}

const fetchSchema = async () => {
  try {
    loading.value = true
    const res = await getConfigCenterSchema(props.configName)
    const s = normalizeSchemaPayload(res)
    schema.value = s || null
    tabs.value = Array.isArray(s?.tabs) ? s.tabs : []
    if (tabs.value.some((t) => isI18nTab(t))) {
      await ensureLanguageOptionsLoaded()
    }
    await fetchSavedValue()
    initFormFromSchema()
  } catch (error) {
    console.error('获取配置 schema 失败:', error)
    $baseMessage('获取配置失败', 'error', 'hey')
    schema.value = null
    tabs.value = []
    savedValue.value = null
    initFormFromSchema()
  } finally {
    loading.value = false
  }
}

const refreshAndFetch = async () => {
  emit('refresh')
  try {
    loading.value = true
    await refreshConfigCenterSchema(props.configName)
  } catch (error) {
    console.error('刷新配置 schema 失败:', error)
    // 刷新失败不阻断后续拉取，尽量拿到可用配置
  } finally {
    loading.value = false
  }
  await fetchSchema()
}

const validateAll = async () => {
  // 等待 DOM 更新完成，确保表单引用已正确设置
  await nextTick()

  // 验证所有 tab 的表单
  let refsToValidate: [string, any][] = []

  tabs.value.forEach((tab) => {
    const tabKey = getTabKey(tab)
    if (!tabKey) return

    if (isI18nTab(tab)) {
      // i18n 模式：验证该 tab 下所有语言的表单
      const languages = getI18nLanguages(tabKey)
      languages.forEach((lang) => {
        const refKey = `${tabKey}__${lang.code}`
        const ref = formRefs.value[refKey]
        if (ref) {
          refsToValidate.push([refKey, ref])
        }
      })
    } else {
      // 非 i18n 模式：验证该 tab 的表单
      const ref = formRefs.value[tabKey]
      if (ref) {
        refsToValidate.push([tabKey, ref])
      }
    }
  })

  if (refsToValidate.length === 0) return true

  const results = await Promise.all(
    refsToValidate.map(
      ([name, r]: [string, any]) =>
        new Promise<boolean>((resolve) => {
          // 设置超时，防止验证卡死
          const timeoutId = setTimeout(() => {
            console.warn(`表单 ${name} 验证超时，视为验证通过`)
            resolve(true) // 超时时视为通过，避免阻塞保存
          }, 5000) // 5秒超时

          try {
            if (!r || typeof r.validate !== 'function') {
              clearTimeout(timeoutId)
              resolve(true)
              return
            }

            // Element Plus 的 validate 返回 Promise
            const validateResult = r.validate()

            // 如果 validate 返回 Promise，使用 Promise
            if (validateResult && typeof validateResult.then === 'function') {
              validateResult
                .then(() => {
                  clearTimeout(timeoutId)
                  resolve(true)
                })
                .catch((error: any) => {
                  // Element Plus validate 在验证失败时会 reject
                  // error 可能是 { fields: {...} } 对象或 false
                  console.warn(`表单 ${name} 验证失败:`, error)
                  clearTimeout(timeoutId)
                  resolve(false)
                })
            } else {
              // 如果不返回 Promise，视为验证通过
              clearTimeout(timeoutId)
              resolve(true)
            }
          } catch (error) {
            console.error(`表单 ${name} 验证异常:`, error)
            clearTimeout(timeoutId)
            resolve(false)
          }
        })
    )
  )
  return results.every(Boolean)
}

const resetToDefault = () => {
  if (!tabs.value || tabs.value.length === 0) return

  $baseConfirm('确定恢复默认配置吗？这会覆盖当前所有修改。', '恢复默认', () => {
    tabs.value.forEach((tab: ConfigTab) => {
      const tabKey = getTabKey(tab)
      if (!tabKey) return
      const defaultValues = tab['default-values'] ?? {}

      if (isI18nTab(tab)) {
        const langCodes = Object.keys(form[tabKey] || {})
        // 保留语言列表，重置每个语言的内容为 default-values
        const next: Record<string, any> = {}
        langCodes.forEach((code) => {
          next[code] = deepClone(defaultValues)
        })
        form[tabKey] = next
      } else {
        form[tabKey] = deepClone(defaultValues)
      }
    })
    nextTick(() => {
      Object.values(formRefs.value).forEach((r: any) => r?.clearValidate?.())
    })
    $baseMessage('已恢复默认配置', 'success', 'hey')
  })
}

const save = async () => {
  if (editorDisabled.value) return
  const ok = await validateAll()
  if (!ok) {
    $baseMessage('请检查表单填写是否正确', 'warning', 'hey')
    return
  }

  try {
    saveLoading.value = true
    // 按 prefix 聚合提交，例如：{ email: {...}, otherPrefix: {...} }
    const payload = deepClone(form)
    const { msg }: any = await saveConfigCenter({
      configName: props.configName,
      configValue: payload,
      departmentId: props.departmentId,
    })
    if (msg) $baseMessage(msg, 'success', 'hey')
    else $baseMessage('保存成功', 'success', 'hey')
  } finally {
    saveLoading.value = false
  }
}

// 监听 departmentId 变化，当从 undefined 变为有效值时重新获取数据
watch(
  () => props.departmentId,
  (newVal, oldVal) => {
    // 当 departmentId 从 undefined 变为有效值时，重新获取数据
    if (oldVal === undefined && newVal !== undefined) {
      fetchSchema()
    }
  }
)

onBeforeMount(() => {
  fetchSchema()
})
</script>

<style lang="scss" scoped>
.header {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;

  .title {
    display: flex;
    flex-direction: column;
    gap: 4px;
    .desc {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
    .danger {
      color: var(--el-color-danger);
    }
  }

  .actions {
    display: flex;
    gap: 8px;
  }
}

.tab-desc {
  margin-bottom: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.editor-area {
  position: relative;
}

.editor-mask {
  position: absolute;
  inset: 0;
  z-index: 2;
  cursor: not-allowed;
  background: rgba(255, 255, 255, 0.45);
}
</style>
