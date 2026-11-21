# District 360 · 宜居 / ADREC 数据接入指南

> 给负责 **宜居（Liveability）** 和 **房地产（ADREC）** 数据的同事。
> 读完这份文档，你只需要改自己模块里的几个文件，就能让页面上对应的区块从 `NaN` 变成真实数值。
> 最后更新：2026-11-21

> ⚠️ **两条硬性要求**
> 1. **字段对照表（`docs/District360_数据字段对照_*.xlsx`）只供参考。** 每个指标的最终口径（取哪张表、什么条件、怎么算）都要 **和数据治理人员协商确认**，不能直接照抄 Excel。本文里的示例 SQL 也只是按 Excel 起草的参考写法。
> 2. **前端必须展示你的取数过程。** 页面上每个数值鼠标悬停 3 秒，会显示它来自哪个库、哪张表、怎么算、实际执行的 SQL。这些内容来自 `metric-lineage.yml`，**更新它是交付的必做项**（第 9 节），没更新不算完成。

---

## 1. 一句话说明

你要做的事：**和数据治理确认口径 → 实现自己模块里的抽取器 `extract()`（从源库查出数据装进「快照」对象返回）→ 把实际取数过程写进 `metric-lineage.yml`。**

写统计库、定时调度、接口返回、页面展示、状态切换（`NaN` → 实数）、指标悬浮提示，框架都已经做好了，你不需要碰。

```
你的源库 ──► 你的抽取器 extract()  ──►  快照对象（字段已定义好）
                                          │  框架自动写入
                                          ▼
                              统计库 district360（stat schema）
                                          │  接口自动读取
                                          ▼
                         /api/v1/districts/{code}/overview  ──►  页面
```

---

## 2. 你需要改哪些文件

| | 宜居同事 | 房地产同事 |
|---|---|---|
| 模块目录 | `backend/d360-liveability/` | `backend/d360-adrec/` |
| 抽取器（主要工作） | `extract/LiveabilityExtractor.java` | `extract/AdrecExtractor.java` |
| Mapper 接口 | `mapper/LiveabilityMapper.java` | `mapper/AdrecMapper.java` |
| SQL | `resources/mapper/liveability/LiveabilityMapper.xml` | `resources/mapper/adrec/AdrecMapper.xml` |
| 数据源配置 | `backend/d360-app/src/main/resources/application-local.yml` 里加 `d360.datasource.liveability` | 同左，加 `d360.datasource.adrec` |
| **取数过程说明（必做）** | `backend/d360-app/src/main/resources/metric-lineage.yml` 里 `liv_*`、`park*` 几项 | 同左，`re_*` 几项 |

**请不要修改**：`d360-common`（契约）、`d360-stat`（统计库表结构与写入）、`d360-app` 的 Java 代码、`frontend/`。需要改这些时请联系 makani 组（见第 10 节）。

模块里已经有骨架代码：数据源配置类、空的 Mapper、抛 `UnsupportedOperationException` 的抽取器。你在骨架上补全即可。

---

## 3. 口径确认流程（先做这一步）

| 步骤 | 做什么 | 产出 |
|---|---|---|
| ① 起草 | 参考字段对照表和本文第 4 / 5 节的示例，列出每个指标打算怎么取数（表、条件、计算） | 口径草稿 |
| ② 确认 | **和数据治理人员逐项协商**：表和字段是否正确、版本 / 阶段 / 状态的取值、单位、去重与过滤规则、统计期等 | 确认后的口径，记下确认人、日期、与 Excel 的差异 |
| ③ 实现 | 按确认后的口径写 SQL 和抽取器 | 代码 |
| ④ 写说明 | 把确认后的计算方式和实际执行的 SQL 写进 `metric-lineage.yml`（第 9 节） | 页面悬停可见的取数过程 |
| ⑤ 提 PR | PR 说明里写清楚：每个指标的口径由谁、哪天确认，与 Excel 有哪些差异；Reviewer 在页面上逐个悬停数值，核对说明与代码一致 | PR 通过 |

