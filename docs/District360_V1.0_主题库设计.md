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

### 2.2 开发建设（部分接入，`ods-dev`，2026-11-26）

迁移 V14（按源字段补列）、V15（加宽用途文本）。抽取器 `ods-dev` 在每日定时里排在 `ods-spatial` 之后（按空间结果关联），全量约 40 秒。

| 表 | 行数 | ODS 源表 | 关联口径 | 关联结果（makani_1119） |
|---|---|---|---|---|
| `dev_masterplan` | 1,029 | makani_masterplan_ud_masterplan_boundary | 项目面内点落社区，再落区域（源 districtid 是区名文本且常为空，不用）；阶段 `stage_dm` 归一为 Concept / Detailed / Minor Amendment / Time Extension / Enquiry / Other，原值在 `stage_raw` | 1,026 有社区 / 区域；123 个阶段为 Other（源为 None 或空） |
| `dev_masterplan_plot` | 82,675 | makani_masterplan_ud_plot | 先 `udm_gisid` = 地块 gisid，再面内点落未注销地块；社区 / 区域随地块 | 82,200（99.4%）有地块，其中 52,055 按 gisid；79,421（96%）属于已知总规 |
| `dev_construction_site` | 2,028 | makani_udm_constructionsite | 面内点落未注销地块，再落社区；在建建筑数 = `sp_building.construction_site_id` | 1,818 有地块，全部有社区；457 个工地有 833 栋建筑 |

**总规实现率** = 范围内能对上地块的总规地块中，所在地块 `construction_status = Constructed` 的比例。它直接继承地块表的建设状态：例如 AL REEM ISLAND（ADM-57）地块表本身 2,319 块 Not Constructed、155 块 Constructed，实现率只有 7.5%，是否反映实际待与数据方确认。

资本项目 `dev_capital_project`、建设许可 `dev_permit`、竣工 / 维修 / 拆除 `dev_building_application` 的源视图（view_gsec_*、view_emecs_dashboard、view_meps_*）在 pop 库为 0 行、makani_1119 里没有这些表，暂未接入。

### 2.3 市场（部分接入，`ods-mkt`，2026-11-26）

迁移 V16。抽取器 `ods-mkt` 在每日定时里排在 `ods-spatial`、`ods-dev` 之后，读当前 ODS 库；**源表不存在时跳过该表**，在 `theme.meta_table.note` 记「ODS 无源表：…」，页面据此提示，导入后下次抽取自动接上。约 10 秒。

| 表 | 行数（makani_1119） | ODS 源表 | 关联口径 |
|---|---|---|---|
| `mkt_tenancy` | 10,000（9,112 份租约、3,001 个单元） | makani_tbl_tawtheeq_all | gisid → 地块（9,973 条，99.7%），社区 / 区域随地块；单元级需 tbl_bdms_tawtheeq_link_data，当前 50 行桥接与样例对不上，暂不关联 |
| `mkt_sale` / `mkt_mortgage` / `mkt_rent` | 0（源表未导入） | re_x_q21_matview_sale / mortgage / rent | 经纬度点落未注销地块、建筑；社区 / 区域随地块，没有地块的按点落社区（源 district / community 是文本，不用） |
| `mkt_plot_transaction` | 0（源表未导入） | re_elms_buy_and_sell_f、re_elms_mortgages_f | elms_plot_id → 地块 |

- **个人信息不入主题库**：Tawtheeq 的租户、出租人、联系人姓名不抽。
- Tawtheeq `rental_value` 为**年租金**（AED；公寓中位数 72,000、合同平均约 1 年）。少数大额合同会把平均值拉高数倍（公寓平均 42 万），所以接口一律给**中位数**；单位面积租金用 Σ租金 ÷ Σ面积。
- 样例数据起租 2010-12 至到期 2024-12；「生效中」按源状态 ACTIVE，部分已过到期日。
- ADREC / ELMS 抽取 SQL 已在 pop 库（各 10 行样例）上验证可执行。

### 2.4 城市运行（部分接入，`ods-ops`，2026-11-26）

迁移 V17。抽取器 `ods-ops` 在每日定时里排在市场之后，约 30 秒。

| 表 | 行数 | ODS 源表 | 说明 |
|---|---|---|---|
| `ops_streetlight` | 124,961 点 / 182,260 灯盏 | makani_adwea_e_streetlight | 源为 EPSG:32640，转 4326；点落社区（124,944 条），社区没有的落区域；状态 In Service / Out Of Service / UnderConst / Proposed / Abandon；源 roadtype 全为空，不统计 |
| `ops_bus_stop` | 3,338 站台 / 1,949 站 | makani_bus_stop | 一行一个站台（主键源 objectid），`gtfs_id` 相同为同一站（如 76000A / B / C 相距约 100 m）；EPSG:32640 转 4326；全部落到社区 |
| `ops_case`、`ops_accident`、`ops_distortion`、`ops_violation`、`ops_occupancy_case` | 0 | view_adda_cases 等 | pop 为 0 行、makani_1119 无表。抽取器每次检查源表并在 `meta_table.note` 写「ODS 无源表 / 源表无数据 / 已有数据但抽取未实现」，有数据后再实现抽取 |

### 2.5 设施（已接入，`ods-ast`，2026-11-26）

迁移 V18。抽取器 `ods-ast` 在每日定时里排在城市运行之后，约 9 秒，写 `ast_facility` 8,148 行。
**用的是 v1 区情概览统计的同一批 makani 表**（数据清单里的 view_lnd_adek_school / view_lnd_doh_facilities 在 pop 为 0 行），过滤规则也一致：

| 类别 | 行数 | ODS 源表 | 说明 |
|---|---|---|---|
| school 学校 | 817（786 个 ESIS） | makani_poi_adek_schools_locations | 学位、在校生、教职工；课程、性别、年级、评级、学费等放 `attrs`；地块按 `udm_gisid`（816 条） |
| medical 医疗 | 3,114 | makani_poi_doh_facilities | facility_category = Medical（含阿拉伯文 طبي 4 条）；床位；名称含 test 的排除 |
| pharmacy 药房 | 1,478 | 同上 | facility_category = Pharma；Non-Health Care 不要 |
| mosque 清真寺 | 2,473 | makani_mosque_awqaf | 阿拉伯文字段；可容纳礼拜人数、层数、建筑状况；无位置的 3 条不要 |
| park 公园 | 266 个面 / 228 个公园 | makani_udm_park + makani_parks_details | 同一 parkid 可有多个面，个数按 park_id 去重；DMT 明细 60 条中 41 条并入所在公园（设施、开放时间、门票），其余 18 条作为独立公园 |

- **社区 / 区域按设施自身位置点落区**（与 v1「中心点落在区面内」一致），不随地块；地块另按 gisid / 位置关联（医疗、药房多在商场楼宇里，落到地块的约 70%）。
- 与 v1 逐区核对（216 个区）：学校、清真寺完全一致；医疗只差 4 个区各 1 条，即 v1 不计的阿拉伯文 طبي 类别。

### 2.6 其他业务主题（已建表，待接入）

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
