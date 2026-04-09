<template>
  <order-manager :is-contact="true" />
</template>

<script lang="ts" setup>
import OrderManager from './OrderManager.vue'
defineOptions({ name: 'OrderContact' })
</script>
