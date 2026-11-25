-- BDMS 编号只在市政内唯一：ADM / AAM / WRM 三套 BDMS 各自编号（makani_1119 核对：buildingid 3.4 万个、floorid 5.4 万个、
-- unitid 5.6 万个跨市政重复）。原先只用编号作键，把不同市政的楼合成一栋（外包面横跨几百公里）、重复的楼层 / 单元被丢弃。
-- 改为 (市政, 编号)：新增 bdms_municipality 列；楼层 / 单元键改为 <市政>-<编号>，BDMS 推导建筑键改为 bdms-<市政>-<编号>。
-- 键的拼法在抽取器里，表结构只加列；升级后需重新执行一次 ods-spatial 抽取。
ALTER TABLE theme.sp_building ADD COLUMN IF NOT EXISTS bdms_municipality varchar(8);
ALTER TABLE theme.sp_floor    ADD COLUMN IF NOT EXISTS bdms_municipality varchar(8);
ALTER TABLE theme.sp_unit     ADD COLUMN IF NOT EXISTS bdms_municipality varchar(8);

COMMENT ON COLUMN theme.sp_building.bdms_municipality IS 'BDMS 编号所属市政（municipalityeng）；BDMS 编号只在市政内唯一';
COMMENT ON COLUMN theme.sp_floor.bdms_municipality IS 'BDMS 编号所属市政（municipalityeng）；floor_key = <市政>-<floorid>';
COMMENT ON COLUMN theme.sp_unit.bdms_municipality IS 'BDMS 编号所属市政（municipalityeng）；unit_key = <市政>-<unitid>';

DROP INDEX IF EXISTS theme.sp_building_bdms_idx;
DROP INDEX IF EXISTS theme.sp_floor_bdms_idx;
DROP INDEX IF EXISTS theme.sp_unit_bdms_idx;
CREATE INDEX sp_building_bdms_idx ON theme.sp_building (bdms_municipality, bdms_building_id);
CREATE INDEX sp_floor_bdms_idx ON theme.sp_floor (bdms_municipality, bdms_building_id);
CREATE INDEX sp_unit_bdms_idx ON theme.sp_unit (bdms_municipality, bdms_building_id);

UPDATE theme.meta_table SET extractor = 'ods-spatial, ods-units' WHERE table_name = 'sp_unit';
