/**
 * 设备检测 composable
 * 用于前端自动检测设备类型，应用响应式样式
 *
 * 使用 useState 确保 SSR 设备检测结果在客户端水合时保持一致，
 * 避免水合不匹配导致的样式闪烁
 */

import type { DeviceType } from "~/types/builder";
import { BREAKPOINTS } from "~/constants";

/**
 * 根据 User-Agent 判断设备类型
 */
function detectDeviceFromUA(userAgent: string): DeviceType {
  const ua = userAgent.toLowerCase();

  // 平板检测（需要在移动设备之前，因为 iPad 也包含 mobile）
  const isTablet = /ipad|tablet|playbook|silk/i.test(ua);
  if (isTablet) return "tablet";

  // 移动设备检测
  const isMobile = /mobile|android|iphone|ipod|blackberry|iemobile|opera mini/i.test(ua);
  if (isMobile) return "mobile";

  // 默认为 PC
  return "pc";
}

/**
 * 根据窗口宽度判断设备类型
 */
function detectDeviceFromWidth(width: number): DeviceType {
  const pcMinWidth = BREAKPOINTS.pc.minWidth ?? 1024;
  const tabletMinWidth = BREAKPOINTS.tablet.minWidth ?? 768;

  if (width >= pcMinWidth) {
    return "pc";
  } else if (width >= tabletMinWidth) {
    return "tablet";
  } else {
    return "mobile";
  }
}

export function useDeviceDetect() {
  // 使用 useState 确保 SSR 和客户端水合时设备类型一致
  const device = useState<DeviceType>("device-detect", () => {
    // SSR 时根据 User-Agent 判断
    if (import.meta.server) {
      const headers = useRequestHeaders(["user-agent"]);
      const userAgent = headers["user-agent"] || "";
      return detectDeviceFromUA(userAgent);
    }
    // 客户端初始化时使用 SSR 传递的值（useState 会自动处理）
    return "pc";
  });

  // 根据窗口宽度更新设备类型
  function updateDevice() {
    if (!import.meta.client) return;
    device.value = detectDeviceFromWidth(window.innerWidth);
  }

  // 客户端：监听窗口大小变化
  if (import.meta.client) {
    onMounted(() => {
      // 挂载后根据实际窗口宽度更新（可能与 UA 判断不同）
      updateDevice();
      window.addEventListener("resize", updateDevice);
    });

    onUnmounted(() => {
      window.removeEventListener("resize", updateDevice);
    });
  }

  return {
    device: readonly(device),
    updateDevice,
  };
}
