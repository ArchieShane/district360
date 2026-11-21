-- 宜居评估范围：宜居库 param_district_scope.in_scope（配置表）。
-- 不在评估范围的区也写行（公园数 / 面积 / 缺口照常展示），但宜居得分不展示，页面显示「不在宜居评估范围」。
-- in_scope 为 NULL 表示未知（例如旧数据），按在评估范围处理。
SET search_path TO stat, public;

DROP VIEW IF EXISTS v_district_360;

ALTER TABLE stat_district_liveability ADD COLUMN in_scope boolean;
COMMENT ON COLUMN stat_district_liveability.in_scope IS '是否在宜居评估范围（宜居库 param_district_scope.in_scope）；false 时页面不展示宜居得分';
COMMENT ON TABLE stat_district_liveability IS '宜居得分与公园；范围内找不到宜居区的区不写行。in_scope = false 的区不展示得分';

CREATE VIEW v_district_360 AS
SELECT d.code,
       d.name_en,
       d.name_ar,
       d.sort_no,
       d.municipality,
       d.makani_district_id,
       m.area_km2,
       m.communities,
       m.plots,
       m.plots_constructed,
       m.plots_under_construction,
       m.plots_vacant_serviced,
       m.plots_vacant_unserviced,
       m.buildings,
       m.buildings_under_construction,
       m.schools,
       m.medical,
       m.mosques,
       m.source_as_of  AS makani_as_of,
       l.score_existing,
       l.score_current,
       l.score_target,
       l.parks,
       l.park_area_ha,
       l.park_gap_ha,
       l.in_scope      AS liv_in_scope,
       l.is_sample     AS liv_sample,
       l.source_as_of  AS liv_as_of,
       a.period_start_month,
       a.period_end_month,
       a.sales_aed,
       a.sales_yoy_pct,
       a.transactions,
       a.mortgages,
       a.avg_price_sqm,
       a.active_leases,
       a.is_sample     AS adrec_sample,
       a.source_as_of  AS adrec_as_of
FROM dim_district d
         LEFT JOIN stat_district_makani m ON m.district_code = d.code
         LEFT JOIN stat_district_liveability l ON l.district_code = d.code
         LEFT JOIN stat_district_adrec a ON a.district_code = d.code;
