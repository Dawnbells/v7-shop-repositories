#!/bin/sh
# ============================================================================
# 空卷首启种子脚本（官方 nginx 镜像在启动 nginx 前自动执行 /docker-entrypoint.d/*.sh）
#
# 职责：保证「nginx 不依赖 agent」在第一次启动时也成立——
# active 缺失（全新前端机）时种子化一个内置最小 release（空 map + 默认调优），
# nginx 据此正常启动；agent 首轮全量同步后会用真实 release 替换它。
# 占位证书由 agent 启动时生成（nginx 的变量证书路径在握手期才读文件，启动不依赖）。
# ============================================================================
set -eu

DATA=/data/v7-front

mkdir -p "$DATA/releases" "$DATA/logs"

if [ ! -e "$DATA/active" ]; then
    SEED="$DATA/releases/00000000T000000-seed0000"
    mkdir -p "$SEED/_system"
    cp /opt/v7front-seed/_system/*.conf "$SEED/_system/"
    # 种子 release 故意不写 .content-hash：agent 首轮构建的哈希必然与空基准不同，
    # 走正常的「校验 → 切换 → reload」流程替换种子
    ln -sfn "$SEED" "$DATA/active"
    echo "v7front: 空卷首启，已种子化最小 release: $SEED"
fi
