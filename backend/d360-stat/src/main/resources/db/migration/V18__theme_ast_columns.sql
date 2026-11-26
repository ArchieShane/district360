-- 设施主题接入（抽取器 ods-ast）：按 ODS 实际字段补列。
-- 源表（makani_1119 / pop 相同；与 v1 区情概览统计用的是同一批表，数字口径一致）：
--   makani_poi_adek_schools_locations  学校 817 条（786 个 ESIS 编号），自带 udm_gisid
--   makani_poi_doh_facilities          医疗 / 药房 4,684 条（名称含 test 的测试数据排除，与 v1 一致）
--   makani_mosque_awqaf                清真寺 2,476 条（字段为阿拉伯文）
--   makani_udm_park                    公园面 248 个（88 个 parkid，多部件公园）
--   makani_parks_details               DMT 公园明细 60 个（设施、开放时间、门票），并入所在公园
-- 数据清单里写的 view_lnd_adek_school / view_lnd_doh_facilities 在 pop 为 0 行，不用。
ALTER TABLE theme.ast_facility
    ADD COLUMN IF NOT EXISTS municipality_code  varchar(8),
    ADD COLUMN IF NOT EXISTS sub_category       varchar(128),
    ADD COLUMN IF NOT EXISTS status             varchar(64),
    ADD COLUMN IF NOT EXISTS is_public          boolean,
    ADD COLUMN IF NOT EXISTS students           int,
    ADD COLUMN IF NOT EXISTS staff              int,
    ADD COLUMN IF NOT EXISTS floors             int,
    ADD COLUMN IF NOT EXISTS park_id            varchar(32),
    ADD COLUMN IF NOT EXISTS gisid              varchar(32),
    ADD COLUMN IF NOT EXISTS attrs              jsonb,
    ADD COLUMN IF NOT EXISTS centroid           geometry(Point, 4326);
CREATE INDEX IF NOT EXISTS ast_facility_community_idx ON theme.ast_facility (community_key, category);
CREATE INDEX IF NOT EXISTS ast_facility_plot_idx ON theme.ast_facility (plot_key);
COMMENT ON COLUMN theme.ast_facility.category IS 'school 学校 / medical 医疗 / pharmacy 药房 / mosque 清真寺 / park 公园';
COMMENT ON COLUMN theme.ast_facility.subtype IS '学校：教育类型；医疗 / 药房：facility_type；清真寺：جامع（清真大寺）/ مسجد；公园：udm / dmt';
COMMENT ON COLUMN theme.ast_facility.capacity IS '学校：学位；医疗：床位；清真寺：可容纳礼拜人数';
COMMENT ON COLUMN theme.ast_facility.attrs IS '详情字段（课程、性别、年级、评级、执照、专科、开放时间、配套设施等），页面原样展示';

UPDATE theme.meta_table SET extractor = 'ods-ast', note = NULL,
       source_tables = 'makani_poi_adek_schools_locations, makani_poi_doh_facilities, makani_mosque_awqaf, makani_udm_park, makani_parks_details'
 WHERE table_name = 'ast_facility';