和 Excel 不一致的地方很正常（Excel 是早期整理的），以数据治理确认的为准，并在 PR 说明里写明差异。

---

## 4. 契约：你拿到什么、返回什么

### 4.1 你拿到的：`List<DistrictRef>`

框架调用 `extract(districts)` 时，会传入 **makani 全部 216 个区**（ADM 101、AAM 69、WRM 46），每个区是一个 `DistrictRef`：

| 字段 | 含义 | 示例 |
|---|---|---|
| `code()` | 页面区编码，**返回数据时用它标识区** | `adm-al-reem-island` |
| `nameEn()` | 显示名 | `Al Reem Island` |
| `municipality()` | 市政 | `ADM` / `AAM` / `WRM` |
| `makaniDistrictId()` | makani 的 districtid（跨市政会重复，仅供参考） | `57` |
| `scopeEwkbHex()` | **统计范围**：该区 makani 原始区面，EPSG:4326，EWKB 十六进制字符串 | `0106000020E6100000...` |

定义在 `backend/d360-common/src/main/java/com/district360/common/district/DistrictRef.java`。

### 4.2 统一口径：按「统计范围」做空间查询（重要）

**三个库（makani、宜居、ADREC）的区 ID / 区名可能不一致，但范围是一致的。** 所以约定：

- **不要用区 ID 或区名去匹配**，统一用 `scopeEwkbHex()` 做空间查询；
- 在 SQL 里把它还原成几何：`ST_GeomFromEWKB(decode(#{scope}, 'hex'))`（坐标系 4326）；
- **点位 / 记录类数据**（如设施点、成交记录）：位置落在范围内即计入
  `ST_Intersects(<范围>, <记录位置>)`；
- **区级汇总表**（如宜居得分是按 district_id 算好的）：先用范围在你自己的区表里找 **重合面积最大** 的那个区，再取它的指标
  `ORDER BY ST_Area(ST_Intersection(<你的区面>, <范围>)) DESC LIMIT 1`。

> 已验证：Liveability 的 `dim_districts.geom` 与 makani 区面基本完全重合（IoU ≥ 0.997），按范围匹配出的 district_id 与按名称对应的一致。

### 4.3 你返回的：快照对象

定义在 `backend/d360-common/src/main/java/com/district360/common/extract/snapshot/`，**字段已经和接口、页面一一对应好了**。下表「参考口径」来自字段对照表，**实现前请和数据治理确认**；字段本身（含义、单位）如需调整请联系 makani 组。

#### 宜居：`LiveabilitySnapshot`

```java
new LiveabilitySnapshot(
    LocalDate sourceAsOf,                       // 数据日期，页面区块标题显示
    List<LiveabilitySnapshot.District> districts)
```

| `LiveabilitySnapshot.District` 字段 | 类型 | 参考口径（以数据治理确认为准） | → 接口字段 | → 页面位置 |
|---|---|---|---|---|
| `code` | String | `DistrictRef.code()` | — | — |
| `inScope` | Boolean | 宜居库配置表 `param_district_scope.in_scope`（没有配置行按 false） | `liveability.status` = `not_assessed`，得分字段为 null | `false` 时宜居得分卡显示「该区不在宜居评估范围内」，不展示得分；公园照常 |
| `scoreExisting` | BigDecimal | `overall_score`，stage = Existing，当前发布版本（`dim_calc_versions.current_flag = true`） | `liveability.existing` | 宜居得分 · 现状 |
| `scoreCurrent` | BigDecimal | stage = Pipeline | `liveability.current` | 宜居得分主值「目前」、列表 / 地图悬停的宜居得分 |
| `scoreTarget` | BigDecimal | stage = AP50 | `liveability.target` | 宜居得分 · 目标 |
| （不用填） | — | 接口自动算 max(0, 目标 − 目前) | `liveability.gap` | 差距 |
| `parks` | Integer | 公园类设施点落在范围内的个数 | `facilities.parks.count` | 公共设施 · 公园 |
| `parkAreaHa` | BigDecimal | `existing_sqm ÷ 10000`（公顷） | `facilities.parks.areaHa` | 公园面积 ha |
| `parkGapHa` | BigDecimal | `max(0, area_required_sqm − existing_sqm) ÷ 10000` | `facilities.parks.gapHa` | 公园缺口 ha |

