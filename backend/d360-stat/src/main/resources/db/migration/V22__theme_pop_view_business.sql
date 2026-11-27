-- 业务视图接入（2026-11-27）：资本项目、建设许可、竣工 / 维修 / 拆除申请、居民诉求、道路事故、视觉违规、违规处罚、
-- 住宅占用、Tawtheeq 单元状态。源表在 ODS 库的 ods_pop_view schema（表名 ods_pop_view_<视图名>），由 ods-dev / ods-ops /
-- ods-mkt 抽取器读取；源表为空时主题表为空，接口返回 0 / 空列表，表登记 note 写明原因。
-- 只加列、加表，不改旧列：旧版程序不受影响。

-- 开发建设 · 资本项目（view_gsec_project_details + view_gsec_project_point / _polygon）-------------------------------
ALTER TABLE theme.dev_capital_project
    ADD COLUMN IF NOT EXISTS dof_no                  varchar(64),
    ADD COLUMN IF NOT EXISTS project_type            varchar(128),
    ADD COLUMN IF NOT EXISTS src_municipality        varchar(128),
    ADD COLUMN IF NOT EXISTS municipality_code       varchar(8),
    ADD COLUMN IF NOT EXISTS cip_location_plot       varchar(256),
    ADD COLUMN IF NOT EXISTS contractor_name         varchar(512),
    ADD COLUMN IF NOT EXISTS start_year              int,
    ADD COLUMN IF NOT EXISTS geom_kind               varchar(16),
    ADD COLUMN IF NOT EXISTS centroid                geometry(Point, 4326),
    ADD COLUMN IF NOT EXISTS delayed                 boolean,
    ADD COLUMN IF NOT EXISTS completed               boolean;
ALTER TABLE theme.dev_capital_project ALTER COLUMN actual_progress TYPE numeric(10, 2);
ALTER TABLE theme.dev_capital_project ALTER COLUMN revised_progress TYPE numeric(10, 2);
ALTER TABLE theme.dev_capital_project ALTER COLUMN original_progress TYPE numeric(10, 2);
ALTER TABLE theme.dev_capital_project ALTER COLUMN status TYPE varchar(128);
CREATE INDEX IF NOT EXISTS dev_capital_project_community_idx ON theme.dev_capital_project (community_key);
CREATE INDEX IF NOT EXISTS dev_capital_project_geom_gix ON theme.dev_capital_project USING gist (geom);
COMMENT ON COLUMN theme.dev_capital_project.delayed IS
    '延期：未完工，且 预测完工日 > 原计划完工日，或 实际进度比修订计划进度落后 10 个百分点以上';
COMMENT ON COLUMN theme.dev_capital_project.completed IS '已完工：project_status / project_phase 含 Complet / Closed';
COMMENT ON COLUMN theme.dev_capital_project.geom_kind IS 'point / polygon：几何取自哪张表（点优先）；都没有时按 cip_location_district 区名归属';

-- 开发建设 · 建设许可（view_emecs_dashboard）----------------------------------------------------------------------------
ALTER TABLE theme.dev_permit
    ADD COLUMN IF NOT EXISTS project_number          varchar(64),
    ADD COLUMN IF NOT EXISTS project_name            varchar(1024),
    ADD COLUMN IF NOT EXISTS owner_type              varchar(128),
    ADD COLUMN IF NOT EXISTS registration_date       date,
    ADD COLUMN IF NOT EXISTS work_start_date         date,
    ADD COLUMN IF NOT EXISTS expected_completion     varchar(64),
    ADD COLUMN IF NOT EXISTS land_use                varchar(256),
    ADD COLUMN IF NOT EXISTS zone                    varchar(128),
    ADD COLUMN IF NOT EXISTS sector                  varchar(128),
    ADD COLUMN IF NOT EXISTS plot_number             varchar(64),
    ADD COLUMN IF NOT EXISTS src_municipality        varchar(128),
    ADD COLUMN IF NOT EXISTS municipality_code       varchar(8),
    ADD COLUMN IF NOT EXISTS geom                    geometry(Point, 4326);
