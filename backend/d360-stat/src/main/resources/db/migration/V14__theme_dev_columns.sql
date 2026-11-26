-- 开发建设主题接入（抽取器 ods-dev）：按 ODS 实际字段补列。
-- 源表（makani_1119 / pop 均有数据）：
--   makani_masterplan_ud_masterplan_boundary  1,029 个总规项目面（projectid 唯一）
--   makani_masterplan_ud_plot                 82,675 个总规地块（147 个项目；udm_gisid 有值的约 72%）
--   makani_udm_constructionsite               2,034 个在建工地面
-- 资本项目 / 建设许可 / 竣工维修拆除三张表（view_gsec_* / view_emecs_dashboard / view_meps_*）ODS 里还没有数据，暂不接入。

-- 总体规划 ----------------------------------------------------------------------------------------
ALTER TABLE theme.dev_masterplan
    ADD COLUMN IF NOT EXISTS project_no         varchar(64),
    ADD COLUMN IF NOT EXISTS municipality_code  varchar(8),
    ADD COLUMN IF NOT EXISTS src_municipality   varchar(64),
    ADD COLUMN IF NOT EXISTS stage_raw          varchar(128),
    ADD COLUMN IF NOT EXISTS masterplan_level   varchar(32),
    ADD COLUMN IF NOT EXISTS project_type       varchar(128),
    ADD COLUMN IF NOT EXISTS project_lead       varchar(256),
    ADD COLUMN IF NOT EXISTS owner_name         varchar(256),
    ADD COLUMN IF NOT EXISTS parent_project_no  varchar(64),
    ADD COLUMN IF NOT EXISTS area_sqm           numeric(16, 2),
    ADD COLUMN IF NOT EXISTS centroid           geometry(Point, 4326);
ALTER TABLE theme.dev_masterplan ALTER COLUMN src_district_id TYPE varchar(256);
COMMENT ON COLUMN theme.dev_masterplan.stage IS '规划阶段（归一）：Concept / Detailed / Minor Amendment / Time Extension / Enquiry / Other；原值在 stage_raw';
COMMENT ON COLUMN theme.dev_masterplan.src_district_id IS '源 districtid：实为区名文本且常为空，仅供参考；district_key 按项目面内点落区';
CREATE INDEX IF NOT EXISTS dev_masterplan_community_idx ON theme.dev_masterplan (community_key);

-- 总规地块 ----------------------------------------------------------------------------------------
ALTER TABLE theme.dev_masterplan_plot
    ADD COLUMN IF NOT EXISTS src_objectid              int,
    ADD COLUMN IF NOT EXISTS municipality_code         varchar(8),
    ADD COLUMN IF NOT EXISTS masterplan_type           varchar(64),
    ADD COLUMN IF NOT EXISTS approval_status           varchar(64),
    ADD COLUMN IF NOT EXISTS registered_land_use       varchar(128),
    ADD COLUMN IF NOT EXISTS land_use_l1               varchar(128),
    ADD COLUMN IF NOT EXISTS land_use_l2               varchar(256),
    ADD COLUMN IF NOT EXISTS udm_plot_number           varchar(64),
    ADD COLUMN IF NOT EXISTS plot_area_sqm             numeric(16, 2),
    ADD COLUMN IF NOT EXISTS far                       numeric(10, 3),
    ADD COLUMN IF NOT EXISTS building_storeys          int,
    ADD COLUMN IF NOT EXISTS buildings                 int,
    ADD COLUMN IF NOT EXISTS residential_population    numeric(14, 1),
    ADD COLUMN IF NOT EXISTS hotel_rooms               int,
    ADD COLUMN IF NOT EXISTS commercial_recreation_gfa numeric(16, 2),
    ADD COLUMN IF NOT EXISTS religious_gfa             numeric(16, 2),
    ADD COLUMN IF NOT EXISTS education_gfa             numeric(16, 2),
    ADD COLUMN IF NOT EXISTS healthcare_gfa            numeric(16, 2),
    ADD COLUMN IF NOT EXISTS community_facilities_gfa  numeric(16, 2),
    ADD COLUMN IF NOT EXISTS centroid                  geometry(Point, 4326),
    ADD COLUMN IF NOT EXISTS geom                      geometry(MultiPolygon, 4326);
CREATE INDEX IF NOT EXISTS dev_masterplan_plot_district_idx ON theme.dev_masterplan_plot (district_key);
CREATE INDEX IF NOT EXISTS dev_masterplan_plot_community_idx ON theme.dev_masterplan_plot (community_key);
CREATE INDEX IF NOT EXISTS dev_masterplan_plot_geom_gix ON theme.dev_masterplan_plot USING gist (geom);
COMMENT ON COLUMN theme.dev_masterplan_plot.plot_key IS '先按 udm_gisid 对 sp_plot.gisid，对不上的按总规地块面内点落在（未注销）地块面内';

-- 在建工地 ----------------------------------------------------------------------------------------
ALTER TABLE theme.dev_construction_site
    ADD COLUMN IF NOT EXISTS description        varchar(512),
    ADD COLUMN IF NOT EXISTS municipality_code  varchar(8),
    ADD COLUMN IF NOT EXISTS buildings          int,
    ADD COLUMN IF NOT EXISTS area_sqm           numeric(16, 2),
    ADD COLUMN IF NOT EXISTS centroid           geometry(Point, 4326);
CREATE INDEX IF NOT EXISTS dev_construction_site_district_idx ON theme.dev_construction_site (district_key);
CREATE INDEX IF NOT EXISTS dev_construction_site_community_idx ON theme.dev_construction_site (community_key);
COMMENT ON COLUMN theme.dev_construction_site.buildings IS '在建建筑数：sp_building.construction_site_id 指向本工地的建筑';

UPDATE theme.meta_table SET extractor = 'ods-dev'
 WHERE table_name IN ('dev_masterplan', 'dev_masterplan_plot', 'dev_construction_site');
