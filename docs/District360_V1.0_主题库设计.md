# District 360 V1.0 · 主题库设计（ODS → theme）

依据：产品原型 `20261123_DMT_Livability_District360_V1.0_AIG.html`（区域 → 社区 → 地块 → 建筑 → 楼层 → 单元逐级下钻）、
数据团队清单 `District360_数据清单与指标关联_20261124_v2.xlsx`（120 个指标、44 张 ODS 源表）。

## 1. 架构

```
ODS 库（10.255.254.111:15432/pop，schema ods）
   │  d360-ods 抽取工程（只读 ODS，流式读出、逐行交给写入端）
   ▼
统计库同库 district360 · schema theme（主题库，明细 + 空间归属编码）
   │  ThemeLoader（d360-stat）：单事务 清表 → 写入 → 主题库内关联上下级 → 汇总 → 登记
   ▼
Spring Boot  /api/v2/space/*  ──►  frontend-360（Vue 3 + MapLibre，按原型的 360 面板）
```

- 与宜居 / ADREC 抽取同一框架：手动触发 `POST /api/v1/admin/etl/ods-spatial`、每日 03:00 定时、日志 `stat.etl_job_log`、失败回滚原数据不变。
- 区别：统计库是区级快照（每区一行，可整体放内存）；主题库是明细（全量约 38 万建筑、52 万楼层、73 万单元），
  所以新增 `ThemeExtractor / ThemeSink / ThemeLoader` 契约（`d360-common` 的 `theme` 包），**流式**读写：
  ODS 连接关闭自动提交 + fetchSize 5000 游标读取，主题库每 500 行一批写入。
- 关联在主题库里做（有空间索引），不在 ODS 上跑大表空间连接（ODS 表无空间索引）。

## 2. 主题表

Flyway：`V10__theme_spatial.sql`（空间）、`V11__theme_business.sql`（业务主题）。表登记在 `theme.meta_table`，页面「数据」页签与各主题页签读它。

### 2.1 空间下钻（已接入，`ods-spatial`）

| 表 | 层级 | 主键（编码规则） | ODS 源表 | 上级关联口径 |
|---|---|---|---|---|
| `sp_municipality` | 市政 | `ADM` / `AAM` / `WRM` | `makani_uae_all_municipalities`（municipalityid 101/102/103） | —；源表无面时由区域面合并 |
| `sp_district` | 区域 | `<市政>-<districtid>`，如 `ADM-400` | `makani_udm_district` | municipalityname |
| `sp_community` | 社区 | `<区域编码>-<communityid>`，如 `ADM-400-1896` | `makani_udm_community` | (municipality, districtid) 属性（核对 1864/1864）；对不上按位置 |
| `sp_plot` | 地块 | gisid | `makani_udm_plot` | 地块内点落在社区面内（地块自带区 / 社区编号与 udm 不是一套） |
| `sp_building` | 建筑 | `udm-<buildinginternalid>`；BDMS 推导的为 `bdms-<buildingid>` | `makani_udm_building` + BDMS | 先 gisid，再建筑内点落在未注销地块面内 |
| `sp_floor` | 楼层 | BDMS floorid | `makani_bdms_floors_height`（缺的由单元推导） | BDMS buildingid → 建筑 |
| `sp_unit` | 单元 | BDMS unitid | `makani_bdms_units_height` | floorid → 楼层 |
| `sp_node_stat` | 各级汇总 | (level, node_key) | — | 每次抽取后重算：社区 / 地块 / 已建 / 在建 / 空置 / 建筑 / 楼层 / 单元 / 面积 |

所有下级表冗余全部上级编码（`municipality_code / district_key / community_key / plot_key / building_key / floor_key`），接口按编码直接过滤。

**BDMS ↔ udm 建筑**：已在 109 全量库核对，BDMS `buildingid` 与 udm 的 `objectid / buildingid / buildinginternalid` 都对不上
（objectid 数值区间重叠但空间 0/300 吻合）。因此按「该楼最低一层的面内一点落在哪个 udm 建筑面内」匹配；
匹配不上的 BDMS 楼用其楼层外包面生成 `source = bdms` 的建筑，页面标「推导数据」。

### 2.2 业务主题（已建表，待接入）

