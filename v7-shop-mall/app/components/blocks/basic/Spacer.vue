<script setup lang="ts">
interface Props {
  height?: string
}

const props = withDefaults(defineProps<Props>(), {
  height: '20px',
})

const spacerStyle = computed(() => ({
  width: '100%',
  height: props.height,
}))
</script>

<template>
  <div class="block-spacer" :style="spacerStyle" />
</template>

<style scoped>
.block-spacer {
  flex-shrink: 0;
}
</style>
