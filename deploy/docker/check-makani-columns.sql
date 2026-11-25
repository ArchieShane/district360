-- 升级前检查：makani 源库里有没有 makani 抽取要用的字段，类型是否兼容（只读，不改任何数据）。
-- 在离线服务器的 makani 源库里执行（psql 或 Navicat 均可）。
--
-- 使用前先按 config/application-prod.yml 里的 d360.makani 两项改下面 params 里的值：
--   schema       = d360.makani.schema        （生产 / 全量库一般是 public）
--   table_prefix = d360.makani.table-prefix  （生产 / 全量库一般为空串 ''）
--
-- 结果为空 = 全部通过，可以升级。
-- 有结果 = 列出的字段缺失或类型不兼容，升级后 makani 抽取会失败（统计库保持旧数据，页面照常，但没有点击详情）；
--          把结果发给开发处理后再升级。
WITH params AS (
    SELECT 'public'::text AS schema, ''::text AS table_prefix
),
expected(tbl, col, kind) AS (VALUES
    -- 建筑 udm_building
    ('udm_building', 'shape', 'geom'),
    ('udm_building', 'buildingid', 'text'),
    ('udm_building', 'buildingheight', 'num'),
    ('udm_building', 'buildingnumberoffloors', 'num'),
    ('udm_building', 'physicalstatus', 'text'),
    ('udm_building', 'nameenglish', 'text'),
    ('udm_building', 'namearabic', 'text'),
    ('udm_building', 'namepopularenglish', 'text'),
    ('udm_building', 'primaryusagetype', 'text'),
    ('udm_building', 'primaryuseengdesc', 'text'),
    ('udm_building', 'secondaryusagetype', 'text'),
    ('udm_building', 'communitynameeng', 'text'),
    ('udm_building', 'completiondate', 'date'),
    -- 地块 udm_plot
    ('udm_plot', 'shape', 'geom'),
    ('udm_plot', 'plotid', 'text'),
    ('udm_plot', 'construction_status', 'text'),
    ('udm_plot', 'servicestatus', 'text'),
    ('udm_plot', 'primaryuseengdesc', 'text'),
    ('udm_plot', 'sectornumber_plotid', 'text'),
    ('udm_plot', 'communityeng', 'text'),
    ('udm_plot', 'plotcalculatedarea', 'num'),
    ('udm_plot', 'elms_landusename_e', 'text'),
    ('udm_plot', 'secuseeng', 'text'),
    ('udm_plot', 'planningstatus', 'text'),
    ('udm_plot', 'elms_allocationstatus', 'text'),
    ('udm_plot', 'elms_plotallocation_name_e', 'text'),
    ('udm_plot', 'ownershiptype', 'text'),
    ('udm_plot', 'devcode', 'text'),
    ('udm_plot', 'devcode_far', 'text'),
    ('udm_plot', 'devcode_maxgfa', 'num'),
    ('udm_plot', 'maxallowableheights', 'text'),
    ('udm_plot', 'investment_name', 'text'),
    ('udm_plot', 'permit_lasttype', 'text'),
    ('udm_plot', 'permit_lastdate', 'date'),
    -- 学校 poi_adek_schools_locations
    ('poi_adek_schools_locations', 'shape', 'geom'),
    ('poi_adek_schools_locations', 'adek_sch_name_en', 'any'),
    ('poi_adek_schools_locations', 'adek_sch_name_ar', 'any'),
    ('poi_adek_schools_locations', 'adek_sch_education_type_en', 'any'),
    ('poi_adek_schools_locations', 'adek_sch_curriculum_en', 'text'),
    ('poi_adek_schools_locations', 'adek_sch_gender_en', 'text'),
    ('poi_adek_schools_locations', 'adek_sch_grades_en', 'text'),
    ('poi_adek_schools_locations', 'adek_sch_cycle_en', 'text'),
    ('poi_adek_schools_locations', 'adek_n_students', 'any'),
    ('poi_adek_schools_locations', 'adek_sch_capacity', 'num'),
    ('poi_adek_schools_locations', 'adek_n_teaching_staff', 'any'),
    ('poi_adek_schools_locations', 'adek_n_total_staff', 'any'),
    ('poi_adek_schools_locations', 'adek_sch_latest_irtqaa_result', 'text'),
    ('poi_adek_schools_locations', 'adek_sch_tuition_fee_category', 'text'),
    ('poi_adek_schools_locations', 'adek_sch_average_tuition_fee', 'num'),
    ('poi_adek_schools_locations', 'adek_sch_academic_year', 'text'),
    ('poi_adek_schools_locations', 'adek_sch_community_en', 'text'),
    ('poi_adek_schools_locations', 'adek_sch_esis_id', 'text'),
    -- 医疗 poi_doh_facilities
    ('poi_doh_facilities', 'shape', 'geom'),
    ('poi_doh_facilities', 'facility_category', 'any'),
    ('poi_doh_facilities', 'facility_name_in_english', 'text'),
    ('poi_doh_facilities', 'facility_name_in_arabic', 'any'),
    ('poi_doh_facilities', 'facility_type', 'any'),
    ('poi_doh_facilities', 'facility_sub_type', 'text'),
    ('poi_doh_facilities', 'specialities_subspecialities', 'text'),
    ('poi_doh_facilities', 'is_public', 'text'),
    ('poi_doh_facilities', 'bed_capacity', 'text'),
    ('poi_doh_facilities', 'emergency_level', 'text'),
    ('poi_doh_facilities', 'address_name_in_english', 'text'),
    ('poi_doh_facilities', 'facility_license_number', 'text'),
    ('poi_doh_facilities', 'permanent_status', 'text'),
    ('poi_doh_facilities', 'date_expiration', 'date')
),
actual AS (
    SELECT c.table_name, c.column_name, c.data_type, c.udt_name
    FROM information_schema.columns c, params p
    WHERE c.table_schema = p.schema
)
SELECT p.schema || '.' || p.table_prefix || e.tbl AS 表,
       e.col                                       AS 字段,
       CASE WHEN a.column_name IS NULL THEN '缺少字段' ELSE '类型不兼容' END AS 问题,
       CASE e.kind
           WHEN 'text' THEN '文本（varchar / text）'
           WHEN 'num'  THEN '数值（integer / numeric / double）'
           WHEN 'date' THEN '日期或时间（date / timestamp）'
           WHEN 'geom' THEN 'geometry'
           ELSE '任意类型'
       END                                         AS 需要的类型,
       coalesce(a.data_type, '—')                  AS 实际类型
FROM expected e
CROSS JOIN params p
LEFT JOIN actual a ON a.table_name = p.table_prefix || e.tbl AND a.column_name = e.col
WHERE a.column_name IS NULL
   OR (e.kind = 'text' AND a.data_type NOT IN ('character varying', 'text', 'character'))
   OR (e.kind = 'num'  AND a.data_type NOT IN ('integer', 'bigint', 'smallint', 'numeric', 'double precision', 'real'))
   OR (e.kind = 'date' AND a.data_type NOT IN ('date', 'timestamp without time zone', 'timestamp with time zone'))
   OR (e.kind = 'geom' AND a.udt_name <> 'geometry')
ORDER BY 1, 2;
