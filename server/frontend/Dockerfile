# ============================================
# 前端 Dockerfile — Vue3 + Vite + Nginx
# 多阶段构建：Node 编译 → Nginx 运行
# 启动时通过 envsubst 动态替换 Nginx 配置中的环境变量
#
# 环境变量：
#   DOMAIN           — 域名（默认 _ 表示匹配所有 Host），用于 Nginx server_name
#   BACKEND_HOST     — 后端地址（默认 backend:8080），Nginx 反向代理目标
#   BACKEND_UPSTREAM — 后端主机名（默认 backend），供 resolver 解析
# ============================================

# ---------- 阶段 1：编译 ----------
FROM node:22-alpine AS builder

WORKDIR /build

# 先复制依赖文件，利用 Docker 缓存层
COPY package.json package-lock.json ./
RUN npm ci

# 复制源码
COPY . .

# 构建生产版本（.env.production 中 VITE_API_BASE_URL=/api，走同域 Nginx 反代）
RUN npm run build

# ---------- 阶段 2：运行 ----------
FROM nginx:1.27-alpine

# 时区
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 复制 Nginx 配置模板（nginx 镜像自带 envsubst 逻辑，此处由 CMD 显式处理）
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

# 复制构建产物
COPY --from=builder /build/dist /usr/share/nginx/html

# Nginx 官方镜像已内置 /docker-entrypoint.d/20-envsubst-on-templates.sh，
# 会读取 /etc/nginx/templates/*.template 并按环境变量替换后写入 conf.d。
# 需要显式声明参与替换的变量白名单，避免误替换 Nginx 自身变量。
ENV NGINX_ENVSUBST_TEMPLATE_SUFFIX=.template \
    NGINX_ENVSUBST_OUTPUT_DIR=/etc/nginx/conf.d \
    DOMAIN=_ \
    BACKEND_HOST=backend:8080 \
    BACKEND_UPSTREAM=backend

# entrypoint 脚本只替换白名单变量
RUN printf '#!/bin/sh\nset -e\nenvsubst '"'"'${DOMAIN} ${BACKEND_HOST} ${BACKEND_UPSTREAM}'"'"' \\\n  < /etc/nginx/templates/default.conf.template \\\n  > /etc/nginx/conf.d/default.conf\nexec nginx -g "daemon off;"\n' \
    > /docker-entrypoint.d/50-custom.sh && chmod +x /docker-entrypoint.d/50-custom.sh

# 不执行官方 20-envsubst（其会替换全部变量），改用上面的白名单脚本
RUN chmod -x /docker-entrypoint.d/20-envsubst-on-templates.sh 2>/dev/null || true

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1