-- 城市运行主题接入（抽取器 ods-ops）：按 ODS 实际字段补列。
-- 有数据的源表（makani_1119 / pop 相同）：
--   makani_adwea_e_streetlight  124,961 个路灯点（EPSG:32640，抽取时转 4326）
--   makani_bus_stop             3,338 个站台点（1,949 个站，gtfs_id 是站、code 是站台；EPSG:32640）
-- 居民诉求 / 道路事故 / 视觉违规 / 违规处罚 / 住宅占用的源视图在 pop 为 0 行、makani_1119 里没有，暂不抽取，
-- 抽取器每次检查源表，结果写在 meta_table.note。

-- 路灯 ------------------------------------------------------------------------------------------------
ALTER TABLE theme.ops_streetlight
    ADD COLUMN IF NOT EXISTS lamp_type          varchar(32),
    ADD COLUMN IF NOT EXISTS road_type          varchar(16),
    ADD COLUMN IF NOT EXISTS fixture_height_m   numeric(8, 2),
    ADD COLUMN IF NOT EXISTS installation_date  date,
    ADD COLUMN IF NOT EXISTS entity             varchar(32),
    ADD COLUMN IF NOT EXISTS municipality_code  varchar(8);
CREATE INDEX IF NOT EXISTS ops_streetlight_community_idx ON theme.ops_streetlight (community_key);
CREATE INDEX IF NOT EXISTS ops_streetlight_geom_gix ON theme.ops_streetlight USING gist (geom);
COMMENT ON COLUMN theme.ops_streetlight.status IS '源 statusindicator：In Service / Out Of Service / UnderConst / Proposed / Abandon';

-- 公交站（一行 = 一个站台）--------------------------------------------------------------------------------
ALTER TABLE theme.ops_bus_stop DROP CONSTRAINT IF EXISTS ops_bus_stop_pkey;
ALTER TABLE theme.ops_bus_stop ALTER COLUMN gtfs_id DROP NOT NULL;
ALTER TABLE theme.ops_bus_stop
    ADD COLUMN IF NOT EXISTS id                 bigint,
    ADD COLUMN IF NOT EXISTS code               varchar(32),
    ADD COLUMN IF NOT EXISTS name_ar            varchar(256),
    ADD COLUMN IF NOT EXISTS stop_number        int,
    ADD COLUMN IF NOT EXISTS point_type         int,
    ADD COLUMN IF NOT EXISTS municipality_code  varchar(8);
DELETE FROM theme.ops_bus_stop WHERE id IS NULL;
ALTER TABLE theme.ops_bus_stop ADD PRIMARY KEY (id);
CREATE INDEX IF NOT EXISTS ops_bus_stop_community_idx ON theme.ops_bus_stop (community_key);
CREATE INDEX IF NOT EXISTS ops_bus_stop_geom_gix ON theme.ops_bus_stop USING gist (geom);
COMMENT ON TABLE theme.ops_bus_stop IS '公交站台（一行一个站台，主键为源 objectid）；gtfs_id 相同的是同一个站；源：makani_bus_stop';

UPDATE theme.meta_table SET extractor = 'ods-ops' WHERE table_name LIKE 'ops\_%';
