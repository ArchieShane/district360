#!/bin/sh
# 在离线服务器的部署目录里执行：首次会导入镜像，之后每次执行都会按当前 .env 重建并启动容器。
#   ./start.sh          导入镜像（如果还没有）并启动 / 重建容器
#   ./start.sh logs     看容器日志
#   ./start.sh stop     停止并删除容器（不影响 jar / config / static / logs）
set -e
cd "$(dirname "$0")"

[ -f .env ] || { echo "缺少 .env（可从 env.example 复制）" >&2; exit 1; }
# 逐项读取 .env（不用 source：JAVA_OPTS 带空格，docker-compose 的 .env 不要求加引号）
envget() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- | sed -e 's/\r$//' -e 's/^"\(.*\)"$/\1/'; }
IMAGE=$(envget D360_IMAGE); IMAGE=${IMAGE:-district360:1.0}
D360_HOST_PORT=$(envget D360_HOST_PORT)
if grep -qE '^D360_CONTEXT_PATH=' .env; then D360_CONTEXT_PATH=$(envget D360_CONTEXT_PATH); else D360_CONTEXT_PATH=/district360; fi
JAVA_OPTS=$(envget JAVA_OPTS)
TZ=$(envget TZ)

if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE="docker-compose"
else
  COMPOSE=""
fi

case "$1" in
  logs) exec docker logs -f --tail 200 district360 ;;
  stop)
    if [ -n "$COMPOSE" ]; then $COMPOSE down; else docker rm -f district360; fi
    exit 0 ;;
esac

# 先检查文件和配置，没填好就不用导入镜像了
[ -f jar/district360.jar ] || { echo "缺少 jar/district360.jar" >&2; exit 1; }
[ -f config/application-prod.yml ] || { echo "缺少 config/application-prod.yml" >&2; exit 1; }
[ -f static/index.html ] || { echo "缺少 static/index.html（前端页面）" >&2; exit 1; }
if grep -v '^[[:space:]]*#' config/application-prod.yml | grep -q '<[^>]*>'; then
  echo "config/application-prod.yml 里还有 <...> 占位符没填，请先填好各库的地址和账号（统计库、makani、宜居、ADREC）" >&2; exit 1
fi

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  TAR=$(ls images/*.tar.gz images/*.tar 2>/dev/null | head -1)
  [ -n "$TAR" ] || { echo "镜像 $IMAGE 不存在，images/ 下也没有镜像包" >&2; exit 1; }
  echo "导入镜像 $TAR ..."
  if [ "${TAR%.gz}" != "$TAR" ]; then gunzip -c "$TAR" | docker load; else docker load -i "$TAR"; fi
fi

mkdir -p logs

if [ -n "$COMPOSE" ]; then
  $COMPOSE up -d --force-recreate
else
  # 没有 docker compose 时用 docker run，参数与 docker-compose.yml 一致
  docker rm -f district360 >/dev/null 2>&1 || true
  docker run -d --name district360 --restart unless-stopped \
    -p "${D360_HOST_PORT:-8360}:8080" \
    -e "D360_CONTEXT_PATH=${D360_CONTEXT_PATH}" \
    -e "JAVA_OPTS=${JAVA_OPTS:--Xms512m -Xmx2g}" \
    -e "TZ=${TZ:-Asia/Dubai}" \
    -v "$PWD/jar:/app/jar:ro" \
    -v "$PWD/config:/app/config:ro" \
    -v "$PWD/static:/app/static:ro" \
    -v "$PWD/logs:/app/logs" \
    --log-opt max-size=50m --log-opt max-file=3 \
    "$IMAGE"
fi

echo
echo "已启动。页面：http://<本机IP>:${D360_HOST_PORT:-8360}${D360_CONTEXT_PATH}/"
echo "看日志：./start.sh logs   （或 tail -f logs/district360.log）"
echo "健康状态：docker ps --filter name=district360   （启动约 1 分钟后显示 healthy）"
