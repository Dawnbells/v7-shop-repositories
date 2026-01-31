<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="600px"
    @close="close"
  >
    <el-form ref="formRef" label-width="100px" :model="form" :rules="rules">
      <el-form-item label="模板名称" prop="name">
        <el-input v-model.trim="form.name" clearable placeholder="请输入模板名称" />
      </el-form-item>
      <el-form-item label="模板描述" prop="description">
        <el-input
          v-model.trim="form.description"
          :autosize="{ minRows: 2, maxRows: 5 }"
          clearable
          placeholder="请输入模板描述"
          type="textarea"
        />
      </el-form-item>
      <el-form-item label="封面图" prop="coverImage">
        <el-input
          v-model.trim="form.coverImage"
          clearable
          placeholder="请输入封面图URL"
          style="margin-bottom: 10px"
        />
        <div v-if="form.coverImage" class="cover-preview">
          <img :alt="form.name" :src="form.coverImage" @error="handleImageError" />
        </div>
      </el-form-item>
      <el-form-item label="共享类型" prop="shareType">
        <el-radio-group v-model="form.shareType">
          <el-radio value="PRIVATE">
            <el-tooltip content="仅自己可见" placement="top">
              <span>私有</span>
            </el-tooltip>
          </el-radio>
          <el-radio value="DEPARTMENT">
            <el-tooltip content="同部门成员可见" placement="top">
              <span>部门共享</span>
            </el-tooltip>
          </el-radio>
          <el-radio v-if="isAdmin" value="COMPANY">
            <el-tooltip content="公司全员可见（仅管理员可设置）" placement="top">
              <span>公司共享</span>
            </el-tooltip>
          </el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogFormVisible = false">取消</el-button>
      <el-button :loading="saveLoading" type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { doEdit } from '/@/api/themeTemplate'
import { useUserStore } from '/@/store/modules/user'

defineOptions({
  name: 'ThemeTemplateEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')

const userStore = useUserStore()
const formRef = ref<any>(null)
const saveLoading = ref<boolean>(false)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const isEdit = ref<boolean>(false)

const form = reactive<any>({
  id: undefined,
  name: '',
  description: '',
  coverImage: '',
  shareType: 'PRIVATE',
})

const rules = reactive<any>({
  name: [{ required: true, trigger: 'blur', message: '模板名称不能为空' }],
  shareType: [{ required: true, trigger: 'change', message: '请选择共享类型' }],
})

const isAdmin = computed(() => {
  // 判断是否为管理员
  const userType = userStore.userType
  return userType === 'ADMIN' || userType === 'SUPER_ADMIN'
})

const showEdit = (row?: any) => {
  dialogFormVisible.value = true
  nextTick(() => {
    isEdit.value = false
    if (row) {
      isEdit.value = true
      title.value = '编辑主题模板'
      Object.assign(form, {
        id: row.id,
        name: row.name,
        description: row.description || '',
        coverImage: row.coverImage || '',
        shareType: row.shareType || 'PRIVATE',
      })
    } else {
      title.value = '新建主题模板'
      Object.assign(form, {
        id: undefined,
        name: '',
        description: '',
        coverImage: '',
        shareType: 'PRIVATE',
      })
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
    name: '',
    description: '',
    coverImage: '',
    shareType: 'PRIVATE',
  })
  isEdit.value = false
  emit('fetch-data')
}

const save = () => {
  formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      try {
        saveLoading.value = true
        const { msg }: any = await doEdit({
          id: form.id,
          name: form.name,
          description: form.description,
          coverImage: form.coverImage,
          shareType: form.shareType,
        })
        $baseMessage(msg || '保存成功', 'success', 'hey')
        dialogFormVisible.value = false
      } catch (error) {
        console.error('保存失败:', error)
      } finally {
        saveLoading.value = false
      }
    }
  })
}

const handleImageError = (e: Event) => {
  const target = e.target as HTMLImageElement
  target.style.display = 'none'
}
</script>

<style lang="scss" scoped>
.cover-preview {
  width: 200px;
  height: 120px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  overflow: hidden;
  background: var(--el-fill-color-light);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}
</style>
