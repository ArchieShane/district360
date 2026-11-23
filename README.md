# District 360 · 区情概览

阿布扎比酋长国 makani 全部 216 个区（ADM 101、AAM 69、WRM 46）的单页区情概览（区列表以数据库为准）：点击地图上的区，3D 场景高亮该区，右侧弹出一页概览（宜居、土地与建设、房地产、公共设施）。

- 原型：`docs/District_360_区情概览_单页__20261121更新.html`
- 字段口径参考：`docs/District360_数据字段对照_*.xlsx`（**只供参考**，最终口径以与数据治理确认的为准，并写进 `metric-lineage.yml`，页面悬停可见）
- 方案与分工：`docs/接口设计与协作方案_v0.2.md`

## 架构

```
makani_1119 ─┐ MakaniExtractor       （makani 组）
Liveability ─┼ LiveabilityExtractor  （宜居组）     每日 03:00 抽取 + 手动触发
ADREC ───────┘ AdrecExtractor        （房地产组）
                 │ 统一写入（按源整批替换，单事务）
                 ▼
     district360 统计库（schema stat，视图 v_district_360）
                 │ 只读
                 ▼
     Spring Boot REST /api/v1  ──►  Vue3 + MapLibre（在线底图 + 建筑 3D 瓦片）
```

**区列表以数据库为准**：makani `udm_district` 中的全部区（216 个，所有市政），按区名排序。区名跨市政会重复（如 AAM 和 ADM 都有 AL BATEEN），所以区编码是「市政 + 区名」，如 `adm-al-reem-island`（页面链接 `#d=adm-al-reem-island`）。不维护区清单配置。

**没有数据时显示 NaN**：某个数据源还没接入（或该区在源库里没有数据）时，区块照常显示完整布局，数值显示灰色的 `NaN`，区块标题右侧标「没有查询到数据」；不显示任何样例数值，便于区分哪些数据已经做完。

**统计口径：三个库都按「统计范围」做空间查询。** 统计范围 = 每个区的 makani 原始区面（存于 `stat.dim_district.scope`）。各库的区 ID / 区名可能不一致，但范围一致；已验证 Liveability 的区面与 makani 区面基本重合（IoU ≥ 0.997）。

页面只有区级汇总数，没有明细，所以接口只读统计库：哪个源慢了、断了或者还没接入，页面都照常显示（显示上次抽取的结果，或者标「样例数值」/「暂无数据」）。

## 目录

```
backend/                      Spring Boot 3.5 · JDK 17 · MyBatis · Flyway
├─ d360-common                契约：统一返回体、DistrictRef（区 + 统计范围）、StatExtractor 接口、三个源的快照类型
├─ d360-stat                  统计库：数据源、Flyway 建表（db/migration）、写入、读查询
├─ d360-makani                makani 抽取：产出区列表（全部区，所有市政）、区面，并按区面做空间汇总
├─ d360-liveability           宜居抽取（骨架，待宜居组实现）
├─ d360-adrec                 ADREC 抽取（骨架，待房地产组实现）
└─ d360-app                   启动类、接口、定时调度；配置 application.yml、指标来源 metric-lineage.yml
frontend/                     Vue 3 · Vite · TypeScript · Pinia · vue-i18n · MapLibre GL
docs/                         原型、字段对照、方案文档
```

## 本地运行

### 后端

需要 JDK 17、Maven 3.9。

内网开发库的地址和账号密码都写在 `backend/d360-app/src/main/resources/application-local.yml`（默认激活 `local` profile），拉下代码即可直接运行：

- 统计库 `d360.datasource.stat`：当前为 `10.255.254.101:15432/district360`（PostgreSQL 12 + PostGIS 3.4）；
- makani 源库 `d360.datasource.makani` + `d360.makani.schema / table-prefix`：当前为 `10.255.254.111:15432/makani_sync_full`；宜居源库同在 111 上（`liveability_data_20260730`）。

新建统计库时先执行 `CREATE DATABASE district360;` 并在库里 `CREATE EXTENSION postgis;`，表由 Flyway 在后端启动时自动创建。也可以用环境变量覆盖（`D360_STAT_DB_URL`、`D360_MAKANI_DB_URL`、`D360_*_DB_USER`、`D360_*_DB_PASSWORD`、`D360_MAKANI_SCHEMA`、`D360_MAKANI_TABLE_PREFIX`）。

```bash
cd backend && mvn -B -DskipTests package && java -jar d360-app/target/district360.jar
```

启动时会自动：
1. Flyway 在统计库建表（`stat` schema）；
2. 如果 makani 统计表还是空的，后台跑一次 makani 抽取：从 makani 读出全部 216 个区（区名、区面）及各项计数、38.3 万栋建筑、41 万个地块（全量库），约 2.5 分钟。

### 前端

需要 Node 20+。

```bash
cd frontend && npm install && npm run dev
```

打开 http://localhost:5173 ，`/api` 会代理到 http://localhost:8080 。底图默认用 CARTO dark-matter（免 key），可在 `frontend/.env.local` 里用 `VITE_BASEMAP_STYLE` 换成其他 MapLibre 样式。

生产构建：`npm run build`，产物在 `frontend/dist`（相对路径打包，可挂在任意二级路由下）。

## Docker 离线部署

前后端同一个端口、默认二级路由 `/district360`，jar / 配置 / 前端页面映射在宿主机目录。打包：`deploy/docker/build-package.sh`（在 109 上做镜像），部署和 Nginx 配置见 [deploy/docker/README.md](deploy/docker/README.md)。

