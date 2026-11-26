-- 节点汇总加面积与竣工 / 拆除：
--   floors_area_sqm      建筑面积（GFA）= 楼层面积合计（BDMS floors）
--   units_area_sqm       使用面积 = 单元面积合计，改为不计单个 > 10,000 m² 的异常单元（源数据 829 个，最大 5.9 亿 m²）
--   units_area_outliers  被排除的异常单元数
--   plots_max_gfa_sqm    可建面积 = 未注销地块 devcode_max_gfa 合计（地块层级用于「已建面积使用率」）
--   buildings_completed / buildings_demolished / last_*_date  udm_building 有竣工 / 拆除日期的建筑数与最近日期
-- 加列后按新口径整表重算一次；之后每次空间抽取重算（ThemeSpatialWriteMapper.computeNodeStats，同一段 SQL）。
ALTER TABLE theme.sp_node_stat
    ADD COLUMN floors_area_sqm      numeric(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN units_area_outliers  int            NOT NULL DEFAULT 0,
    ADD COLUMN plots_max_gfa_sqm    numeric(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN buildings_completed  int            NOT NULL DEFAULT 0,
    ADD COLUMN buildings_demolished int            NOT NULL DEFAULT 0,
    ADD COLUMN last_completion_date date,
    ADD COLUMN last_demolition_date date;

COMMENT ON COLUMN theme.sp_node_stat.floors_area_sqm IS '建筑面积 GFA：楼层面积合计';
COMMENT ON COLUMN theme.sp_node_stat.units_area_sqm IS '使用面积：单元面积合计，不含单个 > 10,000 m² 的异常单元';
COMMENT ON COLUMN theme.sp_node_stat.units_area_outliers IS '面积 > 10,000 m² 被排除的异常单元数';
COMMENT ON COLUMN theme.sp_node_stat.plots_max_gfa_sqm IS '可建面积：未注销地块 devcode_max_gfa 合计';
COMMENT ON COLUMN theme.sp_node_stat.buildings_completed IS 'udm_building 有竣工日期的建筑数';
COMMENT ON COLUMN theme.sp_node_stat.buildings_demolished IS 'udm_building 有拆除日期的建筑数';

TRUNCATE theme.sp_node_stat;

INSERT INTO theme.sp_node_stat (level, node_key, communities, plots, plots_constructed, plots_under_construction,
                                plots_not_constructed, plots_area_sqm, plots_max_gfa_sqm, buildings, buildings_under_construction,
                                buildings_completed, buildings_demolished, last_completion_date, last_demolition_date,
                                floors, floors_area_sqm, units, units_area_sqm, units_area_outliers)
SELECT v.level, v.node_key,
       sum(x.communities), sum(x.plots), sum(x.plots_constructed), sum(x.plots_uc), sum(x.plots_nc),
       coalesce(sum(x.plots_area), 0), coalesce(sum(x.max_gfa), 0), sum(x.buildings), sum(x.buildings_uc),
       sum(x.b_completed), sum(x.b_demolished), max(x.comp_date), max(x.demo_date),
       sum(x.floors), coalesce(sum(x.floors_area), 0), sum(x.units), coalesce(sum(x.units_area), 0), sum(x.units_outliers)
FROM (
    SELECT municipality_code, district_key, NULL::varchar AS community_key, NULL::varchar AS plot_key, NULL::varchar AS building_key,
           1 AS communities, 0 AS plots, 0 AS plots_constructed, 0 AS plots_uc, 0 AS plots_nc, 0::numeric AS plots_area,
           0::numeric AS max_gfa, 0 AS buildings, 0 AS buildings_uc, 0 AS b_completed, 0 AS b_demolished,
           NULL::date AS comp_date, NULL::date AS demo_date, 0 AS floors, 0::numeric AS floors_area,
           0 AS units, 0::numeric AS units_area, 0 AS units_outliers
    FROM theme.sp_community
    UNION ALL
    SELECT municipality_code, district_key, community_key, plot_key, NULL,
           0, 1,
           coalesce((construction_status = 'Constructed')::int, 0),
           coalesce((construction_status = 'Under Construction')::int, 0),
           coalesce((construction_status = 'Not Constructed')::int, 0),
           coalesce(area_sqm, 0), coalesce(devcode_max_gfa, 0), 0, 0, 0, 0, NULL, NULL, 0, 0, 0, 0, 0
    FROM theme.sp_plot WHERE cancel_date IS NULL
    UNION ALL
    SELECT municipality_code, district_key, community_key, plot_key, building_key,
           0, 0, 0, 0, 0, 0, 0, 1,
           (lower(replace(coalesce(physical_status, ''), ' ', '')) = 'underconstruction')::int,
           (completion_date IS NOT NULL)::int, (demolition_date IS NOT NULL)::int,
           completion_date::date, demolition_date::date, 0, 0, 0, 0, 0
    FROM theme.sp_building
    UNION ALL
    SELECT municipality_code, district_key, community_key, plot_key, building_key,
           0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, NULL, NULL, 1, coalesce(area_sqm, 0), 0, 0, 0
    FROM theme.sp_floor
    UNION ALL
    SELECT municipality_code, district_key, community_key, plot_key, building_key,
           0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, NULL, NULL, 0, 0, 1,
           CASE WHEN area_sqm <= 10000 THEN area_sqm ELSE 0 END, (area_sqm > 10000)::int
    FROM theme.sp_unit
) x
CROSS JOIN LATERAL (VALUES ('municipality', x.municipality_code), ('district', x.district_key),
                           ('community', x.community_key), ('plot', x.plot_key), ('building', x.building_key)) v(level, node_key)
WHERE v.node_key IS NOT NULL
GROUP BY v.level, v.node_key
;