#### 房地产：`AdrecSnapshot`

```java
new AdrecSnapshot(
    LocalDate sourceAsOf,          // 数据日期（例如 _ingested_at 最大值）
    YearMonth periodStart,         // 统计期起始月，如 2025-11
    YearMonth periodEnd,           // 统计期结束月，如 2026-10（共 12 个月）
    List<AdrecSnapshot.District> districts,
    List<AdrecSnapshot.Monthly> monthly,
    List<AdrecSnapshot.CommunityMapping> communityMappings)   // 可选，可传空列表
```

| `AdrecSnapshot.District` 字段 | 类型 | 参考口径（以数据治理确认为准） | → 接口字段 | → 页面位置 |
|---|---|---|---|---|
| `code` | String | `DistrictRef.code()` | — | — |
| `salesAed` | BigDecimal | 统计期内 `sale_property_price_aed` 求和，**单位 AED**（不要换算成 bn） | `realEstate.salesAedBn`（接口自动 ÷ 10 亿） | 成交额 AED x.x bn |
| `salesYoyPct` | BigDecimal | （本期成交额 ÷ 上一个 12 个月成交额 − 1）× 100，**18.0 表示 +18%** | `realEstate.salesYoyPct` | 同比 |
| `transactions` | Integer | 统计期内 `sale_application_id` 计数 | `realEstate.transactions` | 成交宗数 |
| `mortgages` | Integer | 统计期内按揭数（`mortgage_start_date`） | `realEstate.mortgages` | 按揭 |
| `avgPriceSqm` | BigDecimal | 统计期内 `property_sale_rate_aed_sqm` 平均 | `realEstate.avgPriceSqm` | 均价 AED/m² |
| `activeLeases` | Integer | `rent_contract_current_status` = 有效 的租约数 | `realEstate.activeLeases` | 在册租约 |

| `AdrecSnapshot.Monthly` 字段 | 类型 | 参考口径 | → 接口 / 页面 |
|---|---|---|---|
| `code` | String | 区编码 | — |
| `month` | YearMonth | 月份 | `realEstate.monthly[].month` |
| `transactions` | int | 该月成交宗数 | 月度成交宗数柱状图（每区 12 条） |

`CommunityMapping(adrecCommunityName, districtCode, remark)`：**可选**。只有在 ADREC 没有坐标、只能按社区匹配时，用来记录「社区 → 区」对应以便核对（见第 7 节）。

### 4.4 取值约定

| 情况 | 怎么做 | 页面效果 |
|---|---|---|
| 某个区在你的源库里**完全没有数据** | **不要放进 `districts` 列表**（跳过） | 「没有查询到数据」 |
| 宜居：区存在，但**不在宜居评估范围** | 照常放进列表，`inScope = false` | 宜居得分卡显示「该区不在宜居评估范围内」，不展示得分；公园照常 |
| 某个指标查不到 | 字段填 `null` | 该数值显示 `NaN` |
| 指标真实值就是 0 | 填 `0` | 显示 `0` |
| 精度 | 得分、面积、金额统计库保留 2 位小数，多余的会四舍五入 | — |

**不要用 0 代替「没有数据」**：页面会把 0 当作真实值显示。

---

## 5. 宜居同事：接入步骤（附参考示例）

> 源库：`10.255.254.109:5443 / liveability_data_20260730`。
> ⚠️ 109 服务器目前存在硬件导致的数据损坏，**只读、不要写**（详见第 10 节）。

### 第 1 步：配置数据源

在 `backend/d360-app/src/main/resources/application-local.yml` 的 `d360.datasource` 下加：

