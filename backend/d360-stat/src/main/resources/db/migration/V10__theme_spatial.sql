-- District 360 V1.0 主题库（schema: theme）· 空间下钻：市政 → 区域 → 社区 → 地块 → 建筑 → 楼层 → 单元
--
-- 数据来源：ODS 库（pop.ods，表名前缀 ods_），由 d360-ods 抽取工程按源整批替换写入（单事务）。
-- 设计说明见 docs/District360_V1.0_主题库设计.md。约定：
--   * 所有几何统一 EPSG:4326，面统一 MultiPolygon；centroid 为 ST_PointOnSurface（保证落在面内）；
--   * 各级主键用业务可读的稳定编码（*_key），源库原始 ID 原样保留在 src_* / *_id 字段；
--   * 上级编码（municipality_code / district_key / community_key / plot_key / building_key）在下级各表冗余，
--     由抽取后在主题库内统一关联（见 ThemeSpatialWriteMapper.xml 的 link* 语句），接口按上级编码直接过滤；
--   * 关联口径与现有统计库一致：属性能对上的用属性（社区 → 区域），对不上的用「对象内点落在上级面内」。
CREATE SCHEMA IF NOT EXISTS theme;

-- 面内一点：有效面用 ST_PointOnSurface（保证落在面内），无效面退回 ST_Centroid（PointOnSurface 对无效面会报错）
CREATE OR REPLACE FUNCTION theme.inner_point(g geometry) RETURNS geometry
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS
$$
SELECT CASE
           WHEN g IS NULL OR ST_IsEmpty(g) THEN NULL
           WHEN ST_IsValid(g) THEN ST_PointOnSurface(g)
           ELSE ST_Centroid(g)
       END
$$;