ALTER TABLE theme.dev_permit ALTER COLUMN permit_status TYPE varchar(128);
ALTER TABLE theme.dev_permit ALTER COLUMN project_status TYPE varchar(128);
ALTER TABLE theme.dev_permit ALTER COLUMN delayed_status TYPE varchar(128);
ALTER TABLE theme.dev_permit ALTER COLUMN gisid TYPE varchar(64);
CREATE INDEX IF NOT EXISTS dev_permit_community_idx ON theme.dev_permit (community_key);
CREATE INDEX IF NOT EXISTS dev_permit_issuing_idx ON theme.dev_permit (issuing_date);
COMMENT ON COLUMN theme.dev_permit.plot_key IS '先按 gisid 对 sp_plot.gisid，再按 zone + sector + plot_number，最后按坐标落地块';

-- 开发建设 · 建筑类申请（view_meps_cj_building_projects_dashboard）------------------------------------------------------
ALTER TABLE theme.dev_building_application
    ADD COLUMN IF NOT EXISTS project_number          varchar(64),
    ADD COLUMN IF NOT EXISTS project_name            varchar(1024),
    ADD COLUMN IF NOT EXISTS kind                    varchar(16),
    ADD COLUMN IF NOT EXISTS land_use_group          varchar(256),
    ADD COLUMN IF NOT EXISTS is_legalization         boolean,
    ADD COLUMN IF NOT EXISTS kpi_start_date          date,
    ADD COLUMN IF NOT EXISTS kpi_end_date            date,
    ADD COLUMN IF NOT EXISTS last_status_date        date,
    ADD COLUMN IF NOT EXISTS duration_days           int,
    ADD COLUMN IF NOT EXISTS src_municipality        varchar(128),
    ADD COLUMN IF NOT EXISTS municipality_code       varchar(8);
ALTER TABLE theme.dev_building_application ALTER COLUMN project_type TYPE varchar(128);
CREATE INDEX IF NOT EXISTS dev_building_application_district_idx ON theme.dev_building_application (district_key, submission_date);
CREATE INDEX IF NOT EXISTS dev_building_application_community_idx ON theme.dev_building_application (community_key);
COMMENT ON COLUMN theme.dev_building_application.kind IS
    'completion / maintenance / demolition / other：按 projecttypename 关键词归类（Completion / Maintenance、Repair / Demolition），待业务确认';

-- 城市运行 · 居民诉求（view_adda_cases）-------------------------------------------------------------------------------
ALTER TABLE theme.ops_case
    ADD COLUMN IF NOT EXISTS service_name            varchar(256),
    ADD COLUMN IF NOT EXISTS entity                  varchar(256),
    ADD COLUMN IF NOT EXISTS priority                varchar(64),
    ADD COLUMN IF NOT EXISTS channel                 varchar(64),
    ADD COLUMN IF NOT EXISTS closed                  boolean,
    ADD COLUMN IF NOT EXISTS municipality_code       varchar(8);
ALTER TABLE theme.ops_case ALTER COLUMN status TYPE varchar(128);
ALTER TABLE theme.ops_case ALTER COLUMN service_category TYPE varchar(256);
ALTER TABLE theme.ops_case ALTER COLUMN category1 TYPE varchar(256);
ALTER TABLE theme.ops_case ALTER COLUMN category2 TYPE varchar(256);
ALTER TABLE theme.ops_case ALTER COLUMN case_type TYPE varchar(256);
CREATE INDEX IF NOT EXISTS ops_case_community_idx ON theme.ops_case (community_key);
COMMENT ON COLUMN theme.ops_case.closed IS '已办结：date_closed 有值，或 status 含 Closed / Resolved / Completed';

-- 城市运行 · 道路事故（view_accident_master_new）------------------------------------------------------------------------
ALTER TABLE theme.ops_accident
    ADD COLUMN IF NOT EXISTS report_kind             varchar(128),
    ADD COLUMN IF NOT EXISTS street                  varchar(256),
    ADD COLUMN IF NOT EXISTS emirate                 varchar(128),
    ADD COLUMN IF NOT EXISTS municipality_code       varchar(8);
