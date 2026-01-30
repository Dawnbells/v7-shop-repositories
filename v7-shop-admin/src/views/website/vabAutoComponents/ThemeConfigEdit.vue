<template>
  <vab-dialog v-model="dialogFormVisible" append-to-body :title="title" width="90%" @close="close">
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="基础配置" name="base">
        <div v-if="!baseSchema || Object.keys(baseSchema).length === 0" class="empty-schema">
          <el-empty description="无可配置项" />
        </div>
        <el-form v-else ref="baseFormRef" label-width="150px" :model="form.base" :rules="baseRules">
          <template v-for="(fieldSchema, key) in baseSchema" :key="key">
            <dynamic-form-field
              :field-key="String(key)"
              :field-schema="fieldSchema"
              :model-value="form.base[key]"
              @update:model-value="form.base[key] = $event"
            />
          </template>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="模板信息配置" name="template">
        <div
          v-if="!templateSchema || Object.keys(templateSchema).length === 0"
          class="empty-schema"
        >
          <el-empty description="无可配置项" />
        </div>
        <el-form
          v-else
          ref="templateFormRef"
          label-width="150px"
          :model="form.template"
          :rules="templateRules"
        >
          <template v-for="(fieldSchema, key) in templateSchema" :key="key">
            <dynamic-form-field
              :field-key="String(key)"
              :field-schema="fieldSchema"
              :model-value="form.template[key]"
              @update:model-value="form.template[key] = $event"
            />
          </template>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="i18n配置" name="i18n">
        <div class="i18n-config-container">
          <div class="i18n-header">
            <el-button type="primary" @click="showAddLanguageDialog = true">添加语言</el-button>
          </div>
          <div v-if="i18nLanguages.length === 0" class="empty-schema">
            <el-empty description="请先添加语言" />
          </div>
          <el-tabs
            v-else
            v-model="activeI18nLanguage"
            tab-position="left"
            @tab-remove="removeI18nLanguage"
          >
            <el-tab-pane
              v-for="lang in i18nLanguages"
              :key="lang.code"
              closable
              :label="lang.cname"
              :name="lang.code"
            >
              <div v-if="!i18nSchema || Object.keys(i18nSchema).length === 0" class="empty-schema">
                <el-empty description="无可配置项" />
              </div>
              <el-form
                v-else
                :ref="(el) => setI18nFormRef(lang.code, el)"
                label-width="150px"
                :model="form.i18n[lang.code]"
                :rules="i18nRules"
              >
                <template v-for="(fieldSchema, key) in i18nSchema" :key="key">
                  <dynamic-form-field
                    :field-key="String(key)"
                    :field-schema="fieldSchema"
                    :model-value="form.i18n[lang.code]?.[key]"
                    @update:model-value="
                      form.i18n[lang.code] = { ...form.i18n[lang.code], [key]: $event }
                    "
                  />
                </template>
              </el-form>
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-tab-pane>
      <el-tab-pane label="皮肤配置" name="theme">
        <div v-if="!themeSchema || Object.keys(themeSchema).length === 0" class="empty-schema">
          <el-empty description="无可配置项" />
        </div>
        <el-form
          v-else
          ref="themeFormRef"
          label-width="150px"
          :model="form.theme"
          :rules="themeRules"
        >
          <template v-for="(fieldSchema, key) in themeSchema" :key="key">
            <dynamic-form-field
              :field-key="String(key)"
              :field-schema="fieldSchema"
              :model-value="form.theme[key]"
              @update:model-value="form.theme[key] = $event"
            />
          </template>
        </el-form>
      </el-tab-pane>
    </el-tabs>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button @click="resetToDefault">恢复默认</el-button>
      <el-button :loading="saveLoading" type="primary" @click="save">保存</el-button>
    </template>
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
          :disabled="i18nLanguages.some((l) => l.code === item.code)"
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
    <file-chooser ref="fileChooserRef" :z-index="5000" />
  </vab-dialog>
</template>

<script lang="ts" setup>
import { getRemoteQueryLanguage } from '/@/api/language'
import { saveThemeConfig } from '/@/api/theme'

defineOptions({
  name: 'ThemeConfigEdit',
})