```yaml
    liveability:
      url: jdbc:postgresql://10.255.254.109:5443/liveability_data_20260730
      username: <账号>
      password: "<密码>"
```

**配了 `url` 模块才会启用**（`LiveabilityDataSourceConfig` 带 `@ConditionalOnProperty`），连接池默认只读、最多 2 个连接。

### 第 2 步：写 SQL（Mapper）

`LiveabilityMapper.java` 示例：

```java
public interface LiveabilityMapper {
    /** 一个区的全部宜居指标；范围内没有宜居区时返回 null 或 livDistrictId 为空。 */
    LivDistrictRow districtStats(@Param("scope") String scopeEwkbHex);

    /** 数据日期 */
    LocalDate dataDate();
}
```

结果行类（放在本模块，例如 `row/LivDistrictRow.java`，字段按列名驼峰自动填充）：

```java
public class LivDistrictRow {
    public Integer livDistrictId;
    public BigDecimal scoreExisting;
    public BigDecimal scoreCurrent;
    public BigDecimal scoreTarget;
    public Integer parks;
    public BigDecimal parkAreaHa;
    public BigDecimal parkGapHa;
}
```

`LiveabilityMapper.xml` 参考示例（表名、字段已按实库核对；**口径按 Excel 起草，需与数据治理确认后再定稿**）：

```xml
<select id="districtStats" resultType="com.district360.liveability.row.LivDistrictRow">
    WITH s AS (SELECT ST_GeomFromEWKB(decode(#{scope}, 'hex')) AS g),     -- 统计范围 = makani 区面
         l AS (SELECT d.district_id
               FROM dim_districts d, s
               WHERE d.geom &amp;&amp; s.g
               ORDER BY ST_Area(ST_Intersection(d.geom, s.g)) DESC
               LIMIT 1)                                                   -- 重合面积最大的宜居区
    SELECT (SELECT district_id FROM l) AS liv_district_id,
           (SELECT overall_score FROM fact_district_scores
             WHERE district_id = (SELECT district_id FROM l) AND calc_version_id = 496 AND stage = 'Existing') AS score_existing,
           (SELECT overall_score FROM fact_district_scores
             WHERE district_id = (SELECT district_id FROM l) AND calc_version_id = 496 AND stage = 'Pipeline') AS score_current,
           (SELECT overall_score FROM fact_district_scores
             WHERE district_id = (SELECT district_id FROM l) AND calc_version_id = 496 AND stage = 'AP50')     AS score_target,
           (SELECT count(*) FROM dim_facilities f, s
             WHERE f.geom &amp;&amp; s.g AND ST_Intersects(s.g, f.geom)
               AND f.stage = 'Existing'
               AND f.facility_type IN ('Park_Local', 'Park_Neighbourhoud', 'Park_District', 'Park_Other'))   AS parks,
           (SELECT existing_sqm / 10000.0 FROM fact_parks_demand
             WHERE district_id = (SELECT district_id FROM l) AND calc_version_id = 496 LIMIT 1)              AS park_area_ha,
           (SELECT greatest(area_required_sqm - existing_sqm, 0) / 10000.0 FROM fact_parks_demand
             WHERE district_id = (SELECT district_id FROM l) AND calc_version_id = 496 LIMIT 1)              AS park_gap_ha
</select>

<select id="dataDate" resultType="java.time.LocalDate">
    SELECT max(updated_at)::date FROM fact_district_scores WHERE calc_version_id = 496
</select>
```

> 这段 SQL 已于 2026-11-21 在实库上跑通（只读）。Al Reem Island 的结果：district_id 185，现状 84.63、目前 86.59、目标 86.66，公园 10 个，公园面积 18.26 ha，缺口 12.13 ha（与原型数值一致）。
> `fact_parks_demand` 在 496 版下每个区正好一行（216 行 / 216 个区），`LIMIT 1` 只是保险。
> 仍待你确认：公园数的来源——字段对照表写的是 `dim_facilities`，新原型设计说明曾把它列在 makani 下，示例按 `dim_facilities` 写。

