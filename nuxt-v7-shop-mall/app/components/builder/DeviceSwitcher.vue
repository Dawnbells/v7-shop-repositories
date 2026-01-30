<script setup lang="ts">
/**
 * 设备切换器
 */

import type { DeviceType } from "~/types/builder";
import { BREAKPOINTS } from "~/constants";

const props = defineProps<{
  currentDevice: DeviceType;
}>();

const emit = defineEmits<{
  change: [device: DeviceType];
}>();

const devices: Array<{ type: DeviceType; icon: string }> = [
  { type: "mobile", icon: "i-carbon-phone" },
  { type: "tablet", icon: "i-carbon-tablet" },
  { type: "pc", icon: "i-carbon-laptop" },
];

function handleClick(device: DeviceType) {
  emit("change", device);
}
</script>

<template>
  <div class="device-switcher">
    <button
      v-for="device in devices"
      :key="device.type"
      class="device-btn"
      :class="{ active: currentDevice === device.type }"
      :title="BREAKPOINTS[device.type].label"
      @click="handleClick(device.type)"
    >
      <span :class="device.icon"></span>
    </button>
  </div>
</template>

<style scoped>
.device-switcher {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  background-color: #0f172a;
  border-radius: 8px;
}

.device-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  font-size: 18px;
  color: #64748b;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.device-btn:hover {
  color: #94a3b8;
  background-color: #334155;
}

.device-btn.active {
  color: #3b82f6;
  background-color: #1e3a5f;
}
</style>