-- 市政 -------------------------------------------------------------------------------------------
CREATE TABLE theme.sp_municipality (
    municipality_code varchar(8)   PRIMARY KEY,           -- ADM / AAM / WRM
    municipality_id   int,                                -- 101 / 102 / 103
    name_en           varchar(128) NOT NULL,
    name_ar           varchar(256),
    area_km2          numeric(12, 3),
    centroid          geometry(Point, 4326),
    geom              geometry(MultiPolygon, 4326),
    loaded_at         timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE theme.sp_municipality IS '市政（阿布扎比酋长国 3 个市政）；源：ods_makani_uae_all_municipalities（emiratenameen = 1），无源面时由区域面合并';

-- 区域 -------------------------------------------------------------------------------------------
CREATE TABLE theme.sp_district (
    district_key       varchar(32)  PRIMARY KEY,          -- <市政>-<districtid>，如 ADM-93
    municipality_code  varchar(8)   NOT NULL,
    district_id        int          NOT NULL,             -- 源 districtid（只在市政内唯一）
    src_objectid       int,
    name_en            varchar(128) NOT NULL,
    name_ar            varchar(256),
    popular_name_en    varchar(128),
    population         numeric(14, 0),
    area_km2           numeric(12, 3),                    -- 由 shape 计算（UTM 40N），源 area 字段为空
    centroid           geometry(Point, 4326),
    geom               geometry(MultiPolygon, 4326),
    loaded_at          timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX sp_district_muni_idx ON theme.sp_district (municipality_code);
CREATE INDEX sp_district_geom_gix ON theme.sp_district USING gist (geom);
COMMENT ON TABLE theme.sp_district IS '区域；源：ods_makani_udm_district';

-- 社区 -------------------------------------------------------------------------------------------
CREATE TABLE theme.sp_community (
    community_key      varchar(48)  PRIMARY KEY,          -- <区域编码>-<communityid>，如 AAM-79-2
    district_key       varchar(32),
    municipality_code  varchar(8),
    district_id        int,
    community_id       varchar(32)  NOT NULL,             -- 源 communityid（只在区域内唯一）
    src_objectid       int,
    name_en            varchar(128),
    name_ar            varchar(256),
    popular_name_en    varchar(128),
    composite_name_en  varchar(256),
    area_km2           numeric(12, 3),
    centroid           geometry(Point, 4326),
    geom               geometry(MultiPolygon, 4326),
    loaded_at          timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX sp_community_district_idx ON theme.sp_community (district_key);
CREATE INDEX sp_community_geom_gix ON theme.sp_community USING gist (geom);
COMMENT ON TABLE theme.sp_community IS '社区 / 扇区；源：ods_makani_udm_community；district_key 按 (municipality, districtid) 属性关联（已核对 1864/1864 一致）';

-- 地块 -------------------------------------------------------------------------------------------
CREATE TABLE theme.sp_plot (
    plot_key             varchar(32)  PRIMARY KEY,        -- = gisid
    gisid                varchar(32)  NOT NULL,
    plot_id              varchar(32),
    plot_number          varchar(64),
    elms_plot_id         varchar(32),
    community_key        varchar(48),
    district_key         varchar(32),
    municipality_code    varchar(8),
    src_municipality     varchar(64),
    src_district_id      varchar(32),                     -- 地块自带的区 / 社区编号与 udm_district 不是一套编码，仅供参考
    src_district_en      varchar(128),
    src_community_id     varchar(32),
    src_community_en     varchar(128),
    area_sqm             numeric(14, 2),                  -- calculatedarea
    planned_area_sqm     numeric(14, 2),
    construction_status  varchar(32),                     -- Constructed / Under Construction / Not Constructed / ...
    service_status       varchar(64),
    planning_status      varchar(64),
    plot_status          varchar(64),
    primary_use          varchar(64),                     -- primaryuseengdesc
    parent_land_use      varchar(64),                     -- elms_parentlanduse_e
    land_use             varchar(128),                    -- elms_landusename_e
    secondary_use        varchar(128),
    ownership_type       varchar(64),
    allocation_status    varchar(64),
    investment_name      varchar(256),
    devcode              varchar(64),
    devcode_category     varchar(128),
    devcode_far          varchar(32),
    devcode_max_gfa      numeric(16, 2),
    max_heights          varchar(64),
    zone_tpss            varchar(64),
    sector_tpss          varchar(64),
    project_id           varchar(64),
    permit_last_type     varchar(128),
    permit_last_date     timestamp,
    cancel_date          timestamp,
    modified_date        timestamp,
    data_received_date   timestamp,
    centroid             geometry(Point, 4326),
    geom                 geometry(MultiPolygon, 4326),
    loaded_at            timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX sp_plot_community_idx ON theme.sp_plot (community_key);
CREATE INDEX sp_plot_district_idx ON theme.sp_plot (district_key);
CREATE INDEX sp_plot_geom_gix ON theme.sp_plot USING gist (geom);
COMMENT ON TABLE theme.sp_plot IS '地块；源：ods_makani_udm_plot（全量含已注销，cancel_date 非空即注销）；community_key 按地块内点落在社区面内';

-- 建筑 -------------------------------------------------------------------------------------------
CREATE TABLE theme.sp_building (
    building_key          varchar(40)  PRIMARY KEY,       -- udm-<buildinginternalid|objectid>；只有 BDMS 分层数据的建筑为 bdms-<buildingid>
    source                varchar(8)   NOT NULL,          -- udm：现状建筑台账；bdms：仅在 BDMS 楼层 / 单元里出现，由其外包面推导
    udm_objectid          int,
    building_id           varchar(32),                    -- udm buildingid（大量为空）
    building_internal_id  int,
    bdms_building_id      bigint,                         -- BDMS buildingid：与 udm 编号不是一套，按楼层内点落在建筑面内空间匹配
    gisid                 varchar(32),
    elms_plot_id          varchar(32),
    plot_key              varchar(32),
    community_key         varchar(48),
    district_key          varchar(32),
    municipality_code     varchar(8),
    name_en               varchar(256),
    name_ar               varchar(256),
    physical_status       varchar(32),
    primary_usage_type    varchar(64),
    primary_use           varchar(64),
    secondary_usage_type  varchar(64),
    floors_count          int,                            -- buildingnumberoffloors
    height_m              numeric(8, 2),
    completion_date       timestamp,
    demolition_date       timestamp,
    start_date            timestamp,
    permit_id             int,
    construction_site_id  int,
    is_bdms               varchar(8),
    bdms_floors           int,                            -- 关联后回填
    bdms_units            int,
    centroid              geometry(Point, 4326),
    geom                  geometry(MultiPolygon, 4326),
    loaded_at             timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX sp_building_plot_idx ON theme.sp_building (plot_key);
CREATE INDEX sp_building_community_idx ON theme.sp_building (community_key);
CREATE INDEX sp_building_district_idx ON theme.sp_building (district_key);
CREATE INDEX sp_building_bdms_idx ON theme.sp_building (bdms_building_id);
CREATE INDEX sp_building_geom_gix ON theme.sp_building USING gist (geom);
COMMENT ON TABLE theme.sp_building IS '建筑；源：ods_makani_udm_building（+ BDMS 楼层 / 单元推导的建筑）；plot_key 先按 gisid，再按建筑内点落在地块面内';

-- 楼层 -------------------------------------------------------------------------------------------
CREATE TABLE theme.sp_floor (
    floor_key          varchar(40)  PRIMARY KEY,          -- = BDMS floorid
    source             varchar(16)  NOT NULL,             -- bdms_floors：楼层表；bdms_units：楼层表缺失，由单元推导
    floor_id           bigint       NOT NULL,
    bdms_building_id   bigint,
    building_key       varchar(40),
    plot_key           varchar(32),
    community_key      varchar(48),
    district_key       varchar(32),
    municipality_code  varchar(8),
    floor_number       varchar(32),                       -- G / F1 / B1 / R ...
    floor_seq          int,                               -- 楼内自下而上序号（按底标高）
    base_height_m      numeric(8, 2),
    floor_height_m     numeric(8, 2),
    area_sqm           numeric(14, 2),
    building_status    varchar(32),
    plot_primary_use   varchar(64),
    geom               geometry(MultiPolygon, 4326),
    loaded_at          timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX sp_floor_building_idx ON theme.sp_floor (building_key);
CREATE INDEX sp_floor_bdms_idx ON theme.sp_floor (bdms_building_id);
COMMENT ON TABLE theme.sp_floor IS '楼层；源：ods_makani_bdms_floors_height（楼层表缺的楼层由 ods_makani_bdms_units_height 推导，source = bdms_units）';

-- 单元 -------------------------------------------------------------------------------------------
CREATE TABLE theme.sp_unit (
    unit_key           varchar(40)  PRIMARY KEY,          -- = BDMS unitid
    unit_id            bigint       NOT NULL,
    floor_id           bigint,
    bdms_building_id   bigint,
    floor_key          varchar(40),
    building_key       varchar(40),
    plot_key           varchar(32),
    community_key      varchar(48),
    district_key       varchar(32),
    municipality_code  varchar(8),
    unit_number        varchar(64),
    unit_type          varchar(64),                       -- Residential / Commercial ...
    usage_type         varchar(64),
    classification     varchar(64),                       -- Apartment / Villa / Shop ...
    category           varchar(64),
    area_sqm           numeric(14, 2),
    bedrooms           int,
    bathrooms          int,
    balcony_area_sqm   numeric(14, 2),
    floor_number       varchar(32),
    base_height_m      numeric(8, 2),
    floor_height_m     numeric(8, 2),
    building_status    varchar(32),
    geom               geometry(MultiPolygon, 4326),
    loaded_at          timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX sp_unit_floor_idx ON theme.sp_unit (floor_key);
CREATE INDEX sp_unit_building_idx ON theme.sp_unit (building_key);
CREATE INDEX sp_unit_bdms_idx ON theme.sp_unit (bdms_building_id);
COMMENT ON TABLE theme.sp_unit IS '单元；源：ods_makani_bdms_units_height';

-- 各级汇总（抽取后在主题库内计算，接口直接读）-------------------------------------------------------
CREATE TABLE theme.sp_node_stat (
    level                         varchar(16) NOT NULL,   -- municipality / district / community / plot / building
    node_key                      varchar(48) NOT NULL,
    communities                   int NOT NULL DEFAULT 0,
    plots                         int NOT NULL DEFAULT 0, -- 不含已注销
    plots_constructed             int NOT NULL DEFAULT 0,
    plots_under_construction      int NOT NULL DEFAULT 0,
    plots_not_constructed         int NOT NULL DEFAULT 0,
    plots_area_sqm                numeric(16, 2) NOT NULL DEFAULT 0,
    buildings                     int NOT NULL DEFAULT 0,
    buildings_under_construction  int NOT NULL DEFAULT 0,
    floors                        int NOT NULL DEFAULT 0,
    units                         int NOT NULL DEFAULT 0,
    units_area_sqm                numeric(16, 2) NOT NULL DEFAULT 0,
    PRIMARY KEY (level, node_key)
);
COMMENT ON TABLE theme.sp_node_stat IS '空间各级节点的计数汇总；每次空间抽取后整表重算';

-- 主题表登记：页面「数据接入状态」读这里 -------------------------------------------------------------
CREATE TABLE theme.meta_table (
    table_name     varchar(64)  PRIMARY KEY,
    theme          varchar(32)  NOT NULL,                 -- 空间 / 开发建设 / 市场 / 城市运行 / 资产与设施 / 宜居
    title_zh       varchar(128) NOT NULL,
    source_tables  varchar(512) NOT NULL,                 -- ODS 源表（省略 ods_ 前缀）
    extractor      varchar(32),                           -- 负责写入的抽取器（source），未实现为空
    sort_no        int          NOT NULL DEFAULT 0,
    row_count      bigint,
    source_as_of   date,
    loaded_at      timestamptz
);
COMMENT ON TABLE theme.meta_table IS '主题库表登记：每张主题表的主题、来源、最近一次写入的行数与时间';

INSERT INTO theme.meta_table (table_name, theme, title_zh, source_tables, extractor, sort_no) VALUES
    ('sp_municipality', '空间', '市政',  'makani_uae_all_municipalities', 'ods-spatial', 1),
    ('sp_district',     '空间', '区域',  'makani_udm_district',            'ods-spatial', 2),
    ('sp_community',    '空间', '社区',  'makani_udm_community',           'ods-spatial', 3),
    ('sp_plot',         '空间', '地块',  'makani_udm_plot',                'ods-spatial', 4),
    ('sp_building',     '空间', '建筑',  'makani_udm_building, makani_bdms_floors_height, makani_bdms_units_height', 'ods-spatial', 5),
    ('sp_floor',        '空间', '楼层',  'makani_bdms_floors_height, makani_bdms_units_height', 'ods-spatial', 6),
    ('sp_unit',         '空间', '单元',  'makani_bdms_units_height',       'ods-spatial', 7);