const props = withDefaults(
  defineProps<{
    templateOptions?: any[]
  }>(),
  {
    templateOptions: () => [],
  }
)

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const $baseConfirm = inject<any>('$baseConfirm')
const fileChooserRef = ref<any>(null)
provide('fileChooserRef', fileChooserRef)
const saveLoading = ref<boolean>(false)
const title = ref<string>('主题配置')
const dialogFormVisible = ref<boolean>(false)
const activeTab = ref<string>('base')
const activeI18nLanguage = ref<string>('')
const showAddLanguageDialog = ref<boolean>(false)
const selectedLanguage = ref<any>(null)
const languageLoading = ref<boolean>(false)
const languageOptions = ref<any[]>([])
const i18nLanguages = ref<any[]>([])
const i18nFormRefs = ref<any>({})

const currentTheme = ref<any>(null)
const templateData = ref<any>(null)

const form = reactive<any>({
  id: undefined,
  base: {},
  template: {},
  i18n: {},
  theme: {},
})

const baseSchema = computed(() => templateData.value?.baseSchema || {})
const templateSchema = computed(() => templateData.value?.templateSchema || {})
const i18nSchema = computed(() => templateData.value?.i18nSchema || {})
const themeSchema = computed(() => templateData.value?.themeSchema || {})

const baseRules = computed(() => generateRules(baseSchema.value))
const templateRules = computed(() => generateRules(templateSchema.value))
const i18nRules = computed(() => generateRules(i18nSchema.value))
const themeRules = computed(() => generateRules(themeSchema.value))

const baseFormRef = ref<any>(null)
const templateFormRef = ref<any>(null)
const themeFormRef = ref<any>(null)

function generateRules(schema: any) {
  const rules: any = {}
  if (!schema) return rules
  Object.keys(schema).forEach((key) => {
    const field = schema[key]
    if (field.required) {
      rules[key] = [{ required: true, message: `${field.label || key}不能为空`, trigger: 'blur' }]
    }
  })
  return rules
}

function setI18nFormRef(code: string, el: any) {
  if (el) {
    i18nFormRefs.value[code] = el
  }
}

const showEdit = async (row: any) => {
  currentTheme.value = row
  dialogFormVisible.value = true
  activeTab.value = 'base'

  // 根据templateName找到对应的模板数据
  const template = props.templateOptions.find((t: any) => t.name === row.templateName)
  if (!template) {
    $baseMessage('未找到对应的模板数据', 'error', 'hey')
    return
  }
  templateData.value = template

  // 初始化表单数据，传入完整的row数据
  await initializeForm(row)
}

defineExpose({
  showEdit,
})

const initializeForm = async (row: any) => {
  try {
    // 先加载语言选项
    await remoteQueryLanguage('')

    // 从row数据中解析配置（JSON字符串格式）
    let parsedBaseConfig = null
    let parsedTemplateConfig = null
    let parsedI18nConfig = null
    let parsedThemeConfig = null

    // 解析baseConfig
    if (row.baseConfig) {
      try {
        parsedBaseConfig =
          typeof row.baseConfig === 'string' ? JSON.parse(row.baseConfig) : row.baseConfig
      } catch {
        console.error('解析baseConfig失败:', row.baseConfig)
      }
    }

    // 解析templateConfig
    if (row.templateConfig) {
      try {
        parsedTemplateConfig =
          typeof row.templateConfig === 'string'
            ? JSON.parse(row.templateConfig)
            : row.templateConfig
      } catch {
        console.error('解析templateConfig失败:', row.templateConfig)
      }
    }

    // 解析i18nConfig
    if (row.i18nConfig) {
      try {
        parsedI18nConfig =
          typeof row.i18nConfig === 'string' ? JSON.parse(row.i18nConfig) : row.i18nConfig
      } catch {
        console.error('解析i18nConfig失败:', row.i18nConfig)
      }
    }

    // 解析themeConfig
    if (row.themeConfig) {
      try {
        parsedThemeConfig =
          typeof row.themeConfig === 'string' ? JSON.parse(row.themeConfig) : row.themeConfig
      } catch {
        console.error('解析themeConfig失败:', row.themeConfig)
      }
    }

    // 初始化base配置
    if (parsedBaseConfig) {
      form.base = parsedBaseConfig
    } else if (templateData.value?.baseValues) {
      form.base = JSON.parse(JSON.stringify(templateData.value.baseValues))
    } else {
      form.base = {}
    }

    // 初始化config配置
    if (parsedTemplateConfig) {
      form.template = parsedTemplateConfig
    } else if (templateData.value?.templateValues) {
      form.template = JSON.parse(JSON.stringify(templateData.value.templateValues))
    } else {
      form.template = {}
    }

    // 初始化i18n配置
    if (parsedI18nConfig) {
      form.i18n = parsedI18nConfig
      // 设置i18n语言列表 - 从已保存的配置中获取语言代码
      const languageCodes = Object.keys(parsedI18nConfig)
      i18nLanguages.value = languageCodes
        .map((code) => {
          const lang = languageOptions.value.find((l: any) => l.code === code)
          return lang || { code, cname: code }
        })
        .filter((item) => !!item)
      if (i18nLanguages.value.length > 0) {
        activeI18nLanguage.value = i18nLanguages.value[0].code
      }
    } else if (templateData.value?.i18nValues) {
      form.i18n = JSON.parse(JSON.stringify(templateData.value.i18nValues))
      // 设置i18n语言列表 - 从模板的i18nValues中获取语言代码
      const languageCodes = Object.keys(templateData.value.i18nValues)
      i18nLanguages.value = languageCodes
        .map((code) => {
          const lang = languageOptions.value.find((l: any) => l.code === code)
          return lang || { code, cname: code }
        })
        .filter((item) => !!item)
      if (i18nLanguages.value.length > 0) {
        activeI18nLanguage.value = i18nLanguages.value[0].code
      }
    } else {
      form.i18n = {}
      i18nLanguages.value = []
    }

    // 初始化theme配置
    if (parsedThemeConfig) {
      form.theme = parsedThemeConfig
    } else if (templateData.value?.themeValues) {
      form.theme = JSON.parse(JSON.stringify(templateData.value.themeValues))
    } else {
      form.theme = {}
    }

    form.id = row.id
  } catch (error) {
    console.error('初始化表单失败:', error)
    $baseMessage('初始化表单失败', 'error', 'hey')
  }
}