### 第 3 步：实现抽取器

`LiveabilityExtractor.java`：

```java
@Override
public LiveabilitySnapshot extract(List<DistrictRef> districts) {
    Long version = mapper.publishedVersion();   // 当前发布版本（current_flag = true），整批共用
    List<LiveabilitySnapshot.District> out = new ArrayList<>();
    for (DistrictRef d : districts) {
        if (d.scopeEwkbHex() == null) {
            continue;
        }
        LivDistrictRow r = mapper.districtStats(d.scopeEwkbHex(), version);
        // 范围内找不到宜居区 → 不放进列表，页面显示「没有查询到数据」
        if (r == null || r.livDistrictId == null) {
            continue;
        }
        // 不在评估范围的区也放进来（inScope = false），页面不展示得分
        out.add(new LiveabilitySnapshot.District(
                d.code(), r.inScope, r.scoreExisting, r.scoreCurrent, r.scoreTarget, r.parks, r.parkAreaHa, r.parkGapHa));
    }
    return new LiveabilitySnapshot(mapper.dataDate(version), out);
}
```

216 个区逐个查询即可，整体在几十秒以内。**抽取器里不要捕获异常后吞掉**：抛出异常时框架会记为失败，统计库保持原样。

### 第 4 步：和数据治理确认、写取数说明、本地验证（见第 3、8、9 节）

---

## 6. 房地产同事：接入步骤

> ADREC 的库不在 109 上，连接信息由你提供。

### 第 1 步：配置数据源

```yaml
    adrec:
      url: jdbc:postgresql://<host>:<port>/<db>
      username: <账号>
      password: "<密码>"
```

### 第 2 步：和数据治理确认口径（必须）

以下是已知需要确认的点，其余指标的表、字段、过滤条件也请逐项确认：

1. **统计期**：「近 12 个月」= 截至哪个月？建议取 **最近一个完整自然月** 往前 12 个月（原型是 Nov-25 – Oct-26）；同比 = 再往前 12 个月。
2. **记录位置**：成交 / 按揭 / 租约记录里有没有坐标或地块编号？（决定能否直接按统计范围空间查询，见第 7 节）
3. **在册租约「有效」** 在 `rent_contract_current_status` 里的具体取值。

### 第 3 步：写 SQL 与抽取器

有坐标时，每个区按范围查询，参考示例（字段名、条件以与数据治理确认的为准）：

```sql
-- 近 12 个月区级汇总
SELECT sum(s.sale_property_price_aed)            AS sales_aed,
       count(s.sale_application_id)              AS transactions,
       avg(s.property_sale_rate_aed_sqm)         AS avg_price_sqm
FROM x_q21_matview_sale s
WHERE ST_Intersects(ST_GeomFromEWKB(decode(#{scope}, 'hex')), <成交记录位置>)
  AND s.sale_contract_date >= #{periodStartDate} AND s.sale_contract_date < #{periodEndNextDate};

-- 月度宗数（12 条）
SELECT date_trunc('month', s.sale_contract_date)::date AS month, count(s.sale_application_id) AS transactions
FROM x_q21_matview_sale s
WHERE ST_Intersects(ST_GeomFromEWKB(decode(#{scope}, 'hex')), <成交记录位置>)
  AND s.sale_contract_date >= #{periodStartDate} AND s.sale_contract_date < #{periodEndNextDate}
GROUP BY 1 ORDER BY 1;
```

抽取器组装方式与宜居相同：每个区一个 `AdrecSnapshot.District` + 12 个 `AdrecSnapshot.Monthly`；同比在 Java 里或 SQL 里算都可以；某区统计期内一条记录都没有就跳过。

---

## 7. ADREC 没有坐标怎么办

如果 ADREC 只有社区名（例如 `AREA_EN`），没有坐标 / 地块编号：