| 主题 | 表 | ODS 源表 | 空间归属 |
|---|---|---|---|
| 开发建设 | `dev_masterplan` | makani_masterplan_ud_masterplan_boundary | 面落区 / 社区 |
| | `dev_masterplan_plot` | makani_masterplan_ud_plot | udm_gisid → 地块 |
| | `dev_capital_project` | view_gsec_project_details + _point / _polygon | 几何落区 |
| | `dev_permit` | view_emecs_dashboard | gisid → 地块 |
| | `dev_building_application` | view_meps_cj_building_projects_dashboard | zone + sector + plot_number（口径待确认） |
| | `dev_construction_site` | makani_udm_constructionsite | 面落区 |
| 市场 | `mkt_sale` / `mkt_mortgage` / `mkt_rent` | re_x_q21_matview_sale / mortgage / rent | 文本区名 / 社区名映射 |
| | `mkt_plot_transaction` | re_elms_buy_and_sell_f、re_elms_mortgages_f | elms_plot_id → 地块 |
| | `mkt_tenancy` | makani_tbl_tawtheeq_all + tbl_bdms_tawtheeq_link_data + view_vw_tawtheeq_unit_status | BDMS unitid → 单元 |
| 城市运行 | `ops_case` | view_adda_cases + flood_w_adda_slacomplaince_d + flood_adda_csat_d | 坐标落地块 / 社区 |
| | `ops_accident` | view_accident_master_new | 坐标落区 |
| | `ops_distortion` | view_ins_distortion + view_uis_v_area_inspections | 坐标 / zone+sector+plot |
| | `ops_violation` | view_ins_violations + view_ins_violation_trx_result + view_ins_violations_uis | plot_gis_id → 地块 |
| | `ops_occupancy_case` | view_ins_survey | zone+sector+plot |
| | `ops_streetlight` / `ops_bus_stop` | makani_adwea_e_streetlight / makani_bus_stop | 点落区 |
| 资产与设施 | `ast_facility` | view_lnd_adek_school、view_lnd_doh_facilities、makani_mosque_awqaf、makani_udm_park、makani_parks_details | 点 / 面落地块 |
| 宜居 | `liv_resident_need` | dream_busstopsurveys（字段待确认） | 区域 |

粒度一律为「明细 + 空间归属编码」，区级统计在接口里按编码聚合——原型的地块 / 建筑页要列明细（许可、交易、违规……）。
接入一个主题 = 在 `d360-ods` 写一个 `ThemeExtractor<XxxSink>` + 在 `d360-stat` 写对应 `ThemeLoader`，框架不用改。

## 3. ODS 数据现状（2026-11-25）

本地开发当前用 mock 库 **makani_1119**（111，schema ods，表名 ods_makani_*，与 pop 库表名一致，只换 url）。

| ODS 表 | makani_1119 | pop | 说明 |
|---|---|---|---|
| makani_udm_district / community | 216 / 1,864 | 216 / 1,864 | 齐全 |
| makani_udm_building | 382,692 | 0 | |
| makani_bdms_floors_height | 523,750 行（floorid 去重 399,119） | 0 | 重复 floorid 只保留一行 |
| makani_udm_plot | 10,000 | 10,000 | 样例（全量约 41 万） |
| makani_bdms_units_height | 10,000 | 10,000 | 样例（全量约 73 万） |
| makani_udm_municipality | — | 0 | 市政用 uae_all_municipalities |

makani_1119 抽取结果（5 分 42 秒，单事务）：市政 3、区域 216、社区 1,864、地块 10,000、建筑 478,111（udm 382,692 + BDMS 推导 95,419）、楼层 399,119、单元 10,000。

- 关联率：楼层 → 建筑、单元 → 楼层 100%；建筑 → 社区 / 区域 99.99%；BDMS 楼 → udm 建筑匹配 92,222 栋；
  建筑 → 地块只有 14,891 栋（地块只有样例）。
- 未匹配的 95,419 栋 BDMS 楼抽查 2,000 栋，只有 16% 与任何 udm 建筑面有重叠，多为 As Planned 的规划楼，按「推导数据」保留。
- 接口耗时（本机 → 101）：酋长国 / 市政节点约 0.5 秒，区域及以下 < 0.2 秒；最大区域的建筑图层（约 1.9 万栋）13.5 MB（已开 gzip），后续可改为矢量瓦片。

ODS 全量入库后重新执行 `ods-spatial` 即可，无需改代码；字段检查脚本 `deploy/docker/check-ods-columns.sql`（makani_1119、pop 均通过）。

## 4. 待确认

1. ODS 全量入库时间；udm_building / bdms_floors_height 为空是否为同步问题。
2. 全量（约 160 万行）单事务写入耗时与主题库容量，首次全量后评估是否改为分表交换。
3. BDMS ↔ udm 建筑是否有官方桥接字段（目前按空间匹配）。
4. 各业务主题的空间归属口径（ADREC 文本区名映射、zone+sector+plot 规则）。
