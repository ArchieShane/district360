#!/bin/sh
set -e

if [ ! -f "$D360_JAR" ]; then
  echo "[district360] 找不到 $D360_JAR：请把 district360.jar 放到宿主机映射的 jar/ 目录" >&2
  exit 1
fi
if [ "$SPRING_PROFILES_ACTIVE" = "prod" ] && [ ! -f /app/config/application-prod.yml ]; then
  echo "[district360] 找不到 /app/config/application-prod.yml：请检查宿主机映射的 config/ 目录" >&2
  exit 1
fi

echo "[district360] 二级路由 ${D360_CONTEXT_PATH:-/}，容器端口 ${D360_PORT}，JAVA_OPTS=${JAVA_OPTS}"
# shellcheck disable=SC2086
exec java $JAVA_OPTS -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom -jar "$D360_JAR" "$@"
