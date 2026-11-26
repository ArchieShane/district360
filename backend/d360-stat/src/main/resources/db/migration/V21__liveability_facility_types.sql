-- 设施类型目录与各区数量（原型 Assets「all facility types (35) by domain」、Public facilities 前 5 类）。
-- 源：宜居库 dim_facility_types（原型 8 个领域、fact_facilities 有数据的类型，当前 35 类）
--     + fact_facilities（stage = Existing，ST_PointOnSurface 落在区范围内，与公园数同一口径）。
-- 由 liveability 抽取器每日整表重写。

CREATE TABLE stat_liv_facility_type (
    facility_type  varchar(64)  PRIMARY KEY,
    type_label     varchar(128) NOT NULL,
    category       varchar(64)  NOT NULL,
    category_label varchar(128) NOT NULL
);
COMMENT ON TABLE stat_liv_facility_type IS '设施类型目录：宜居库 dim_facility_types，原型 8 个领域内且 fact_facilities 有数据的类型（当前 35 类）';
COMMENT ON COLUMN stat_liv_facility_type.category IS '领域：dim_facility_types.category（如 Health_And_Wellbeing；宜居库拼写 Saftey_And_Security 原样保留）';

CREATE TABLE stat_district_liv_facility_count (
    district_code varchar(32) NOT NULL,
    facility_type varchar(64) NOT NULL,
    n             int         NOT NULL,
    PRIMARY KEY (district_code, facility_type)
);
COMMENT ON TABLE stat_district_liv_facility_count IS '各区各设施类型现状数量：fact_facilities stage = Existing、面内一点落在区范围内；只存数量大于 0 的';
