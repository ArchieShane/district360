#!/bin/sh
# 在开发机上执行：编译后端 jar、打包前端，到构建服务器（默认 109）上做运行镜像并导出，
# 最后在 deploy/docker/out/ 下生成可拷到离线服务器的部署包 district360-docker-<日期>.tar.gz。
#
#   deploy/docker/build-package.sh                 完整部署包（含镜像，首次部署用）
#   SKIP_IMAGE=1 deploy/docker/build-package.sh    升级包（只有 jar + 前端，不含镜像）
#
# 可用环境变量：
#   MVN          mvn 命令（默认 mvn）
#   BUILD_HOST   做镜像的服务器，需有 docker 且已有 eclipse-temurin:17-jre-jammy 或能联网拉取（默认 root@10.255.254.109）
#   IMAGE        镜像名（默认 district360:1.0）
set -e

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
HERE="$ROOT/deploy/docker"
MVN=${MVN:-mvn}
BUILD_HOST=${BUILD_HOST:-root@10.255.254.109}
IMAGE=${IMAGE:-district360:1.0}
STAMP=$(date +%Y%m%d%H%M)
OUT="$HERE/out"
PKG="$OUT/district360"

echo "== 1/4 编译后端"
(cd "$ROOT/backend" && "$MVN" -B -q -DskipTests package)

echo "== 2/4 打包前端"
(cd "$ROOT/frontend" && { [ -d node_modules ] || npm ci; } && npm run build)

echo "== 3/4 组装部署目录 $PKG"
rm -rf "$PKG" && mkdir -p "$PKG/jar" "$PKG/static" "$PKG/logs" "$PKG/images"
cp "$ROOT/backend/d360-app/target/district360.jar" "$PKG/jar/"
cp -R "$ROOT/frontend/dist/." "$PKG/static/"
if [ -z "$SKIP_IMAGE" ]; then
  mkdir -p "$PKG/config"
  cp "$HERE/config/application-prod.yml" "$PKG/config/"
  cp "$HERE/env.example" "$PKG/.env"
  cp "$HERE/docker-compose.yml" "$HERE/start.sh" "$HERE/README.md" "$PKG/"
  chmod +x "$PKG/start.sh"

  echo "== 4/4 在 $BUILD_HOST 上做镜像 $IMAGE"
  REMOTE=/tmp/district360-image-build
  ssh "$BUILD_HOST" "rm -rf $REMOTE && mkdir -p $REMOTE"
  scp -q "$HERE/Dockerfile" "$HERE/entrypoint.sh" "$BUILD_HOST:$REMOTE/"
  # 镜像名里的 : 换成 _ 作文件名
  TAR="$(echo "$IMAGE" | tr ':/' '__').tar.gz"
  ssh "$BUILD_HOST" "cd $REMOTE && docker build -q -t $IMAGE . && docker save $IMAGE | gzip > $REMOTE/$TAR && docker image inspect -f '{{.Architecture}}' $IMAGE"
  scp -q "$BUILD_HOST:$REMOTE/$TAR" "$PKG/images/"
  ssh "$BUILD_HOST" "rm -rf $REMOTE"
  NAME="district360-docker-$STAMP.tar.gz"
else
  rmdir "$PKG/images" "$PKG/logs"
  echo "== 4/4 跳过镜像（升级包）"
  NAME="district360-upgrade-$STAMP.tar.gz"
fi

# macOS 打包时不带 ._ 隐藏文件和扩展属性（否则 Linux 解压时会报 LIBARCHIVE.xattr 警告）
(cd "$OUT" && COPYFILE_DISABLE=1 tar --no-xattrs --no-mac-metadata -czf "$NAME" district360)
echo
echo "部署包：$OUT/$NAME（$(du -h "$OUT/$NAME" | cut -f1)）"
