# syntax=docker/dockerfile:1
# ============================================================================
# v7front-agent 镜像：两阶段构建
#   1. golang 阶段编译静态二进制（CGO_ENABLED=0，无任何动态依赖）
#   2. 运行阶段基于与数据面**完全相同**的 nginx 镜像——
#      保证 agent 里 `nginx -t` 的校验行为与真正跑流量的 nginx 一字不差
# ============================================================================
ARG NGINX_IMAGE=nginx:1.29-alpine

FROM golang:1.23-alpine AS builder
WORKDIR /src
COPY go.mod ./
COPY *.go ./
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /v7front-agent .

FROM ${NGINX_IMAGE}
COPY --from=builder /v7front-agent /usr/local/bin/v7front-agent
# 覆盖 nginx 镜像的默认入口：本容器只跑 agent（nginx 由另一个容器跑）
ENTRYPOINT ["/usr/local/bin/v7front-agent"]
