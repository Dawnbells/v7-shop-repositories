<template>
  <vab-dialog v-model="visible" append-to-body :title="title" width="90%" @close="close">
    <config-center-page
      v-if="visible"
      :key="renderKey"
      config-name="email"
      :department-id="departmentId"
      :page-title="pageTitle"
    />
  </vab-dialog>
</template>

<script lang="ts" setup>
import ConfigCenterPage from '/@/views/system/vabAutoComponents/ConfigCenterPage.vue'

defineOptions({
  name: 'DepartmentConfigDialog',
})

const visible = ref<boolean>(false)
const renderKey = ref<number>(0)
const departmentId = ref<number | undefined>(undefined)
const departmentName = ref<string>('')

const title = computed(() => {
  if (departmentName.value) return `部门级配置（${departmentName.value}）`
  return '部门级配置'
})

const pageTitle = computed(() => title.value)

const show = (row: any) => {
  departmentId.value = row?.id
  departmentName.value = row?.name || ''
  renderKey.value++
  visible.value = true
}

defineExpose({
  show,
})

const close = () => {
  visible.value = false
  departmentId.value = undefined
  departmentName.value = ''
}
</script>


