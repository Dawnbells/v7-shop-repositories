<template>
  <email-configuration-page
    v-if="departmentInfoLoaded"
    :department-id="departmentId"
    :page-title="title"
  />
</template>

<script lang="ts" setup>
import { getConfigCenterDepartmentInfo } from '/@/api/configCenter'
import EmailConfigurationPage from '/@/views/system/vabAutoComponents/EmailConfigurationPage.vue'

defineOptions({
  name: 'EmailConfigCenter',
})

const departmentInfo = ref<any>(null)
const departmentInfoLoaded = ref(false)

const departmentId = computed(() => {
  const id = departmentInfo.value?.id
  return isNaN(Number(id)) ? undefined : Number(id)
})

const title = computed(() => {
  if (!departmentInfo.value) return '公司配置'
  const name = departmentInfo.value?.name || departmentInfo.value?.departmentName
  return name ? `部门配置（${name}）` : '部门配置'
})

const fetchDepartmentInfo = async () => {
  try {
    const res: any = await getConfigCenterDepartmentInfo()
    departmentInfo.value = res?.data ?? null
  } catch {
    departmentInfo.value = null
  } finally {
    departmentInfoLoaded.value = true
  }
}

onBeforeMount(() => {
  fetchDepartmentInfo()
})
</script>
