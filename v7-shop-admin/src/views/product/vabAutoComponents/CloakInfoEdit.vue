<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="600px"
    @close="close"
  >
    <el-form ref="formRef" label-width="120px" :model="form" :rules="rules">
      <el-form-item label="规则名称" prop="name">
        <el-input v-model.trim="form.name" clearable :disabled="isEdit" />
      </el-form-item>
      <el-form-item label="显示商品" prop="spuId">
        <el-select
          v-model="form.spuId"
          filterable
          :loading="spuLoading"
          remote
          :remote-method="remoteQuerySpu"
          style="width: 100%"
        >
          <el-option v-for="item in spuOptions" :key="item.id" :label="item.name" :value="item.id">
            <span style="float: left">{{ item.name }}</span>
            <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
              {{ item.code }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="国家代码(包含)" prop="includeCountryCode">
        <el-input v-model.trim="form.includeCountryCode" clearable />
      </el-form-item>
      <el-form-item label="国家代码(排除)" prop="excludeCountryCode">
        <el-input v-model.trim="form.excludeCountryCode" clearable />
      </el-form-item>
      <el-form-item label="爬虫类型(包含)" prop="includeCrawler">
        <el-select
          v-model="form.includeCrawler"
          clearable
          multiple
          placeholder="请选择爬虫类型"
          style="width: 100%"
        >
          <el-option label="FacebookBot" value="FacebookBot" />
          <el-option label="GoogleBot" value="GoogleBot" />
          <el-option label="TikTokBot" value="TikTokBot" />
        </el-select>
      </el-form-item>
      <el-form-item label="爬虫类型(排除)" prop="excludeCrawler">
        <el-select
          v-model="form.excludeCrawler"
          clearable
          multiple
          placeholder="请选择爬虫类型"
          style="width: 100%"
        >
          <el-option label="All" value="All" />
          <el-option label="FacebookBot" value="FacebookBot" />
          <el-option label="GoogleBot" value="GoogleBot" />
          <el-option label="TikTokBot" value="TikTokBot" />
          <el-option label="Other" value="OtherBot" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { getRemoteQuery } from '/@/api/spu'

defineOptions({
  name: 'CloakInfoEdit',
})

const emit = defineEmits(['update-cloak-infos'])
const formRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const spuLoading = ref<boolean>(false)
const spuOptions = ref<any[]>([])
const isEdit = ref<boolean>(false)
const form = reactive<any>({
  name: '',
  includeCountryCode: '',
  excludeCountryCode: '',
  includeCrawler: [],
  excludeCrawler: [],
  spuId: '',
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入斗篷规则名称' }],
  spuId: [{ required: true, trigger: 'blur', message: '请选择显示商品' }],
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    if (row) {
      title.value = '编辑斗篷规则'
      isEdit.value = true
      Object.assign(form, row)
      if (row.spuId) {
        form.spuId = row.spuId
        remoteQuerySpu(row.spuId)
      }
    } else {
      title.value = '添加斗篷规则'
    }
  })
}

defineExpose({
  showEdit,
})

const close = () => {
  formRef.value.clearValidate()
  formRef.value.resetFields()
  spuOptions.value = []
  Object.assign(form, {
    id: undefined,
    spuId: '',
    includeCountryCode: '',
    excludeCountryCode: '',
    includeCrawler: [],
    excludeCrawler: [],
  })
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        emit('update-cloak-infos', { ...form })
        dialogFormVisible.value = false
      } finally {
        saveLoading.value = false
      }
    }
  })
}

const remoteQuerySpu = async (query: string) => {
  spuLoading.value = true
  try {
    const { data } = await getRemoteQuery(query)
    spuOptions.value = data.list
  } finally {
    spuLoading.value = false
  }
}
</script>
