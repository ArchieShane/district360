-- 升级前检查：ODS 源库里有没有 ODS 空间抽取（d360-ods，source = ods-spatial）要用的字段，类型是否兼容（只读，不改任何数据）。
-- 在 ODS 库里执行。使用前按 config/application-prod.yml 的 d360.ods.schema / table-prefix 改 params（默认 ods / ods_）。
--
-- 结果为空 = 全部通过。有结果 = 列出的字段缺失或类型不兼容，ods-spatial 抽取会失败（主题库保持旧数据），发给开发处理。
-- 注意：只检查字段，不检查行数；表为空时抽取照常成功，对应层级没有数据（如建筑由 BDMS 推导）。
WITH params AS (
    SELECT 'ods'::text AS schema, 'ods_'::text AS table_prefix
),
expected(tbl, col, kind) AS (VALUES
    -- makani_uae_all_municipalities
    ('makani_uae_all_municipalities', 'shape', 'geom'),
    ('makani_uae_all_municipalities', 'municipalityid', 'num'),
    ('makani_uae_all_municipalities', 'nameen', 'text'),
    ('makani_uae_all_municipalities', 'namear', 'text'),
    -- makani_udm_district
    ('makani_udm_district', 'shape', 'geom'),
    ('makani_udm_district', 'districtid', 'num'),
    ('makani_udm_district', 'municipalityname', 'text'),
    ('makani_udm_district', 'objectid', 'num'),
    ('makani_udm_district', 'nameenglish', 'text'),
    ('makani_udm_district', 'namearabic', 'text'),
    ('makani_udm_district', 'namepopularenglish', 'text'),
    ('makani_udm_district', 'population', 'num'),
    ('makani_udm_district', 'trs_moye_input_time', 'date'),
    -- makani_udm_community
    ('makani_udm_community', 'shape', 'geom'),
    ('makani_udm_community', 'municipality', 'text'),
    ('makani_udm_community', 'districtid', 'text'),
    ('makani_udm_community', 'communityid', 'text'),
    ('makani_udm_community', 'objectid', 'num'),
    ('makani_udm_community', 'communitynameeng', 'text'),
    ('makani_udm_community', 'communitynameara', 'text'),
    ('makani_udm_community', 'commpopnameeng', 'text'),
    ('makani_udm_community', 'comnamecompositeeng', 'text'),
    -- makani_udm_plot
    ('makani_udm_plot', 'shape', 'geom'),
    ('makani_udm_plot', 'gisid', 'text'),
    ('makani_udm_plot', 'plotid', 'text'),
    ('makani_udm_plot', 'plotnumber', 'text'),
    ('makani_udm_plot', 'elms_plotid', 'text'),
    ('makani_udm_plot', 'municipalityname', 'text'),
    ('makani_udm_plot', 'municipalityeng', 'text'),
    ('makani_udm_plot', 'districtid', 'text'),
    ('makani_udm_plot', 'districteng', 'text'),
    ('makani_udm_plot', 'communityid', 'text'),
    ('makani_udm_plot', 'communityeng', 'text'),
    ('makani_udm_plot', 'calculatedarea', 'num'),
    ('makani_udm_plot', 'plannedareametric', 'num'),
    ('makani_udm_plot', 'construction_status', 'text'),
    ('makani_udm_plot', 'servicestatus', 'text'),
    ('makani_udm_plot', 'planningstatus', 'text'),
    ('makani_udm_plot', 'status', 'text'),
    ('makani_udm_plot', 'primaryuseengdesc', 'text'),
    ('makani_udm_plot', 'elms_parentlanduse_e', 'text'),
    ('makani_udm_plot', 'elms_landusename_e', 'text'),
    ('makani_udm_plot', 'secuseeng', 'text'),
    ('makani_udm_plot', 'ownershiptype', 'text'),
    ('makani_udm_plot', 'elms_allocationstatus', 'text'),
    ('makani_udm_plot', 'investment_name', 'text'),
    ('makani_udm_plot', 'devcode', 'text'),
    ('makani_udm_plot', 'devcode_category', 'text'),
    ('makani_udm_plot', 'devcode_far', 'text'),
    ('makani_udm_plot', 'devcode_maxgfa', 'num'),
    ('makani_udm_plot', 'maxallowableheights', 'text'),
    ('makani_udm_plot', 'zonetpss', 'text'),
    ('makani_udm_plot', 'sectortpss', 'text'),
    ('makani_udm_plot', 'projectid', 'text'),
    ('makani_udm_plot', 'permit_lasttype', 'text'),
    ('makani_udm_plot', 'permit_lastdate', 'date'),
    ('makani_udm_plot', 'canceldate', 'date'),
    ('makani_udm_plot', 'modifieddate', 'date'),
    ('makani_udm_plot', 'datareceiveddate', 'date'),
    -- makani_udm_building
    ('makani_udm_building', 'shape', 'geom'),
    ('makani_udm_building', 'objectid', 'num'),
    ('makani_udm_building', 'buildingid', 'text'),
    ('makani_udm_building', 'buildinginternalid', 'num'),
    ('makani_udm_building', 'gisid', 'text'),
    ('makani_udm_building', 'elms_plotid', 'text'),
    ('makani_udm_building', 'nameenglish', 'text'),
    ('makani_udm_building', 'namearabic', 'text'),
    ('makani_udm_building', 'physicalstatus', 'text'),
    ('makani_udm_building', 'primaryusagetype', 'text'),
    ('makani_udm_building', 'primaryuseengdesc', 'text'),
    ('makani_udm_building', 'secondaryusagetype', 'text'),
    ('makani_udm_building', 'buildingnumberoffloors', 'num'),
    ('makani_udm_building', 'buildingheight', 'num'),
    ('makani_udm_building', 'completiondate', 'date'),
    ('makani_udm_building', 'demolitiondate', 'date'),
    ('makani_udm_building', 'startdate', 'date'),
    ('makani_udm_building', 'permitid', 'num'),
    ('makani_udm_building', 'constructionsiteid', 'num'),
    ('makani_udm_building', 'is_bdms', 'text'),
    -- makani_bdms_floors_height
    ('makani_bdms_floors_height', 'shape', 'geom'),
    ('makani_bdms_floors_height', 'floorid', 'num'),
    ('makani_bdms_floors_height', 'buildingid', 'num'),
    ('makani_bdms_floors_height', 'floornumber', 'text'),
    ('makani_bdms_floors_height', 'floor_baseheight', 'num'),
    ('makani_bdms_floors_height', 'floorheight', 'num'),
    ('makani_bdms_floors_height', 'area_sqm', 'num'),
    ('makani_bdms_floors_height', 'bldg_buildingstatus', 'text'),
    ('makani_bdms_floors_height', 'plots_primaryuseeng', 'text'),
    -- makani_bdms_units_height
    ('makani_bdms_units_height', 'shape', 'geom'),
    ('makani_bdms_units_height', 'unitid', 'num'),
    ('makani_bdms_units_height', 'floorid', 'num'),
    ('makani_bdms_units_height', 'buildingid', 'num'),
    ('makani_bdms_units_height', 'unitnumber', 'text'),
    ('makani_bdms_units_height', 'unittype', 'text'),
    ('makani_bdms_units_height', 'unitusagetype', 'text'),
    ('makani_bdms_units_height', 'unitclassification', 'text'),
    ('makani_bdms_units_height', 'unitcategory', 'text'),
    ('makani_bdms_units_height', 'unitarea', 'num'),
    ('makani_bdms_units_height', 'unitnumbedroom', 'num'),
    ('makani_bdms_units_height', 'unitnumbathroom', 'num'),
    ('makani_bdms_units_height', 'unitbalconiesarea', 'num'),
    ('makani_bdms_units_height', 'floornumber', 'text'),
    ('makani_bdms_units_height', 'floor_baseheight', 'num'),
    ('makani_bdms_units_height', 'floorheight', 'num'),
    ('makani_bdms_units_height', 'bldg_buildingstatus', 'text')
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
