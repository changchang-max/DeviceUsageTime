# ============================================
# 后端 Dockerfile — Spring Boot 3.5 + Java 17
# 多阶段构建：Maven 编译 → JRE 运行
#
# 说明：
#   src/main/resources/.env 必须进入构建上下文（见 .dockerignore），
#   spring-dotenv 依赖它来解析 ${MYSQL_USER} / ${MYSQL_PASSWORD} 等占位符。
#   容器运行时，docker compose 注入的环境变量优先级更高，会覆盖 .env 中的值。
# ============================================

# ---------- 阶段 1：编译 ----------
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# 先复制 pom.xml，利用 Docker 缓存层加速依赖下载
COPY pom.xml .
RUN mvn dependency:go-offline -B -q || true

# 复制源码并编译（跳过测试）
COPY src ./src

# .env 以 CRLF 保存时会干扰 shell 解析，先转 LF；
# 该文件只在 builder 阶段用于解析占位符，不会进入运行镜像。
RUN if [ -f src/main/resources/.env ]; then \
        sed -i 's/\r$//' src/main/resources/.env; \
    fi \
    && mvn clean package -DskipTests -B -q

# ---------- 阶段 2：运行 ----------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 时区与应用统一为 Asia/Shanghai
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 创建非 root 用户运行应用
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 从构建阶段复制 jar
COPY --from=builder /build/target/*.jar app.jar

# 运行期凭据一律由 docker compose / docker run 的环境变量注入，
# 因此从镜像内移除 .env，避免密钥随镜像分发。
RUN apk add --no-cache zip >/dev/null \
    && set -eux; \
    rm -rf /tmp/bo; mkdir -p /tmp/bo; cd /tmp/bo; \
    unzip -q -o /app/app.jar; \
    rm -f BOOT-INF/classes/.env; \
    rm -f /app/app.jar; \
    zip -q -r -0 /app/app.jar .; \
    cd /; rm -rf /tmp/bo; \
    apk del zip >/dev/null

# 创建日志目录并设置权限（logs/backend.log 由 application.yaml 指定）
RUN mkdir -p logs && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

# JVM 参数可通过环境变量 JAVA_OPTS 覆盖
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# 健康检查调用 Spring Boot Actuator（application.yaml 已暴露 health）
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]