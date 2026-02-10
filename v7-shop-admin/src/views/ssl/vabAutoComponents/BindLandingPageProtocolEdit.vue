<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="800px"
    @close="close"
  >
    <el-form
      ref="formRef"
      class="bind-protocol-form"
      label-width="100px"
      :model="form"
      :rules="rules"
    >
      <div class="form-section">
        <div class="section-title">
          <vab-icon icon="information-line" />
          基本信息
        </div>
        <el-row :gutter="20">
          <el-col :md="12" :sm="12" :xs="24">
            <el-form-item label="落地页类型" prop="landingPageType">
              <el-input :model-value="landingPageTypeNames[form.landingPageType] || form.landingPageType" disabled />
            </el-form-item>
          </el-col>
          <el-col :md="12" :sm="12" :xs="24">
            <el-form-item label="绑定协议" prop="protocolId">
              <el-select
                v-model="form.protocolId"
                clearable
                filterable
                :loading="selectLoading"
                placeholder="请搜索并选择协议"
                remote
                :remote-method="remoteQueryProtocol"
                style="width: 100%"
                @change="handleProtocolChange"
              >
                <el-option
                  v-for="item in options"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                >
                  <span style="float: left">{{ item.id }}</span>
                  <span
                    style="float: right; font-size: 13px; color: var(--el-text-color-secondary)"
                  >
                    {{ item.name }}
                  </span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <div v-if="selectedProtocol && selectedProtocol.placeholders?.length" class="form-section">
        <div class="section-title">
          <vab-icon icon="settings-3-line" />
          参数配置
          <span class="title-tip">（根据所选协议自动生成的占位符字段）</span>
        </div>
        <div class="placeholder-card">
          <el-row :gutter="20">
            <el-col
              v-for="ph in selectedProtocol.placeholders"
              :key="ph"
              :md="12"
              :sm="12"
              :xs="24"
            >
              <el-form-item
                :label="ph"
                :prop="'placeholderValues.' + ph"
                :rules="[{ required: false, message: '请输入' + ph, trigger: 'blur' }]"
              >
                <el-input
                  v-model="form.placeholderValues[ph]"
                  clearable
                  :placeholder="'请输入 ' + ph"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </div>
      </div>
    </el-form>
    <template #footer>
      <el-button
        v-if="form.protocolId"
        :loading="unbindLoading"
        type="danger"
        @click="handleUnbind"
      >
        解绑协议
      </el-button>
      <el-button @click="dialogFormVisible = false">取消</el-button>
      <el-button :loading="saveLoading" type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { getRemoteQuery } from '/@/api/protocol'
import { bindLandingPageProtocol } from '/@/api/subDomain'

defineOptions({
  name: 'BindLandingPageProtocolEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const options = ref<any[]>([])
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const selectLoading = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const unbindLoading = ref<boolean>(false)
const form = reactive<any>({
  subDomainId: '',
  spuId: '',
  landingPageType: '',
  protocolId: '',
  placeholderValues: {},
})
const rules = reactive<any>({
  protocolId: [{ required: false, trigger: 'change', message: '请选择绑定协议' }],
})

const landingPageTypeNames: Record<string, string> = {
  LAND: '真实落地页',
  CLOAK: '风险用户落地页',
  BLACKLISTED: '黑名单落地页',
}

const selectedProtocol = computed(() => {
  return options.value.find((item) => item.id === String(form.protocolId))
})

const handleProtocolChange = () => {
  form.placeholderValues = {}
  if (selectedProtocol.value?.placeholders) {
    selectedProtocol.value.placeholders.forEach((ph: string) => {
      form.placeholderValues[ph] = ''
    })
  }
}

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(async () => {
    title.value = `绑定协议 - ${landingPageTypeNames[row.landingPageType] || row.landingPageType}`
    form.subDomainId = row.subDomainId
    form.spuId = row.spuId
    form.landingPageType = row.landingPageType
    form.protocolId = row.protocolId ? String(row.protocolId) : ''
    form.placeholderValues = row.placeholderValues ? { ...row.placeholderValues } : {}

    // 如果已有协议，先预填 options 保证显示名称而不是 ID
    if (form.protocolId && row.protocolName) {
      options.value = [{ id: form.protocolId, name: row.protocolName }]
    }

    if (form.protocolId) {
      // 获取协议详情（包括 placeholders）
      await remoteQueryProtocol(form.protocolId)

      // 确保占位符字段都存在于 placeholderValues 中
      if (selectedProtocol.value?.placeholders) {
        selectedProtocol.value.placeholders.forEach((ph: string) => {
          if (!(ph in form.placeholderValues)) {
            form.placeholderValues[ph] = ''
          }
        })
      }
    }
  })
}

defineExpose({
  showEdit,
})

const close = () => {
  formRef.value.clearValidate()
  formRef.value.resetFields()
  Object.assign(form, {
    subDomainId: '',
    spuId: '',
    landingPageType: '',
    protocolId: '',
    placeholderValues: {},
  })
  emit('fetch-data')
}

const handleUnbind = async () => {
  unbindLoading.value = true
  try {
    const { msg }: any = await bindLandingPageProtocol({
      subDomainId: form.subDomainId,
      spuId: form.spuId,
      landingPageType: form.landingPageType,
      protocolId: '',
      placeholderValues: {},
    })
    await $baseMessage(msg || '解绑成功', 'success', 'hey')
    dialogFormVisible.value = false
  } finally {
    unbindLoading.value = false
  }
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        const { msg }: any = await bindLandingPageProtocol({
          subDomainId: form.subDomainId,
          spuId: form.spuId,
          landingPageType: form.landingPageType,
          protocolId: form.protocolId,
          placeholderValues: form.placeholderValues,
        })
        await $baseMessage(msg, 'success', 'hey')
        dialogFormVisible.value = false
      } finally {
        saveLoading.value = false
      }
    }
  })
}
const remoteQueryProtocol = async (query: string) => {
  selectLoading.value = true
  try {
    const { data } = await getRemoteQuery(query)
    options.value = (data.list || []).map((item: any) => ({
      ...item,
      id: String(item.id),
    }))
  } finally {
    selectLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.bind-protocol-form {
  max-height: 65vh;
  padding-right: 15px;
  overflow-y: auto;

  .form-section {
    margin-bottom: 25px;

    &:last-child {
      margin-bottom: 10px;
    }

    .el-col {
      margin-bottom: 5px;
    }

    .section-title {
      display: flex;
      align-items: center;
      margin-bottom: 20px;
      font-size: 15px;
      font-weight: bold;
      color: var(--el-text-color-primary);

      .vab-icon {
        margin-right: 8px;
        font-size: 18px;
        color: var(--el-color-primary);
      }

      .title-tip {
        margin-left: 8px;
        font-size: 12px;
        font-weight: normal;
        color: var(--el-text-color-secondary);
      }
    }
  }

  .placeholder-card {
    padding: 24px 20px 8px;
    background-color: var(--el-fill-color-lighter);
    border: 1px solid var(--el-border-color-light);
    border-radius: var(--el-border-radius-base);

    .el-col {
      margin-bottom: 24px;
    }

    :deep(.el-form-item) {
      margin-bottom: 0;
    }

    :deep(.el-form-item__label) {
      font-weight: 500;
    }
  }
}

/* 优化滚动条 */
.bind-protocol-form::-webkit-scrollbar {
  width: 6px;
}
.bind-protocol-form::-webkit-scrollbar-thumb {
  background: var(--el-border-color-darker);
  border-radius: 3px;
}
.bind-protocol-form::-webkit-scrollbar-track {
  background: transparent;
}
</style>
