# Doc-Gen-Service Dockerfile
# 支持多架构构建 (amd64 / arm64)

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 安装 curl 用于健康检查
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 创建模板目录
RUN mkdir -p /app/templates

# 复制预编译的 JAR 文件
COPY target/*.jar app.jar

# 设置环境变量
ENV TEMPLATE_PATH=/app/templates
ENV SERVER_PORT=8081

# 暴露端口
EXPOSE 8081

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
