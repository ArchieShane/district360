-- 统计范围：makani 区面原始多边形（未简化）。三个库都按这个范围做空间查询，保证口径一致。
-- boundary 是简化后的，只用于地图展示。
SET search_path TO stat, public;

ALTER TABLE dim_district ADD COLUMN scope geometry(MultiPolygon, 4326);
COMMENT ON COLUMN dim_district.scope IS '统计范围：makani udm_district 原始区面（未简化），宜居 / ADREC 抽取按此范围空间查询';
CREATE INDEX dim_district_scope_gix ON dim_district USING gist (scope);
