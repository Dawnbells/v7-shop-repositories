/**
 * 设备检测 composable
 * 用于前端自动检测设备类型，应用响应式样式
 */

import type { DeviceType } from "~/types/builder";
import { BREAKPOINTS } from "~/constants";

export function useDeviceDetect() {
  const device = ref<DeviceType>("mobile");

  // 根据窗口宽度判断设备类型
  function updateDevice() {
    if (!import.meta.client) return;

    const width = window.innerWidth;

    if (width >= BREAKPOINTS.pc.minWidth) {
      device.value = "pc";
    } else if (width >= BREAKPOINTS.tablet.minWidth) {
      device.value = "tablet";
    } else {
      device.value = "mobile";
    }
  }

  // 客户端初始化
  if (import.meta.client) {
    // 立即检测一次
    updateDevice();

    // 监听窗口大小变化
    onMounted(() => {
      window.addEventListener("resize", updateDevice);
    });

    onUnmounted(() => {
      window.removeEventListener("resize", updateDevice);
    });
  }

  // SSR 时根据 User-Agent 判断（可选）
  if (import.meta.server) {
    const headers = useRequestHeaders(["user-agent"]);
    const userAgent = headers["user-agent"] || "";

    // 简单的移动设备检测
    const isMobile = /mobile|android|iphone|ipad|ipod|blackberry|iemobile|opera mini/i.test(
      userAgent.toLowerCase()
    );
    const isTablet = /ipad|tablet|playbook|silk/i.test(userAgent.toLowerCase());

    if (isTablet) {
      device.value = "tablet";
    } else if (isMobile) {
      device.value = "mobile";
    } else {
      device.value = "pc";
    }
  }

  return {
    device: readonly(device),
    updateDevice,
  };
}
