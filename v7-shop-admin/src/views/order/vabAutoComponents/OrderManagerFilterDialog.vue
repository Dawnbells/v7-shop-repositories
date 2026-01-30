<template>
  <el-dialog v-model="outerVisible" width="80%" modal center>
    <order-manager />
  </el-dialog>
</template>

<script lang="ts" setup>
import OrderManager from './../OrderManager.vue'

const outerVisible = ref<boolean>(false)
const showEdit = (row: Record<string, any>, params: Record<string, any>) => {
  outerVisible.value = true
}

defineExpose({
  showEdit,
})
</script>
