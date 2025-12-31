# 简单 Dockerfile - 使用预编译的 JAR 文件
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 创建模板目录
RUN mkdir -p /app/templates

# 复制预编译的 JAR 文件
COPY target/*.jar app.jar

# 设置环境变量
ENV TEMPLATE_PATH=/app/templates
ENV SERVER_PORT=8081

# 暴露端口
EXPOSE 8081

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
