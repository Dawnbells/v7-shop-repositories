<template>
  <vab-dialog v-model="dialogFormVisible" :title="title" width="60%" @close="close">
    <el-form ref="formRef" label-width="100px" :model="form" :rules="rules">
      <el-form-item label="文章名字" prop="name">
        <el-input v-model.trim="form.name" clearable />
      </el-form-item>
      <el-form-item label="文章标题" prop="title">
        <el-input v-model.trim="form.title" clearable />
      </el-form-item>
      <el-form-item label="文章语言" prop="languageId">
        <el-select
          v-model="form.languageId"
          filterable
          :loading="languageLoading"
          remote
          :remote-method="remoteQueryLanguage"
          style="width: 100%"
          value-key="id"
        >
          <el-option v-for="item in options" :key="item.id" :label="item.cname" :value="item.id">
            <span style="float: left">{{ item.cname }}</span>
            <span style="float: right; font-size: 13px; color: var(--el-text-color-secondary)">
              {{ item.code }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="文章类型" prop="articleType">
        <el-select v-model="form.articleType" placeholder="请选择文章类型" style="width: 100%">
          <el-option label="协议" value="PROTOCOL" />
          <el-option label="常规" value="NORMAL" />
        </el-select>
      </el-form-item>
      <el-form-item label="文章描述" prop="description">
        <el-input v-model.trim="form.description" clearable />
      </el-form-item>
      <!-- INSERT_YOUR_CODE -->
      <!-- 文章内容支持 {{通用占位符}} 和 {{i18n_国际化占位符}} 两种方式，灰色小提示，移到 editor 上方 -->
      <!-- 灰色提示，样式适配 -->
      <div style="margin-bottom: 8px; font-size: 13px; color: #909399">
        文章内容支持
        <code>{{ '\{\{通用占位符\}\}' }}</code>
        和
        <code>{{ '\{\{i18n_国际化占位符\}\}' }}</code>
        两种占位符方式
      </div>
      <el-form-item label-position="top" prop="content">
        <product-wang-editor v-model="form.content" :is-product="false" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { getRemoteQueryLanguage } from '~/src/api/language'
import { doEdit } from '/@/api/article'

defineOptions({
  name: 'ArticleEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const languageLoading = ref<boolean>(false)
const dialogFormVisible = ref<boolean>(false)
const options = ref<any[]>([])
const isEdit = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const form = reactive<any>({
  name: '',
  title: '',
  content: '',
  articleType: 'PROTOCOL',
  languageId: '',
})
const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '请输入文章名字' }],
  title: [{ required: true, trigger: 'blur', message: '请输入文章标题' }],
  content: [{ required: true, trigger: 'blur', message: '请输入文章内容' }],
  description: [{ required: true, trigger: 'blur', message: '请输入文章描述' }],
  articleType: [{ required: true, trigger: 'blur', message: '请选择文章类型' }],
  languageId: [{ required: true, trigger: 'blur', message: '请选择文章语言' }],
})

const showEdit = (row: any) => {
  dialogFormVisible.value = true
  remoteQueryLanguage('')
  nextTick(() => {
    isEdit.value = false
    if (row) {
      isEdit.value = true
      title.value = '编辑文章'
      Object.assign(form, row)
      form.languageId = row.language.id
    } else {
      title.value = '添加文章'
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
    id: undefined,
  })
  emit('fetch-data')
}

const save = () => {
  formRef.value.validate(async (valid: any) => {
    if (valid) {
      try {
        saveLoading.value = true
        const { msg }: any = await doEdit(form)
        await $baseMessage(msg, 'success', 'hey')
        dialogFormVisible.value = false
      } finally {
        saveLoading.value = false
      }
    }
  })
}

const remoteQueryLanguage = async (query: string) => {
  languageLoading.value = true
  try {
    const { data } = await getRemoteQueryLanguage(query)
    options.value = data.list
    if (data.total > 0 && !form.languageId) {
      form.languageId = data.list[0].id
    }
  } finally {
    languageLoading.value = false
  }
}
</script>

<style>
/* 设置表格内容不换行，并根据内容自动调整宽度 */
/* .el-table th,
.el-table td {
  white-space: nowrap;
} */
.v7-shop-upload-size {
  width: 100px;
  height: 100px;
}
.uploadTipDesc {
  margin-top: 12px;
  margin-bottom: 0;
  color: #7a8499;
}
.sku-code {
  float: left;
}

.sku-item-name {
  float: right;
  margin-left: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.image-wrapper {
  position: relative;
  display: inline-block;
}

.delete-icon {
  position: absolute;
  top: 5px;
  right: 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 5px;
  color: white;
  cursor: pointer;
  background-color: rgba(0, 0, 0, 0.5);
  border-radius: 50%;
}

.delete-icon:hover {
  background-color: rgba(70, 33, 33, 0.8);
}

.add-image-button {
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border: 1px dashed #d9d9d9;
}

.add-image-button .el-icon {
  font-size: 28px;
  color: #999;
}
</style>
