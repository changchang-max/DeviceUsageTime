# ============================================
# 后端 Dockerfile — Spring Boot 3.5 + Java 17
# 多阶段构建：Maven 编译 → JRE 运行
# ============================================

# ---------- 阶段 1：编译 ----------
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# 先复制 pom.xml，利用 Docker 缓存层加速依赖下载
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# 复制源码并编译（跳过测试）
COPY src ./src
RUN mvn clean package -DskipTests -B -q

# ---------- 阶段 2：运行 ----------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 创建非 root 用户运行应用
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 从构建阶段复制 jar
COPY --from=builder /build/target/*.jar app.jar

# 创建日志目录并设置权限
RUN mkdir -p logs && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

# JVM 参数可通过环境变量 JAVA_OPTS 覆盖
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:--Xms256m -Xmx512m} -jar app.jar"]