ALTER TABLE theme.ops_accident ALTER COLUMN severity TYPE varchar(128);
ALTER TABLE theme.ops_accident ALTER COLUMN accident_kind TYPE varchar(256);
CREATE INDEX IF NOT EXISTS ops_accident_community_idx ON theme.ops_accident (community_key);
CREATE INDEX IF NOT EXISTS ops_accident_geom_gix ON theme.ops_accident USING gist (geom);

-- 城市运行 · 视觉违规上报（view_ins_distortion）-------------------------------------------------------------------------
ALTER TABLE theme.ops_distortion
    ADD COLUMN IF NOT EXISTS org_id                  varchar(64),
    ADD COLUMN IF NOT EXISTS service_center          varchar(256),
    ADD COLUMN IF NOT EXISTS plot_gis_id             varchar(32),
    ADD COLUMN IF NOT EXISTS within_sla              boolean,
    ADD COLUMN IF NOT EXISTS municipality_code       varchar(8);
ALTER TABLE theme.ops_distortion ALTER COLUMN status TYPE varchar(128);
ALTER TABLE theme.ops_distortion ALTER COLUMN category TYPE varchar(128);
CREATE INDEX IF NOT EXISTS ops_distortion_community_idx ON theme.ops_distortion (community_key);
CREATE INDEX IF NOT EXISTS ops_distortion_plot_idx ON theme.ops_distortion (plot_key);
COMMENT ON COLUMN theme.ops_distortion.within_sla IS
    '按期整改：已关闭，且 关闭日 − 上报日（天）≤ maxtime2resolve（maxtime2resolve 为空时为 null，不计入分母）';
COMMENT ON COLUMN theme.ops_distortion.category IS '三类归类（基础设施 / 建筑外观 / 个人行为）待业务给出条目映射，目前为空；页面先按 service_name 分组';

-- 城市运行 · 违规与处罚（view_ins_violations + view_ins_violations_uis，地块经 view_ins_violation_trx_result）-------------
ALTER TABLE theme.ops_violation
    ADD COLUMN IF NOT EXISTS source                  varchar(8),
    ADD COLUMN IF NOT EXISTS issue_status            varchar(128),
    ADD COLUMN IF NOT EXISTS current_activity        varchar(256),
    ADD COLUMN IF NOT EXISTS zone_name               varchar(128),
    ADD COLUMN IF NOT EXISTS sector_name             varchar(128),
    ADD COLUMN IF NOT EXISTS plot_number             varchar(64),
    ADD COLUMN IF NOT EXISTS result                  varchar(256),
    ADD COLUMN IF NOT EXISTS municipality_code       varchar(8),
    ADD COLUMN IF NOT EXISTS geom                    geometry(Point, 4326);
ALTER TABLE theme.ops_violation ALTER COLUMN status TYPE varchar(128);
ALTER TABLE theme.ops_violation ALTER COLUMN payment_status TYPE varchar(128);
ALTER TABLE theme.ops_violation ALTER COLUMN law TYPE varchar(128);
CREATE INDEX IF NOT EXISTS ops_violation_community_idx ON theme.ops_violation (community_key);
COMMENT ON COLUMN theme.ops_violation.stage IS '当前处罚阶段：fine（有罚款或已转违规）/ warning / awareness';
COMMENT ON COLUMN theme.ops_violation.law IS '三部法律归类待业务给出条目映射，目前为空；页面先按 service_name 分组';
COMMENT ON COLUMN theme.ops_violation.source IS 'ins = view_ins_violations，uis = view_ins_violations_uis（同号以 ins 为准）';