const remoteQueryLanguage = async (query: string) => {
  languageLoading.value = true
  try {
    const { data } = await getRemoteQueryLanguage(query || '')
    languageOptions.value = data.list || []
  } finally {
    languageLoading.value = false
  }
}

const addI18nLanguage = () => {
  if (!selectedLanguage.value) {
    $baseMessage('请选择语言', 'warning', 'hey')
    return
  }

  if (i18nLanguages.value.some((l) => l.code === selectedLanguage.value.code)) {
    $baseMessage('该语言已添加', 'warning', 'hey')
    return
  }

  i18nLanguages.value.push(selectedLanguage.value)

  // 初始化该语言的配置数据
  if (!form.i18n[selectedLanguage.value.code]) {
    // 使用默认值或空对象
    const defaultValues = templateData.value?.i18nValues?.[selectedLanguage.value.code] || {}
    form.i18n[selectedLanguage.value.code] = JSON.parse(JSON.stringify(defaultValues))

    // 如果schema中有字段，初始化空值
    if (i18nSchema.value) {
      Object.keys(i18nSchema.value).forEach((key) => {
        if (!form.i18n[selectedLanguage.value.code][key]) {
          form.i18n[selectedLanguage.value.code][key] = ''
        }
      })
    }
  }

  activeI18nLanguage.value = selectedLanguage.value.code
  showAddLanguageDialog.value = false
  selectedLanguage.value = null
}

const removeI18nLanguage = (name: string | number) => {
  const code = String(name)
  const index = i18nLanguages.value.findIndex((l) => l.code === code)
  if (index !== -1) {
    // 如果删除的是当前激活的语言，需要切换到其他语言
    if (activeI18nLanguage.value === code) {
      if (i18nLanguages.value.length > 1) {
        // 如果还有其他语言，切换到下一个（或前一个）
        const nextIndex = index < i18nLanguages.value.length - 1 ? index : index - 1
        activeI18nLanguage.value = i18nLanguages.value[nextIndex].code
      } else {
        // 如果没有其他语言了，清空激活状态
        activeI18nLanguage.value = ''
      }
    }
    // 从语言列表中移除
    i18nLanguages.value.splice(index, 1)
    // 从表单数据中删除该语言的配置
    delete form.i18n[code]
    // 清理表单引用
    delete i18nFormRefs.value[code]
    // 如果删除后没有语言了，确保激活状态为空
    if (i18nLanguages.value.length === 0) {
      activeI18nLanguage.value = ''
    }
  }
}

