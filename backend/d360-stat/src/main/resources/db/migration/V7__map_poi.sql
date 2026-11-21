-- 公共设施点位（学校 / 医疗 / 清真寺），与 stat_district_makani 中的计数同一口径；供面板列表和地图定位
SET search_path TO stat, public;

CREATE TABLE map_poi (
    id            bigserial PRIMARY KEY,
    district_code varchar(64) NOT NULL,
    category      varchar(16) NOT NULL,
    source_id     varchar(64),
    name_en       varchar(512),
    name_ar       varchar(512),
    subtype       varchar(128),
    lon           double precision NOT NULL,
    lat           double precision NOT NULL
);
CREATE INDEX map_poi_district_idx ON map_poi (district_code, category);
COMMENT ON TABLE map_poi IS '公共设施点位；category = school / medical / mosque';

-- 清空 makani 产出的表，启动后自动重新抽取（带上设施点位）
DELETE FROM dim_district;
DELETE FROM stat_district_makani;
DELETE FROM stat_district_landuse;
DELETE FROM map_building;
DELETE FROM map_plot;
