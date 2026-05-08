# 多阶段构建Dockerfile，优化构建步骤和镜像大小

# 第一阶段：构建前端Vue.js应用
FROM node:23.11.0-alpine AS frontend-builder

# 设置工作目录
WORKDIR /app/frontend-vue

# 复制前端依赖文件
COPY frontend-vue/package*.json ./

# 安装前端依赖（包含 devDependencies，构建阶段需要 vite 等构建工具）
RUN npm ci

# 复制前端源代码
COPY frontend-vue/ ./

# 构建前端应用
RUN npm run build

# 第二阶段：构建后端Spring Boot应用
FROM eclipse-temurin:21-jdk-alpine AS backend-builder

# 设置工作目录
WORKDIR /app

# 安装Maven
RUN apk add --no-cache maven

# 复制Maven配置文件
COPY pom.xml .

# 下载依赖（利用Docker缓存层）
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 复制前端构建产物
COPY --from=frontend-builder /app/frontend-vue/dist ./frontend-vue/dist

# 构建应用（使用 docker profile 跳过前端构建，前端产物已在上一步复制）
RUN mvn clean package -DskipTests -B -Pdocker

# 第三阶段：创建最终运行镜像
FROM eclipse-temurin:21-jre-alpine

# 设置工作目录
WORKDIR /app

# 创建非root用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 安装必要工具
RUN apk add --no-cache curl

# 从构建阶段复制JAR文件
COPY --from=backend-builder /app/target/*.jar app.jar

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
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]