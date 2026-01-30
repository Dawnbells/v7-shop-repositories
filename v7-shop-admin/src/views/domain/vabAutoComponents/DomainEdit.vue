<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="800px"
    @close="close"
  >
    <el-form ref="formRef" label-width="80px" :model="form" :rules="rules">
      <el-form-item label="绑定域名" prop="id">
        <el-select
          v-model="form.id"
          clearable
          filterable
          :loading="loading"
          placeholder="请选择绑定域名"
          remote
          :remote-method="querySearchAsync"
          reserve-keyword
          style="width: 100%"
        >
          <el-option
            v-for="item in listOptions"
            :key="item.id"
            :label="item.fullName"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="跳转域名" prop="redirectId">
        <el-select
          v-model="form.redirectId"
          clearable
          disabled
          filterable
          :loading="loading"
          placeholder="请选择跳转域名, 无跳转留空"
          remote
          :remote-method="querySearchAsync"
          reserve-keyword
          style="width: 100%"
        >
          <el-option
            v-for="item in listOptions"
            :key="item.id"
            :label="item.fullName"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
import { doEdit, queryDomains } from '/@/api/domain'

defineOptions({
  name: 'DomainEdit',
})

const emit = defineEmits(['fetch-data'])
const $baseMessage = inject<any>('$baseMessage')
const formRef = ref<any>(null)
const title = ref<string>('')
const loading = ref<boolean>(false)
const dialogFormVisible = ref<boolean>(false)
const saveLoading = ref<boolean>(false)
const listOptions = ref<any[]>([])
const form = reactive<any>({
  id: '',
  redirectId: '',
})
const rules = reactive<any>({
  id: [{ required: true, trigger: 'blur', message: '请输入绑定的域名' }],
})

const showEdit = (row: any) => {
  console.log(row)
  dialogFormVisible.value = true
  nextTick(() => {
    if (row) {
      title.value = '编辑'
      Object.assign(form, row)
      listOptions.value = [{ ...row }]
    } else {
      title.value = '添加'
    }
  })
}

defineExpose({
  showEdit,
})

const close = () => {
  formRef.value.clearValidate()
  formRef.value.resetFields()
  listOptions.value = []
  Object.assign(form, {
    id: undefined,
  })
  emit('fetch-data')
}

const save = () => {
  console.log(form)
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

const querySearchAsync = (queryString: string, cb: (arg: any) => void) => {
  queryDomains(queryString)
    .then((res) => {
      const list = res.data.list
      listOptions.value = list
    })
    .catch((error) => {
      console.error(error)
      listOptions.value = []
    })
}

const handleSelect = (item: Record<string, any>) => {
  console.log(item)
}
</script>
