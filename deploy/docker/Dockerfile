# District 360 运行镜像：只含 JRE 17 + 启动脚本。
# jar、配置文件、前端页面都不打进镜像，由宿主机目录映射进来（见 docker-compose.yml），
# 升级时替换宿主机上的文件再重启容器即可，不用重新做镜像。
FROM eclipse-temurin:17-jre-jammy

ENV TZ=Asia/Dubai \
    LANG=C.UTF-8 \
    SPRING_PROFILES_ACTIVE=prod \
    D360_PORT=8080 \
    D360_CONTEXT_PATH=/district360 \
    D360_STATIC_LOCATIONS=file:/app/static/ \
    D360_JAR=/app/jar/district360.jar \
    JAVA_OPTS="-Xms512m -Xmx2g"

# /app 是工作目录：Spring Boot 会自动读取 /app/config/application-prod.yml
WORKDIR /app
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh && mkdir -p /app/jar /app/config /app/static /app/logs

EXPOSE 8080

# /api/v1/meta 会查统计库：数据库连不上也会显示 unhealthy
HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=3 \
    CMD curl -fsS -o /dev/null "http://127.0.0.1:${D360_PORT}${D360_CONTEXT_PATH}/api/v1/meta" || exit 1

ENTRYPOINT ["/entrypoint.sh"]
