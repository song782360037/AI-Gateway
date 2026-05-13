# 多阶段构建Dockerfile，优化构建步骤和镜像大小

# 第一阶段：构建前端Vue.js应用
FROM node:22-alpine AS frontend-builder

# 设置工作目录
WORKDIR /app/frontend-vue

# 复制前端依赖文件
COPY ai-gateway-app/frontend-vue/package*.json ./

# 配置 npm 使用淘宝镜像加速依赖下载
RUN npm config set registry https://registry.npmmirror.com

# 安装前端依赖（包含 devDependencies，构建阶段需要 vite 等构建工具）
RUN npm ci

# 复制前端源代码
COPY ai-gateway-app/frontend-vue/ ./

# 构建前端应用
RUN npm run build

# 第二阶段：构建后端Spring Boot应用
FROM maven:3.9-eclipse-temurin-21-alpine AS backend-builder

# 设置工作目录
WORKDIR /app

# 复制 Maven 配置文件，使用阿里云镜像加速依赖下载
COPY settings.xml /root/.m2/settings.xml

# 先复制父 POM 和各子模块 POM（利用 Docker 缓存层）
COPY pom.xml .
COPY ai-gateway-sdk/pom.xml ai-gateway-sdk/
COPY ai-gateway-app/pom.xml ai-gateway-app/

# 下载依赖（利用Docker缓存层，POM 不变时跳过）
RUN mvn dependency:go-offline -B

# 复制 SDK 源代码
COPY ai-gateway-sdk/src ai-gateway-sdk/src

# 复制 App 源代码
COPY ai-gateway-app/src ai-gateway-app/src

# 复制前端构建产物
COPY --from=frontend-builder /app/frontend-vue/dist ai-gateway-app/frontend-vue/dist

# 构建应用（使用 docker profile 跳过前端构建，前端产物已在上一步复制）
RUN mvn clean package -Dmaven.test.skip=true -B -Pdocker -pl ai-gateway-app -am

# 第三阶段：创建最终运行镜像
FROM eclipse-temurin:21-jre-alpine

# 设置工作目录
WORKDIR /app

# 设置时区为上海时间，并安装必要工具（合并 RUN 减少镜像层数）
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata curl && \
    ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# 创建非root用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 从构建阶段复制JAR文件
COPY --from=backend-builder /app/ai-gateway-app/target/*.jar app.jar

# 设置文件权限
RUN chown -R appuser:appgroup /app

# 切换到非root用户
USER appuser

# 暴露应用端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 默认JVM参数，可通过JAVA_OPTS环境变量覆盖
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# 启动应用
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
