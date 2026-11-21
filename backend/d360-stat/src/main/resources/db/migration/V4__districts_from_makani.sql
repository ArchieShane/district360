-- 区列表改为以数据库为准：makani udm_district 中 ADM 的全部区，由 makani 抽取整批写入 dim_district。
-- 不再维护配置清单，去掉字典相关字段；区编码改为由 makani 区名生成（al-reem-island 等）。
SET search_path TO stat, public;

DROP VIEW IF EXISTS v_district_360;

ALTER TABLE dim_district DROP COLUMN liv_district_id;
ALTER TABLE dim_district DROP COLUMN mapping_confirmed;
ALTER TABLE dim_district DROP COLUMN mapping_note;
CREATE INDEX IF NOT EXISTS dim_district_name_idx ON dim_district (name_en);

-- 清空 makani 产出的表，启动后自动重新抽取 ADM 全部区
DELETE FROM dim_district;
DELETE FROM stat_district_makani;
DELETE FROM stat_district_landuse;
DELETE FROM map_building;

-- 开发期样例数据（宜居 / ADREC）改用新的区编码；Al Wahda 在 makani 中不存在，删除
DELETE FROM stat_district_liveability WHERE district_code = 'wahda';
DELETE FROM stat_district_adrec WHERE district_code = 'wahda';
DELETE FROM stat_district_adrec_monthly WHERE district_code = 'wahda';

CREATE TEMP TABLE code_map (old_code varchar, new_code varchar) ON COMMIT DROP;
INSERT INTO code_map VALUES
    ('reem', 'al-reem-island'), ('maryah', 'al-maryah-island'), ('zahiyah', 'al-zahiyah'), ('danah', 'al-danah'),
    ('hisn', 'al-hisn'), ('khalidiyah', 'al-khalidiyah'), ('manhal', 'al-manhal'), ('nahyan', 'al-nahyan'),
    ('bateen', 'al-bateen'), ('mushrif', 'al-mushrif'), ('zafaranah', 'hadbat-al-zafaranah'),
    ('rasakhdar', 'al-ras-al-akhdar'), ('hudayriyat', 'al-hidayriyyat'), ('saadiyat', 'al-saadiyat-island'),
    ('yas', 'yas-island'), ('mina', 'zayed-port');
UPDATE stat_district_liveability t SET district_code = m.new_code FROM code_map m WHERE t.district_code = m.old_code;
UPDATE stat_district_adrec t SET district_code = m.new_code FROM code_map m WHERE t.district_code = m.old_code;
UPDATE stat_district_adrec_monthly t SET district_code = m.new_code FROM code_map m WHERE t.district_code = m.old_code;

CREATE VIEW v_district_360 AS
SELECT d.code,
       d.name_en,
       d.name_ar,
       d.sort_no,
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
