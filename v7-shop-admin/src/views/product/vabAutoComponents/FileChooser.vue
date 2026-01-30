<template>
  <vab-dialog
    v-model="dialogFormVisible"
    append-to-body
    :title="title"
    width="80%"
    :z-index="3000"
    @close="close"
  >
    <div>
      <explorer ref="explorerRef" @choose="handleConfirm" />
    </div>

    <template #footer>
      <el-button type="primary" @click="handleConfirm">确认</el-button>
    </template>
  </vab-dialog>
</template>

<script lang="ts" setup>
defineOptions({
  name: 'FileChooser',
})

const emit = defineEmits(['on-selected'])
const explorerRef = ref<any>(null)
const title = ref<string>('')
const dialogFormVisible = ref<boolean>(false)
const onChooseListener = ref<((selected: []) => void) | null>(null)

const choose = async () => {
  dialogFormVisible.value = true
  nextTick(() => {
    title.value = '资源选择器'
    explorerRef.value.reset()
  })
  return new Promise((resolve) => {
    onChooseListener.value = (items: any) => {
      resolve(items)
      onChooseListener.value = null
    }
  })
}

const handleConfirm = () => {
  const selected = explorerRef.value.getSelectedFile()
  if (selected && selected.length > 0) {
    emit('on-selected', selected)
    onChooseListener.value?.(selected)
    dialogFormVisible.value = false
  }
}

defineExpose({
  choose,
})

const close = () => {}
</script>
