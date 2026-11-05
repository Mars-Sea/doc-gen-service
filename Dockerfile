# Doc-Gen-Service Dockerfile
# 支持多架构构建 (amd64 / arm64)

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 安装 curl 用于健康检查
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 创建模板目录与堆快照目录
RUN mkdir -p /app/templates /app/dumps

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
# JVM 参数说明：
# - MaxRAMPercentage=75：堆按容器内存上限的 75% 算。不设容器 mem_limit 时 JVM 会按宿主机总内存推算，
#   容器里看到的宿主内存可能远大于实际可用量，堆会开得过大，最后被 cgroup 杀掉。
# - HeapDumpOnOutOfMemoryError + HeapDumpPath：OOM 时落堆快照，否则「哪个请求、哪份数据撑爆的」无从查起。
#   目录需在 docker-compose 里挂载到宿主机，否则容器重启后快照随之消失。
# 不设 ExitOnOutOfMemoryError：它会在 OOM 抛出的那一刻直接终止进程，ExcelService 里的
#   catch (OutOfMemoryError) 与全局异常处理都来不及执行，客户端只能看到连接被重置而不是 503 中文提示。
#   真进入持续 OOM 的半死状态时，由容器的 restart 策略兜底。
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/app/dumps", \
    "-jar", "app.jar"]