1. **先联系 makani 组**：我们可以把 makani 社区面（`udm_community`，带区归属）提供给你（例如补进 `DistrictRef` 或统计库），保证仍然是按范围匹配；
2. 用社区名对上 makani 社区，再按社区面落区；对不上的社区记进 `communityMappings`（`districtCode = null`，`remark` 写原因），写入统计库表 `stat.map_adrec_community` 方便核对；
3. **不要自己维护一份「社区名 → 页面区」的手工对照表**——会和其他两个库的口径分叉。

---

## 8. 本地调试与验收

1. **启动后端**（统计库默认是 `10.255.254.101:15432/district360`）：
   ```bash
   cd backend && /path/to/mvn -B -DskipTests package && java -jar d360-app/target/district360.jar
   ```
   日志里应看到：`已启用的抽取器：[makani, liveability]`（或 `adrec`）。
2. **手动触发抽取**：
   ```bash
   curl -X POST -H "X-Admin-Token: local-dev-token" http://localhost:8080/api/v1/admin/etl/liveability
   ```
   同步返回结果，`status` 为 `success` 即写入成功；失败时 `message` 是异常信息，统计库保持原样。
3. **查看结果**：
   - `GET /api/v1/meta`：你的源 `rowCount`、`sourceAsOf`、`lastStatus`；
   - `GET /api/v1/districts/adm-al-reem-island/overview`：对应区块 `status` 从 `unavailable` 变成 `ok`，字段有值；
   - 统计库：`stat.stat_district_liveability` / `stat.stat_district_adrec` / `stat.stat_district_adrec_monthly`，抽取日志 `stat.etl_job_log`。
4. **看页面**（`cd frontend && npm run dev`，打开 http://localhost:5173）：对应区块不再显示 `NaN` 和「没有查询到数据」标签。
5. **悬停核对取数说明**：逐个悬停你负责的数值 3 秒，确认库、表、计算方式、SQL 与代码一致。
6. **验收建议**：Al Reem Island 与源库手工查询结果核对；再抽检 2 个其他市政的区（如 `aam-al-bateen`、`wrm-...`）。

参考值（宜居，按第 4 节 SQL 直接查源库）：Al Reem Island → district_id 185，现状 84.63 / 目前 86.59 / 目标 86.66，公园 10 个、18.26 ha、缺口 12.13 ha；Al Hidayriyyat → 157，目前 22.22。数据日期 2026-11-15。

---

## 9. 取数过程必须在前端展示（必做）

页面上 **每个数值** 鼠标悬停 3 秒都会弹出取数说明，内容全部来自 `backend/d360-app/src/main/resources/metric-lineage.yml`：

| 提示框里的内容 | 来自 yml 字段 | 你要怎么写 |
|---|---|---|
| 指标名 | `name-zh` / `name-en` | 已有，一般不用改 |
| 页面读取 | `stat-column` | 已有（统计库表.列） |
| 数据来源（库 / 表） | `source` + `source-tables` | 写你 **实际读取** 的表；库名由数据源配置自动显示，不写主机和端口 |
| 计算方式 | `logic-zh` / `logic-en` | 用一两句话写清楚条件和计算，和代码一致 |
| SQL | `sql` | **粘贴实际执行的 SQL**；统计范围参数写 `:district_scope`（页面会显示成「某区的统计范围」） |

你负责的指标：

- 宜居：`liv_current`、`liv_existing`、`liv_target`、`liv_gap`、`parks`、`park_area_ha`、`park_gap_ha`
- ADREC：`re_sales`、`re_yoy`、`re_transactions`、`re_mortgages`、`re_price`、`re_leases`、`re_monthly`

示例：

```yaml
      liv_current:
        name-zh: 宜居得分 · 目前
        name-en: Liveability · current
        source: liveability
        stat-column: stat_district_liveability.score_current
        source-tables: ["dim_districts", "fact_district_scores"]
        logic-zh: 按统计范围匹配重合面积最大的宜居区，取 stage = Pipeline、calc_version_id = 496 的 overall_score。
        logic-en: Liveability district with the largest overlap with the scope; overall_score with stage = Pipeline, calc_version_id = 496.
        sql: |
          WITH s AS (SELECT ST_GeomFromEWKB(:district_scope) AS g), ...
```

