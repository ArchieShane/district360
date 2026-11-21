# District 360 · Docker 离线部署

一个容器、一个端口：后端（Spring Boot）同时托管前端页面。默认二级路由 `/district360`：

- 页面：`http://<服务器>:8360/district360/`
- 接口：`http://<服务器>:8360/district360/api/v1/...`

镜像里只有 JRE 17 和启动脚本；**jar、配置文件、前端页面、日志都映射在宿主机目录里**，升级替换文件后重启即可，不用重新做镜像。

## 部署包内容

```
district360/
├─ .env                         端口、二级路由、JVM 内存
├─ docker-compose.yml
├─ start.sh                     导入镜像 + 启动 / 重建容器
├─ images/district360_1.0.tar.gz  运行镜像（eclipse-temurin:17-jre-jammy，x86_64）
├─ jar/district360.jar          → 容器 /app/jar/district360.jar
├─ config/application-prod.yml  → 容器 /app/config/application-prod.yml（数据库地址、账号等）
├─ static/                      → 容器 /app/static/（前端页面）
└─ logs/                        → 容器 /app/logs/（district360.log，按 100MB 滚动，保留 30 个）
```

## 首次部署

服务器需要 Docker（已有 `docker compose` 或 `docker-compose` 更好，没有也行，`start.sh` 会改用 `docker run`）。

```bash
tar xzf district360-docker-*.tar.gz && cd district360
vi config/application-prod.yml   # 填统计库、makani、宜居、ADREC 四个库的地址和账号（<...> 占位符全部替换；ADREC 的 source-srid 以数据方确认的为准）
vi .env                          # 按需改端口 D360_HOST_PORT、二级路由 D360_CONTEXT_PATH
./start.sh
```

统计库首次使用前要先建库并装 PostGIS（表由程序启动时自动创建）：

```sql
CREATE DATABASE district360;
-- 在 district360 库里：
CREATE EXTENSION IF NOT EXISTS postgis;
```

统计表为空时，程序启动后会在后台自动跑一次 makani 抽取（约 2.5 分钟），之后每天 03:00（迪拜时间）自动抽取。

检查：

```bash
docker ps --filter name=district360          # 约 1 分钟后 STATUS 显示 (healthy)
curl http://127.0.0.1:8360/district360/api/v1/meta
./start.sh logs                              # 或 tail -f logs/district360.log
```

## 常用操作

| 要做的事 | 怎么做 |
|---|---|
| 改数据库等配置 | 改 `config/application-prod.yml`，`docker restart district360` |
| 改端口 / 二级路由 / 内存 | 改 `.env`，`./start.sh`（会重建容器） |
| 升级后端 | 替换 `jar/district360.jar`，`docker restart district360` |
| 升级前端 | 清空 `static/` 后放入新的前端文件，无需重启（浏览器刷新即可） |
| 停止 | `./start.sh stop` |
| 手动触发抽取 | 先在配置里设 `d360.admin.token`，然后 `curl -X POST -H "X-Admin-Token: <口令>" http://127.0.0.1:8360/district360/api/v1/admin/etl/all` |

升级包（`district360-upgrade-*.tar.gz`）只含 `jar/` 和 `static/`：解压后覆盖到部署目录对应位置即可，**不会覆盖** `config/` 和 `.env`。

## 挂到域名的二级路由下（Nginx）

容器内部已经带着 `/district360` 前缀，Nginx 原样转发、**不要去掉前缀**（`proxy_pass` 后面不写路径）：

```nginx
location = /district360 { return 301 /district360/; }
location /district360/ {
    proxy_pass http://<部署服务器IP>:8360;   # 末尾不要加 / 或路径
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 300s;
}
```

改二级路由名（例如改成 `/d360`）：只改 `.env` 里的 `D360_CONTEXT_PATH=/d360` 再 `./start.sh`，Nginx 的 location 跟着改；前端用的是相对路径，**不用重新打包**。

## 注意

- **地图底图**：页面的底图样式默认从公网加载（CARTO dark-matter）。服务器离线不影响（底图由用户浏览器加载），但如果**用户的浏览器也上不了公网**，底图和地名不会显示（区边界、建筑、地块照常显示），需要内网的底图服务，打包前在 `frontend/.env.local` 里用 `VITE_BASEMAP_STYLE` 指定。
- 镜像是 x86_64（amd64）的；ARM 服务器需要另做镜像。
- 容器时区为 Asia/Dubai（.env 的 `TZ`），日志时间和定时抽取都按这个时区。

## 重新打包（开发机）

```bash
deploy/docker/build-package.sh                # 完整部署包（含镜像），在 109 上做镜像
SKIP_IMAGE=1 deploy/docker/build-package.sh   # 升级包（只有 jar + 前端）
```

产物在 `deploy/docker/out/`。脚本会在本机编译 jar（`MVN` 指定 mvn 路径）和前端，再通过 ssh 到 `BUILD_HOST`（默认 `root@10.255.254.109`）构建并导出镜像。
