# District 360 · 区情概览

阿布扎比市政（ADM）17 个区的单页区情概览：点击地图上的区，3D 场景高亮该区，右侧弹出一页概览（宜居、土地与建设、房地产、公共设施）。

- 原型：`docs/District_360_区情概览_单页__20261121更新.html`
- 字段口径：`docs/District360_数据字段对照_2026112101.xlsx`
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

页面只有区级汇总数，没有明细，所以接口只读统计库：哪个源慢了、断了或者还没接入，页面都照常显示（显示上次抽取的结果，或者标「样例数值」/「暂无数据」）。

## 目录

```
backend/                      Spring Boot 3.5 · JDK 17 · MyBatis · Flyway
├─ d360-common                契约：统一返回体、区字典、StatExtractor 接口、三个源的快照类型
├─ d360-stat                  统计库：数据源、Flyway 建表（db/migration）、写入、读查询
├─ d360-makani                makani 抽取（按区面做空间汇总）
├─ d360-liveability           宜居抽取（骨架，待宜居组实现）
├─ d360-adrec                 ADREC 抽取（骨架，待房地产组实现）
└─ d360-app                   启动类、接口、定时调度；配置 application.yml、区字典 district-dictionary.yml
frontend/                     Vue 3 · Vite · TypeScript · Pinia · vue-i18n · MapLibre GL
docs/                         原型、字段对照、方案文档
```

## 本地运行

### 后端

需要 JDK 17、Maven 3.9。

内网开发库的账号密码已写在 `backend/d360-app/src/main/resources/application-local.yml`（默认激活 `local` profile），拉下代码即可直接运行。需要连别的库时，改这个文件或用环境变量覆盖（`D360_STAT_DB_URL`、`D360_MAKANI_DB_URL`、`D360_*_DB_USER`、`D360_*_DB_PASSWORD`）。

```bash
cd backend && mvn -B -DskipTests package && java -jar d360-app/target/district360.jar
```

启动时会自动：
1. Flyway 在统计库建表（`stat` schema），并写入宜居 / ADREC 的样例数据（`is_sample = true`）；
2. 把区字典同步到 `stat.dim_district`；
3. 如果 makani 统计表还是空的，后台跑一次 makani 抽取（约 6 秒）。

### 前端

需要 Node 20+。

```bash
cd frontend && npm install && npm run dev
```

打开 http://localhost:5173 ，`/api` 会代理到 http://localhost:8080 。底图默认用 CARTO dark-matter（免 key），可在 `frontend/.env.local` 里用 `VITE_BASEMAP_STYLE` 换成其他 MapLibre 样式。

生产构建：`npm run build`，产物在 `frontend/dist`，由 Nginx 托管并把 `/api` 反向代理到后端即可。

## 切换到生产库

当前连的是数据治理人员整理的库（`makani_1119`，表在 `ods` schema、带 `ods_makani_` 前缀）。到生产环境只改配置，不改代码：

- 数据源地址与账号：`d360.datasource.*`（或对应环境变量）；
- makani 表的位置：`d360.makani.schema`、`d360.makani.table-prefix`（例如生产库是 `public` schema、无前缀，就配 `schema: public`、`table-prefix: ""`）；
- 指标悬浮提示里的库名、表名会自动跟着配置变。

## 接口

| 接口 | 用途 |
|---|---|
| `GET /api/v1/districts` | 区列表：名称、面积、中心点、外包框、宜居得分（列表、悬停提示） |
| `GET /api/v1/districts/{code}/overview` | 右侧面板：标题 + 宜居 / 土地与建设 / 房地产 / 公共设施，每块带 `status` 和 `asOf` |
| `GET /api/v1/map/districts.geojson` | 区边界（EPSG:4326，已简化） |
| `GET /api/v1/map/tiles/buildings/{z}/{x}/{y}.pbf` | 建筑体块矢量瓦片（图层 `buildings`，属性 `code`、`height`、`uc`） |
| `GET /api/v1/meta` | 区数量、各源数据日期、是否样例、最近一次抽取状态 |
| `GET /api/v1/lineage` | 指标血缘：每个指标读哪个库 / 表、怎么算、对应 SQL（页面悬浮提示用） |
| `POST /api/v1/admin/etl/{makani\|liveability\|adrec\|all}` | 手动触发抽取，请求头 `X-Admin-Token`（配置项 `d360.admin.token`，为空时禁用） |

区块 `status`：`ok` 正常 · `sample` 样例数值 · `not_assessed` 未纳入宜居评估 · `unavailable` 暂无数据。

## 指标来源（悬浮提示）

页面上每个数值鼠标悬浮都会显示：页面读的是统计库哪一列、源头是哪个库哪张表、计算方式、对应 SQL（已代入当前区的 ID，可复制）。

说明写在 `backend/d360-app/src/main/resources/metric-lineage.yml`，库名、表名占位符在运行时按实际配置替换。**改了取数逻辑请同步改这个文件**；前端给数值加 `data-m="<指标键>"` 即可接入提示。

## 宜居组 / 房地产组怎么接入

只需要改自己的模块，不碰统计库、接口和前端：

1. 在 `application-local.yml` 里配置自己的源库 `d360.datasource.liveability.*` 或 `d360.datasource.adrec.*`（配置了 `url` 模块才会启用）；
2. 在 `resources/mapper/<模块>/*.xml` 写取数 SQL，实现 `LiveabilityExtractor` / `AdrecExtractor` 的 `extract()`，返回 `LiveabilitySnapshot` / `AdrecSnapshot`（定义在 `d360-common`，字段含义见类注释）；
3. 区的对应关系用 `DistrictRef`：宜居用 `livDistrictId()`（为空表示不在评估范围，跳过即可）；ADREC 是社区粒度，把社区 → 区映射放进快照的 `communityMappings`；
4. 本地验证：`POST /api/v1/admin/etl/liveability`（或 `adrec`），看 `stat.etl_job_log` 和页面对应区块；
5. 分支 `feature/liveability-*` / `feature/adrec-*`，PR 到 `main`。

第一次成功抽取会整批替换掉该源的样例数据。统计表结构、快照类型、`StatExtractor` 接口由 makani 组维护，需要改动请提 PR。

## 待确认

见 `docs/接口设计与协作方案_v0.2.md` 第 8 节。页面数值以各源库当前实际数据为准；影响口径的主要有：

- Al Mina / Al Wahda / Hudayriyat / Saadiyat 与 makani 区的对应关系（区字典里标了 `mapping-confirmed: false`，页面显示 TBC）；
- 土地利用「混合」：makani 的地块用途里没有 Mixed 这一类，归类规则待定（暂按 Investment，配置项 `d360.land-use.groups`；Investment 地块实际多为别墅 / 联排住宅）。