现在里面是按字段对照表起草的「计划口径」，请改成 **与数据治理确认后的口径**。抽取器启用后，提示里的「计划口径」标记会自动去掉；**说明与实际代码是否一致，由你负责**，PR review 时会逐项核对。

## 10. 协作规范

- **分支**：`feature/liveability-xxx` / `feature/adrec-xxx`，完成后提 PR 到 `main`，请 makani 组 review；
- **PR 自查清单**：
  - [ ] 每个指标的口径已与数据治理确认；
  - [ ] `metric-lineage.yml` 里你负责的指标已更新为确认后的计算方式和实际执行的 SQL；
  - [ ] PR 说明里写了每个指标的确认人、确认日期、与 Excel 的差异；
  - [ ] 本地跑通 `POST /api/v1/admin/etl/<源>`，`etl_job_log` 成功；
  - [ ] 页面上逐个悬停数值，提示内容与代码一致；
- **只改自己的模块** + `application-local.yml` 里自己的数据源 + `metric-lineage.yml` 里自己的指标；
- **需要改契约时**（快照加字段、统计表加列、接口返回加字段）：联系 makani 组，由我们修改 `d360-common` / `d360-stat`（含 Flyway 迁移脚本）/ 接口 / 前端，避免多人同时改表结构；
- **不要在抽取器里写统计库**，也不要直接改 `stat.*` 表——写入统一由框架完成（整批替换、单事务）；
- 第一次成功抽取后，该源的数据会整批替换；之后每天迪拜时间 03:00 自动抽取（`d360.etl.cron`）。

---

## 11. 当前环境提醒

| 项 | 情况 |
|---|---|
| 统计库 | `10.255.254.101:15432/district360`（PostgreSQL 12 + PostGIS 3.4），已建好，表由程序启动时自动创建 |
| makani 源库 | 暂用 109 上的 `makani_sync_full`（`makani_1119` 的建筑表被硬件故障写坏） |
| 109 服务器 | 2026-11-21 发现内存 / 磁盘硬件故障：数据被持续写坏，大批量写入时数据库两次崩溃恢复。**只读、不要写**，等 DBA 处理。Liveability 库也在 109 上，抽取时如果遇到「invalid memory alloc」「compressed data is corrupt」之类报错，大概率是这个原因，请告诉 makani 组 |
| 配置 | 所有数据源都在 `application-local.yml`（随代码提交，内网库）；生产环境用环境变量覆盖。`metric-lineage.yml` 里的 `planned-db` 只在没配数据源时显示，部署不用改 |

---

## 12. 常见问题

**Q：为什么不让我直接写统计表？**
A：写入要整批替换、单事务，失败时保持旧数据不变，还要记录抽取日志；统一由框架做，各源互不影响。

**Q：区很多（216 个），逐个查会不会慢？**
A：宜居按示例 SQL 逐区查询，总体几十秒；如果你的源库更慢，可以先一次性把所有数据读出来，在 Java 里按范围归区，但口径必须仍然是「按范围」。

**Q：我的库里有的区不在这 216 个里，或者 216 个里有的区我没有数据？**
A：只返回能对上的区；对不上的跳过即可，页面会显示「没有查询到数据」。宜居里能对上、但不在评估范围的区要返回，并标 `inScope = false`。

**Q：怎么确认我返回的数据真的到了页面上？**
A：按第 8 节：`etl_job_log` 成功 → `overview` 接口对应区块 `status = ok` → 页面不再是 `NaN`。

**Q：数据治理确认的口径和字段对照表不一样，听谁的？**
A：以数据治理确认的为准。`metric-lineage.yml` 里写确认后的口径，差异写在 PR 说明里。

**Q：契约里的字段不够用怎么办？**
A：联系 makani 组。我们改快照、统计表（Flyway 迁移）、接口和前端，你只管填值。
