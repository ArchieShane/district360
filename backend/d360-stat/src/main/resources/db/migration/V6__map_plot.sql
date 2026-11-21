-- 地块形状（EPSG:3857），供地图选中区后按建设状态高亮（矢量瓦片）
SET search_path TO stat, public;

CREATE TABLE map_plot (
    id            bigserial PRIMARY KEY,
    district_code varchar(64) NOT NULL,
    plot_id       varchar(64),
    status        varchar(24) NOT NULL,
    use_desc      varchar(64),
    geom          geometry(MultiPolygon, 3857) NOT NULL
);
CREATE INDEX map_plot_geom_gix ON map_plot USING gist (geom);
CREATE INDEX map_plot_code_idx ON map_plot (district_code);
COMMENT ON TABLE map_plot IS '地块形状；status = constructed / under_construction / vacant_serviced / vacant_unserviced / other';

-- 清空 makani 产出的表，启动后自动重新抽取（带上地块）
DELETE FROM dim_district;
DELETE FROM stat_district_makani;
DELETE FROM stat_district_landuse;
DELETE FROM map_building;
