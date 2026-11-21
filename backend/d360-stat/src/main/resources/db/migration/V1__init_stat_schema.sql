-- District 360 统计库（schema: stat）
-- 各数据源写各自的表（整批替换），接口只读这些表。表结构由 makani 侧统一维护，改动请提 PR。
SET search_path TO stat, public;

-- 区字典 + makani 区面 ---------------------------------------------------------
CREATE TABLE dim_district (
    code               varchar(32)  PRIMARY KEY,
    name_en            varchar(128) NOT NULL,
    name_ar            varchar(256),
    sort_no            int          NOT NULL,
    makani_district_id int,
    makani_name        varchar(128),
    liv_district_id    int,
    mapping_confirmed  boolean      NOT NULL DEFAULT true,
    mapping_note       varchar(512),
    centroid           geometry(Point, 4326),
    bbox               geometry(Polygon, 4326),
    boundary           geometry(MultiPolygon, 4326),
    updated_at         timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE dim_district IS '统一区字典（code 为全系统主键）；区面、中心点来自 makani';

-- makani -----------------------------------------------------------------------
CREATE TABLE stat_district_makani (
    district_code              varchar(32) PRIMARY KEY,
    area_km2                   numeric(12, 3),
    communities                int,
    plots                      int,
    plots_constructed          int,
    plots_under_construction   int,
    plots_vacant_serviced      int,
    plots_vacant_unserviced    int,
    buildings                  int,
    buildings_under_construction int,
    schools                    int,
    medical                    int,
    mosques                    int,
    source_as_of               date,
    extracted_at               timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE stat_district_makani IS 'makani 区级汇总：对象中心点落在区面内即计入';

CREATE TABLE stat_district_landuse (
    district_code varchar(32) NOT NULL,
    use_desc      varchar(64) NOT NULL,
    plot_count    int         NOT NULL,
    PRIMARY KEY (district_code, use_desc)
);
COMMENT ON TABLE stat_district_landuse IS '区 × makani 原始用途（udm_plot.primaryuseengdesc）地块数；居住/商业/混合等归类在读接口按配置做';

CREATE TABLE map_building (
    id                 bigserial PRIMARY KEY,
    district_code      varchar(32) NOT NULL,
    building_id        varchar(64),
    height             numeric(8, 1) NOT NULL,
    under_construction boolean NOT NULL DEFAULT false,
    geom               geometry(MultiPolygon, 3857) NOT NULL
);
CREATE INDEX map_building_geom_gix ON map_building USING gist (geom);
COMMENT ON TABLE map_building IS '17 区建筑体块（EPSG:3857），供矢量瓦片 3D 拉伸';

-- Liveability（宜居同事写入）---------------------------------------------------
CREATE TABLE stat_district_liveability (
    district_code  varchar(32) PRIMARY KEY,
    score_existing numeric(6, 2),
    score_current  numeric(6, 2),
    score_target   numeric(6, 2),
    parks          int,
    park_area_ha   numeric(12, 2),
    park_gap_ha    numeric(12, 2),
    is_sample      boolean NOT NULL DEFAULT false,
    source_as_of   date,
    extracted_at   timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE stat_district_liveability IS '宜居得分与公园；不在评估范围的区不写行（页面显示未纳入评估）';

-- ADREC（房地产同事写入）-------------------------------------------------------
CREATE TABLE map_adrec_community (
    adrec_community_name varchar(256) PRIMARY KEY,
    district_code        varchar(32),
    remark               varchar(512),
    updated_at           timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE map_adrec_community IS 'ADREC 社区 → 页面区；district_code 为空 = 未映射';

CREATE TABLE stat_district_adrec (
    district_code      varchar(32) PRIMARY KEY,
    period_start_month date,
    period_end_month   date,
    sales_aed          numeric(18, 2),
    sales_yoy_pct      numeric(8, 2),
    transactions       int,
    mortgages          int,
    avg_price_sqm      numeric(12, 2),
    active_leases      int,
    is_sample          boolean NOT NULL DEFAULT false,
    source_as_of       date,
    extracted_at       timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE stat_district_adrec_monthly (
    district_code varchar(32) NOT NULL,
    month         date        NOT NULL,
    transactions  int         NOT NULL,
    PRIMARY KEY (district_code, month)
);

-- 抽取日志 ---------------------------------------------------------------------
CREATE TABLE etl_job_log (
    id          bigserial PRIMARY KEY,
    source      varchar(32) NOT NULL,
    started_at  timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    status      varchar(16) NOT NULL,
    row_count   int,
    source_as_of date,
    message     text
);
CREATE INDEX etl_job_log_source_idx ON etl_job_log (source, started_at DESC);

-- 原型里说的 district_360 宽表 ---------------------------------------------------
CREATE VIEW v_district_360 AS
SELECT d.code,
       d.name_en,
       d.name_ar,
       d.sort_no,
       d.mapping_confirmed,
       d.mapping_note,
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
