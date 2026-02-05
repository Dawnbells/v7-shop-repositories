<script setup lang="ts">
/**
 * AppImage - 通用图片组件
 *
 * 功能：
 * - 自动拼接图片基础链接（http/https 开头的不处理）
 * - 加载失败自动换前缀降级
 * - 支持自定义降级图片
 * - 懒加载支持
 */

interface Props {
  src: string; // 图片路径（相对或绝对）
  alt?: string; // 替代文本
  fallback?: string; // 自定义降级图片（优先级高于换前缀）
  width?: string | number;
  height?: string | number;
  lazy?: boolean; // 是否懒加载
}

const props = withDefaults(defineProps<Props>(), {
  alt: "",
  fallback: "",
  lazy: true,
});

const config = useRuntimeConfig();

// 是否是完整 URL
const isFullUrl = (url: string) =>
  url.startsWith("http://") || url.startsWith("https://");

// 构建完整 URL
function buildUrl(path: string, baseUrl: string): string {
  if (!path || !baseUrl) return path;
  const cleanBase = baseUrl.endsWith("/") ? baseUrl.slice(0, -1) : baseUrl;
  const cleanPath = path.startsWith("/") ? path : `/${path}`;
  return `${cleanBase}${cleanPath}`;
}

// 主图片 URL
const primaryUrl = computed(() => {
  const path = props.src;
  if (!path) return "";
  if (isFullUrl(path)) return path;

  const baseUrl = config.public.imageBaseUrl as string;
  return baseUrl ? buildUrl(path, baseUrl) : path;
});

// 降级图片 URL（换前缀）
const fallbackUrl = computed(() => {
  const path = props.src;
  if (!path) return "";

  // 如果有自定义降级图片，优先使用
  if (props.fallback) return props.fallback;

  // 完整 URL 无法换前缀降级
  if (isFullUrl(path)) return "";

  const fallbackBaseUrl = config.public.imageFallbackUrl as string;
  return fallbackBaseUrl ? buildUrl(path, fallbackBaseUrl) : "";
});

// 当前显示的 URL
const useFallback = ref(false);
const displayUrl = computed(() => {
  if (useFallback.value && fallbackUrl.value) {
    return fallbackUrl.value;
  }
  return primaryUrl.value;
});

// 加载失败处理 - 切换到降级 URL
function handleError() {
  if (!useFallback.value && fallbackUrl.value) {
    useFallback.value = true;
  }
}

// src 变化时重置状态
watch(
  () => props.src,
  () => {
    useFallback.value = false;
  }
);
</script>

<template>
  <img
    v-if="displayUrl"
    :src="displayUrl"
    :alt="alt"
    :width="width"
    :height="height"
    :loading="lazy ? 'lazy' : undefined"
    @error="handleError"
  />
</template>
