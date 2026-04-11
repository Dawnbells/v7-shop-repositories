<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="800px"
    @close="close"
  >
    <el-form ref="formRef" label-width="100px" :model="form" :rules="rules">
      <el-form-item label="域名" prop="name">
        <el-input
          v-model.trim="form.name"
          disabled
          :placeholder="domainType === 'topLevel' ? '一级域名' : '二级域名'"
        />
      </el-form-item>
      <el-form-item label="绑定像素" prop="pixelIds">
        <el-select
          v-model="form.pixelIds"
          clearable
          filterable
          :loading="selectLoading"
          multiple
          placeholder="请搜索并选择像素账户"
          remote
          :remote-method="remoteQueryPixel"
          style="width: 100%"
          @change="handlePixelChange"
        >
          <el-option
            v-for="item in options"
            :key="item.id"
            :label="item.pixelName"
            :value="item.id"
          >
            <span style="float: left">{{ item.pixelId }}</span>
            <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
              {{ item.pixelName }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogFormVisible = false">取消</el-button>
      <el-button :loading="saveLoading" type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { getRemoteQuery } from '/@/api/pixelAccount'
import { bindPixels as bindSubDomainPixels } from '/@/api/subDomain'
import { bindPixels as bindTopLevelDomainPixels } from '/@/api/topLevelDomain'

defineOptions({
  name: 'BindDomainPixelEdit',
})

const emit = defineEmits(['fetch-data', 'close'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const options = ref<any[]>([])
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const selectLoading = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const domainType = ref<'topLevel' | 'sub'>('topLevel')
const form = reactive<any>({
  id: '',
  name: '',
  pixelIds: [],
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入域名' }],
  pixelIds: [{ required: false, trigger: 'change', message: '请选择绑定像素' }],
})

const handlePixelChange = () => {
  // 可以在这里添加额外的逻辑
}

const showEdit = (row: any, type: 'topLevel' | 'sub' = 'topLevel') => {
  dialogFormVisible.value = true
  domainType.value = type
  nextTick(async () => {
    title.value = type === 'topLevel' ? '绑定像素（一级域名）' : '绑定像素（二级域名）'
    form.id = row.id
    form.name = row.name

    // 初始化 options 为空数组
    options.value = []

    // 处理已绑定的像素ID
    if (row.pixelIds) {
      form.pixelIds = Array.isArray(row.pixelIds)
        ? row.pixelIds.map(String)
        : [String(row.pixelIds)]
    } else {
      form.pixelIds = []
    }

    // 如果已有像素对象数组，直接预填 options（保证显示名称而不是 ID）
    if (row.pixels && Array.isArray(row.pixels) && row.pixels.length > 0) {
      options.value = row.pixels.map((pixel: any) => ({
        id: String(pixel.id),
        pixelId: pixel.pixelId || pixel.id,
        pixelName: pixel.pixelName || pixel.name || '',
      }))

      // 确保 form.pixelIds 与预填的像素ID一致
      const pixelIdsFromPixels = options.value.map((opt) => opt.id)
      if (form.pixelIds.length === 0) {
        form.pixelIds = pixelIdsFromPixels
      } else {
        // 合并，确保不重复
        const existingIds = new Set(form.pixelIds)
        pixelIdsFromPixels.forEach((id) => {
          if (!existingIds.has(id)) {
            form.pixelIds.push(id)
          }
        })
      }
    } else if (form.pixelIds.length > 0) {
      // 如果没有像素对象数组，但有像素ID，需要调用API获取像素详情
      await remoteQueryPixel('')
    }
  })
}

defineExpose({
  showEdit,
})

const close = () => {
  formRef.value?.clearValidate()
  formRef.value?.resetFields()
  Object.assign(form, {
    id: undefined,
    pixelIds: [],
  })
  options.value = []
  emit('close')
  emit('fetch-data')
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        const bindPixelApi =
          domainType.value === 'topLevel' ? bindTopLevelDomainPixels : bindSubDomainPixels
        const { msg }: any = await bindPixelApi({
          id: form.id,
          pixelIds: form.pixelIds,
        })
        await $baseMessage(msg, 'success', 'hey')
        dialogFormVisible.value = false
      } finally {
        saveLoading.value = false
      }
    }
  })
}

const remoteQueryPixel = async (query: string) => {
  selectLoading.value = true
  try {
    const { data } = await getRemoteQuery(query || '')
    const pixelList = (data.list || []).map((item: any) => ({
      ...item,
      id: String(item.id),
    }))

    // 合并已存在的选项，避免重复
    options.value = pixelList
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
}
</style>
