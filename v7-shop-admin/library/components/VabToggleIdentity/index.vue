<template>
  <div style="display: flex; gap: 8px; align-items: center">
    <vab-icon v-if="currentViewMode === 'PERSONAL'" icon="user-3-line" @click="toggleRole" />
    <vab-icon v-else icon="group-line" @click="toggleRole" />
  </div>
</template>

<script setup>
import { getViewMode, switchViewMode } from '/@/api/user'

const currentViewMode = ref('TEAM') // self | leader

const toggleRole = () => {
  switchViewMode(currentViewMode.value === 'PERSONAL' ? 'team' : 'personal')
  currentViewMode.value = currentViewMode.value === 'PERSONAL' ? 'TEAM' : 'PERSONAL'
  $pub('reload-router-view')
}

onBeforeMount(() => {
  getViewMode().then((res) => {
    console.log('getViewMode', res)
    currentViewMode.value = res.data
  })
})
</script>