const resetToDefault = () => {
  if (!templateData.value) {
    $baseMessage('无法恢复默认值，模板数据不存在', 'warning', 'hey')
    return
  }

  $baseConfirm('您确定要恢复为默认配置吗？这将覆盖当前所有配置。', '恢复默认配置', () => {
    // 重置base配置为模板默认值
    if (templateData.value?.baseValues) {
      form.base = JSON.parse(JSON.stringify(templateData.value.baseValues))
    } else {
      form.base = {}
    }

    // 重置template配置为模板默认值
    if (templateData.value?.templateValues) {
      form.template = JSON.parse(JSON.stringify(templateData.value.templateValues))
    } else {
      form.template = {}
    }

    // 重置i18n配置为模板默认值
    if (templateData.value?.i18nValues) {
      form.i18n = JSON.parse(JSON.stringify(templateData.value.i18nValues))
      // 设置i18n语言列表 - 从模板的i18nValues中获取语言代码
      const languageCodes = Object.keys(templateData.value.i18nValues)
      i18nLanguages.value = languageCodes
        .map((code) => {
          const lang = languageOptions.value.find((l: any) => l.code === code)
          return lang || { code, cname: code }
        })
        .filter((item) => !!item)
      if (i18nLanguages.value.length > 0) {
        activeI18nLanguage.value = i18nLanguages.value[0].code
      } else {
        activeI18nLanguage.value = ''
      }
    } else {
      form.i18n = {}
      i18nLanguages.value = []
      activeI18nLanguage.value = ''
    }

    // 重置theme配置为模板默认值
    if (templateData.value?.themeValues) {
      form.theme = JSON.parse(JSON.stringify(templateData.value.themeValues))
    } else {
      form.theme = {}
    }

    // 清空所有表单验证状态
    nextTick(() => {
      baseFormRef.value?.clearValidate()
      templateFormRef.value?.clearValidate()
      themeFormRef.value?.clearValidate()
      Object.values(i18nFormRefs.value).forEach((formRef: any) => {
        if (formRef) {
          formRef.clearValidate()
        }
      })
    })

    // 切换回基础配置tab
    activeTab.value = 'base'

    $baseMessage('已恢复为默认配置', 'success', 'hey')
  })
}

const close = () => {
  form.id = undefined
  form.base = {}
  form.template = {}
  form.i18n = {}
  form.theme = {}
  i18nLanguages.value = []
  activeI18nLanguage.value = ''
  templateData.value = null
  currentTheme.value = null
  dialogFormVisible.value = false
  emit('fetch-data')
}

const validateAllForms = async () => {
  const validations: Promise<boolean>[] = []

  // 验证base表单
  if (baseFormRef.value && Object.keys(baseSchema.value).length > 0) {
    validations.push(
      new Promise((resolve) => {
        baseFormRef.value?.validate((valid: boolean) => resolve(valid))
      })
    )
  }

  // 验证template表单
  if (templateFormRef.value && Object.keys(templateSchema.value).length > 0) {
    validations.push(
      new Promise((resolve) => {
        templateFormRef.value?.validate((valid: boolean) => resolve(valid))
      })
    )
  }

  // 验证所有i18n表单
  Object.values(i18nFormRefs.value).forEach((formRef: any) => {
    if (formRef && Object.keys(i18nSchema.value).length > 0) {
      validations.push(
        new Promise((resolve) => {
          formRef.validate((valid: boolean) => resolve(valid))
        })
      )
    }
  })

  // 验证theme表单
  if (themeFormRef.value && Object.keys(themeSchema.value).length > 0) {
    validations.push(
      new Promise((resolve) => {
        themeFormRef.value?.validate((valid: boolean) => resolve(valid))
      })
    )
  }

  const results = await Promise.all(validations)
  return results.every(Boolean)
}

const save = async () => {
  const isValid = await validateAllForms()
  if (!isValid) {
    $baseMessage('请检查表单填写是否正确', 'warning', 'hey')
    return
  }

  try {
    saveLoading.value = true
    // 将每个字段配置转换为JSON字符串
    const saveData = {
      id: form.id,
      base: JSON.stringify(form.base),
      template: JSON.stringify(form.template),
      i18n: JSON.stringify(form.i18n),
      theme: JSON.stringify(form.theme),
    }
    const { msg }: any = await saveThemeConfig(saveData)
    await $baseMessage(msg, 'success', 'hey')
    dialogFormVisible.value = false
  } finally {
    saveLoading.value = false
  }
}
</script>

<style scoped>
.empty-schema {
  padding: 40px 0;
  text-align: center;
}

.i18n-config-container {
  min-height: 400px;
}

.i18n-header {
  margin-bottom: 16px;
}

:deep(.el-tabs--left) {
  min-height: 400px;
}
</style>
