FROM openjdk:17-jdk-slim

# 应用 jar 包
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# 定义挂载目录（用于映射 resources）
VOLUME /app/resources

# 启动参数：让 Spring Boot 读取挂载的配置
ENTRYPOINT ["java","-jar","/app.jar","--spring.config.additional-location=file:/app/resources/"]
