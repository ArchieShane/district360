-- 地图点击详情：地块 / 建筑 / 公共设施各加一列 attrs（makani 源表里供展示的字段，抽取时拼成 JSON）。
-- 键名与前端详情卡片的字段列表一一对应；没有值的键不写。
SET search_path TO stat, public;

ALTER TABLE map_plot ADD COLUMN attrs jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE map_building ADD COLUMN attrs jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE map_poi ADD COLUMN attrs jsonb NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN map_plot.attrs IS '地块详情字段（makani udm_plot），供地图点击详情';
COMMENT ON COLUMN map_building.attrs IS '建筑详情字段（makani udm_building），供地图点击详情';
COMMENT ON COLUMN map_poi.attrs IS '设施详情字段（学校 poi_adek_schools_locations / 医疗 poi_doh_facilities），供地图点击详情';

-- 不清表：统计库有其他实例在读，清空会让页面在重新抽取完成前没有数据。
-- 升级后手动触发一次 makani 抽取（POST /api/v1/admin/etl/makani），或等每天凌晨的定时抽取，详情字段才会有值；
-- 抽取是单事务整批替换，期间页面照常可用。
