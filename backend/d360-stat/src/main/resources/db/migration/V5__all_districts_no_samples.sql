-- 1) 区列表扩大到 udm_district 全部区（ADM / AAM / WRM），区编码带市政前缀（adm-al-reem-island）
-- 2) 删除开发期样例数据：没有接口数据的区块页面显示「没有查询到数据」，不再展示样例数值
SET search_path TO stat, public;

DROP VIEW IF EXISTS v_district_360;

ALTER TABLE dim_district ADD COLUMN municipality varchar(16);
CREATE INDEX IF NOT EXISTS dim_district_sort_idx ON dim_district (sort_no);

-- 清空 makani 产出的表，启动后自动重新抽取全部区
DELETE FROM dim_district;
DELETE FROM stat_district_makani;
DELETE FROM stat_district_landuse;
DELETE FROM map_building;

-- 删除样例数据（宜居 / ADREC 抽取器接入后写入的才是真实数据）
DELETE FROM stat_district_adrec_monthly m
WHERE NOT EXISTS (SELECT 1 FROM stat_district_adrec a WHERE a.district_code = m.district_code AND NOT a.is_sample);
DELETE FROM stat_district_adrec WHERE is_sample;
DELETE FROM stat_district_liveability WHERE is_sample;

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
