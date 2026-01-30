<template>
  <config-center-page
    config-name="email"
    :department-id="departmentId"
    :page-title="title"
    :page-description="subtitle"
    @refresh="fetchDepartmentInfo"
  />
</template>

<script lang="ts" setup>
import { getConfigCenterDepartmentInfo } from '/@/api/configCenter'
import ConfigCenterPage from '/@/views/system/vabAutoComponents/ConfigCenterPage.vue'

defineOptions({
  name: 'EmailConfigCenter',
})

const departmentInfo = ref<any>(null)

const departmentId = computed(() => {
  const id = departmentInfo.value?.id
  return isNaN(Number(id)) ? undefined : Number(id)
})

const title = computed(() => {
  if (!departmentInfo.value) return '公司配置'
  const name = departmentInfo.value?.name || departmentInfo.value?.departmentName
  return name ? `部门配置（${name}）` : '部门配置'
})

const subtitle = computed(() => {
  if (!departmentInfo.value) return '系统级配置'
  return '部门级配置'
})

const fetchDepartmentInfo = async () => {
  try {
    const res: any = await getConfigCenterDepartmentInfo()
    departmentInfo.value = res?.data ?? null
  } catch {
    departmentInfo.value = null
  }
}

onBeforeMount(() => {
  fetchDepartmentInfo()
})
</script>
