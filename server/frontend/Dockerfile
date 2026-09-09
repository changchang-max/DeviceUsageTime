# ============================================
# 前端 Dockerfile — Vue3 + Vite + Nginx
# 多阶段构建：Node 编译 → Nginx 运行
# 启动时通过 envsubst 动态替换 Nginx 配置中的环境变量
#
# 环境变量：
#   DOMAIN       — 域名（默认 primordialcode.xyz），用于 Nginx server_name
#   BACKEND_HOST — 后端地址（默认 localhost:8080），Nginx 反向代理目标
# ============================================

# ---------- 阶段 1：编译 ----------
FROM node:22-alpine AS builder

WORKDIR /build

# 先复制依赖文件，利用 Docker 缓存层
COPY package.json package-lock.json ./
RUN npm ci

# 复制源码
COPY . .

# 构建生产版本
RUN npm run build

# ---------- 阶段 2：运行 ----------
FROM nginx:1.27-alpine

# 安装 envsubst 依赖（alpine 中 gettext 提供 envsubst）
RUN apk add --no-cache gettext

# 复制 Nginx 配置模板
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

# 复制构建产物
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80

# 启动脚本：先用 envsubst 替换模板中的环境变量，再启动 Nginx
# ${DOMAIN} 替换为实际域名，${BACKEND_HOST} 替换为后端地址
CMD ["/bin/sh", "-c", "envsubst '${DOMAIN} ${BACKEND_HOST}' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'"]