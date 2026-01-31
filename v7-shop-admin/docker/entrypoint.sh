#!/bin/sh
# Docker entrypoint 脚本
# 用于在容器启动时将系统环境变量注入到前端应用中

# 替换 index.html 中的占位符为实际环境变量值
# 如果环境变量未设置，则保留占位符（前端会 fallback 到构建时的 .env 值）
INDEX_FILE="/usr/share/nginx/html/index.html"

if [ -f "$INDEX_FILE" ]; then
  # VITE_NUXT_BUILDER_URL
  if [ -n "$VITE_NUXT_BUILDER_URL" ]; then
    sed -i "s|__VITE_NUXT_BUILDER_URL__|${VITE_NUXT_BUILDER_URL}|g" "$INDEX_FILE"
  fi

  # VITE_API_BASE_URL
  if [ -n "$VITE_API_BASE_URL" ]; then
    sed -i "s|__VITE_API_BASE_URL__|${VITE_API_BASE_URL}|g" "$INDEX_FILE"
  fi

  # VITE_IMAGE_BASE_URL
  if [ -n "$VITE_IMAGE_BASE_URL" ]; then
    sed -i "s|__VITE_IMAGE_BASE_URL__|${VITE_IMAGE_BASE_URL}|g" "$INDEX_FILE"
  fi

  echo "[entrypoint] Environment variables injected into index.html"
else
  echo "[entrypoint] Warning: $INDEX_FILE not found"
fi

# 启动 nginx
exec nginx -g 'daemon off;'
