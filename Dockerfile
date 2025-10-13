FROM openjdk:17-jdk-slim

WORKDIR /app

# 应用 jar 包
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# 创建resources目录
RUN mkdir -p /app/resources

# 暴露端口
EXPOSE 8888

# 启动参数：优先使用外部配置（使用相对路径或绝对路径）
ENTRYPOINT ["java","-jar","app.jar","--spring.config.location=file:/app/resources/application.yml,classpath:/application.yml"]