-- 城市运行 · 住宅占用案件（view_ins_survey）----------------------------------------------------------------------------
ALTER TABLE theme.ops_occupancy_case
    ADD COLUMN IF NOT EXISTS raised_on               timestamp,
    ADD COLUMN IF NOT EXISTS closed_date             timestamp,
    ADD COLUMN IF NOT EXISTS service_name            varchar(256),
    ADD COLUMN IF NOT EXISTS violation_name          varchar(512),
    ADD COLUMN IF NOT EXISTS zone_name               varchar(128),
    ADD COLUMN IF NOT EXISTS sector_name             varchar(128),
    ADD COLUMN IF NOT EXISTS plot_number             varchar(64),
    ADD COLUMN IF NOT EXISTS municipality_code       varchar(8),
    ADD COLUMN IF NOT EXISTS geom                    geometry(Point, 4326);
ALTER TABLE theme.ops_occupancy_case ALTER COLUMN status TYPE varchar(128);
ALTER TABLE theme.ops_occupancy_case ALTER COLUMN current_activity TYPE varchar(256);
CREATE INDEX IF NOT EXISTS ops_occupancy_case_district_idx ON theme.ops_occupancy_case (district_key);
CREATE INDEX IF NOT EXISTS ops_occupancy_case_community_idx ON theme.ops_occupancy_case (community_key);
CREATE INDEX IF NOT EXISTS ops_occupancy_case_plot_idx ON theme.ops_occupancy_case (plot_key);

-- 市场 · Tawtheeq 单元状态（view_vw_tawtheeq_unit_status）：入住率、单元入住状态 --------------------------------------------
CREATE TABLE IF NOT EXISTS theme.mkt_unit_status (
    src_id             bigint       PRIMARY KEY,           -- 源 objectid
    gisid              varchar(32),
    tawtheeq_building_id bigint,
    tawtheeq_unit_id   bigint,
    unit_reg_num       varchar(64),
    unit_number        varchar(64),
    floor_number       varchar(32),
    building_name      varchar(512),
    usage_type         varchar(128),
    classification     varchar(128),
    bedrooms           int,
    area_sqm           numeric(14, 2),
    is_rented          boolean,
    blocked            boolean,
    unit_key           varchar(40),                        -- 经 municipality_code + unitid 对上 sp_unit 时才有
    building_key       varchar(40),
    plot_key           varchar(32),
    community_key      varchar(48),
    district_key       varchar(32),
    municipality_code  varchar(8),
    loaded_at          timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS mkt_unit_status_district_idx ON theme.mkt_unit_status (district_key);
CREATE INDEX IF NOT EXISTS mkt_unit_status_community_idx ON theme.mkt_unit_status (community_key);
CREATE INDEX IF NOT EXISTS mkt_unit_status_plot_idx ON theme.mkt_unit_status (plot_key);
CREATE INDEX IF NOT EXISTS mkt_unit_status_unit_idx ON theme.mkt_unit_status (unit_key);
COMMENT ON TABLE theme.mkt_unit_status IS
    'Tawtheeq 单元状态（一行一个单元）；源：view_vw_tawtheeq_unit_status。gisid → 地块；入住率 = is_rented 为真的单元 ÷ 单元数';
COMMENT ON COLUMN theme.mkt_unit_status.blocked IS '当前处于 hard / soft / general block 期间（开始日 ≤ 今天 ≤ 结束日或结束日为空）';
COMMENT ON COLUMN theme.mkt_unit_status.unit_key IS 'Tawtheeq unitid 与 BDMS unitid 是否同一套编号待确认；对得上（同市政同号）才填';

INSERT INTO theme.meta_table (table_name, theme, title_zh, source_tables, sort_no)
VALUES ('mkt_unit_status', '市场', 'Tawtheeq 单元状态', 'view_vw_tawtheeq_unit_status', 26)
ON CONFLICT (table_name) DO NOTHING;

UPDATE theme.meta_table SET extractor = 'ods-dev'
 WHERE table_name IN ('dev_capital_project', 'dev_permit', 'dev_building_application');
UPDATE theme.meta_table SET extractor = 'ods-ops'
 WHERE table_name IN ('ops_case', 'ops_accident', 'ops_distortion', 'ops_violation', 'ops_occupancy_case');
UPDATE theme.meta_table SET extractor = 'ods-mkt' WHERE table_name = 'mkt_unit_status';