## 切换 makani 库（schema / 表名前缀）

makani 源表的位置全部由配置决定，表全名 = `<schema>.<table-prefix><表名>`，换库只改配置、不改代码：

| 库 | `d360.datasource.makani.url` | `d360.makani.schema` | `d360.makani.table-prefix` |
|---|---|---|---|
| 数据治理库 | `.../makani_1119` | `ods` | `ods_makani_` |
| 全量库 / 生产库 | `.../makani_sync_full` | `public` | `""`（留空） |

- 默认值在 `application.yml`，本地覆盖在 `application-local.yml`，也可以用环境变量 `D360_MAKANI_DB_URL`、`D360_MAKANI_SCHEMA`、`D360_MAKANI_TABLE_PREFIX`；
- 改完重启后端，再执行一次抽取（`POST /api/v1/admin/etl/makani`）；
- 指标悬浮提示里的库名、表名会自动跟着配置变。

> 当前本地配置指向 111 上的 `makani_sync_full`：2026-11-21 发现 109 服务器存在硬件导致的数据损坏，源库已于 2026-11-22 迁到 10.255.254.111:15432 并修复，过程见 [docs/109数据库迁移步骤.md](docs/109数据库迁移步骤.md)。

## 接口

| 接口 | 用途 |
|---|---|
| `GET /api/v1/districts?page=0&size=30&q=` | 区列表分页（全部区，按区名排序）：左侧列表滚动到底加载下一页，`q` 按区名或市政（ADM / AAM / WRM）搜索 |
| `GET /api/v1/districts/{code}/overview` | 右侧面板：标题 + 宜居 / 土地与建设 / 房地产 / 公共设施，每块带 `status` 和 `asOf` |
| `GET /api/v1/map/districts.geojson` | 全部区边界（EPSG:4326，已简化）及标签 / 悬停所需属性（中心点、外包框、面积、宜居得分），地图一次性加载 |
| `GET /api/v1/map/tiles/buildings/{z}/{x}/{y}.pbf` | 建筑体块矢量瓦片（图层 `buildings`，属性 `code`、`height`、`uc`） |
| `GET /api/v1/districts/{code}/facilities/{school\|medical\|mosque}` | 某区公共设施列表（与面板计数同一口径）：面板点击学校 / 医疗 / 清真寺展开，点击条目地图定位 |
| `GET /api/v1/map/tiles/plots/{z}/{x}/{y}.pbf` | 地块矢量瓦片（选中区后按建设状态高亮） |
| `GET /api/v1/meta` | 区数量、各源数据日期、是否样例、最近一次抽取状态 |
| `GET /api/v1/lineage` | 指标血缘：每个指标读哪个库 / 表、怎么算、对应 SQL（页面悬浮提示用） |
| `POST /api/v1/admin/etl/{makani\|liveability\|adrec\|all}` | 手动触发抽取，请求头 `X-Admin-Token`（配置项 `d360.admin.token`，为空时禁用） |

区块 `status`：`ok` 正常 · `not_assessed` 未纳入宜居评估（宜居接入真实数据后才会出现）· `unavailable` 没有查询到数据。

## 指标来源（悬浮提示）

页面上每个数值鼠标悬浮都会显示：页面读的是统计库哪一列、源头是哪个库哪张表、计算方式、对应 SQL（已代入当前区的 ID，可复制）。

说明写在 `backend/d360-app/src/main/resources/metric-lineage.yml`，库名、表名占位符在运行时按实际配置替换。**改了取数逻辑请同步改这个文件**；前端给数值加 `data-m="<指标键>"` 即可接入提示。

## 宜居组 / 房地产组怎么接入

**详细步骤、字段口径和可直接参考的示例代码见 [docs/同事接入指南_宜居与ADREC.md](docs/同事接入指南_宜居与ADREC.md)。** 下面是简要版：

只需要改自己的模块，不碰统计库、接口和前端：

1. 在 `application-local.yml` 里配置自己的源库 `d360.datasource.liveability.*` 或 `d360.datasource.adrec.*`（配置了 `url` 模块才会启用）；
2. 在 `resources/mapper/<模块>/*.xml` 写取数 SQL，实现 `LiveabilityExtractor` / `AdrecExtractor` 的 `extract()`，返回 `LiveabilitySnapshot` / `AdrecSnapshot`（定义在 `d360-common`，字段含义见类注释）；
3. **按统计范围做空间查询，不用区 ID / 区名**：框架传进来的 `DistrictRef.scopeEwkbHex()` 就是该区的 makani 原始区面，SQL 里用 `ST_GeomFromEWKB(decode(#{scope}, 'hex'))`。区级表（如宜居得分）先用范围找重合面积最大的区，点位 / 记录按位置落在范围内计数。示例见 `LiveabilityMapper.xml` 和 `metric-lineage.yml`；
4. 本地验证：`POST /api/v1/admin/etl/liveability`（或 `adrec`），看 `stat.etl_job_log` 和页面对应区块；
5. 分支 `feature/liveability-*` / `feature/adrec-*`，PR 到 `main`。

接入前页面对应区块显示「没有查询到数据」；第一次成功抽取后显示真实数值。统计表结构、快照类型、`StatExtractor` 接口由 makani 组维护，需要改动请提 PR。

## 待确认

见 `docs/接口设计与协作方案_v0.2.md` 第 8 节。页面数值以各源库当前实际数据为准；影响口径的主要有：

- 土地利用：按业务要求已删除「混合」，面板只显示居住、商业；Investment 等未列出的用途计入「公用与其他」（配置项 `d360.land-use.groups`）。
