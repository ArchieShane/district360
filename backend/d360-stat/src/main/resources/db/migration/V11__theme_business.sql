-- District 360 V1.0 主题库（schema: theme）· 业务主题表（开发建设 / 市场 / 城市运行 / 资产与设施 / 宜居）
--
-- 依据：District360_数据清单与指标关联_20261124_v2.xlsx 的「数据清单」（用到的字段）与原型 V1.0 各页签。
-- 粒度：一律为「明细记录 + 空间归属编码」，不在主题库做区级汇总——原型需要按区域 / 社区 / 地块 / 建筑逐级查看，
-- 且地块、建筑页要列出明细（许可、交易、违规、工单……）。区级统计在读接口或统计库（stat）里按编码聚合。
--
-- 空间归属（district_key / community_key / plot_key / building_key / unit_key）在抽取后由主题库统一回填：
--   有 gisid 的按 gisid → sp_plot；有坐标 / 几何的按点（面取内点）落在 sp_plot / sp_community / sp_district 面内；
--   只有文本区名 / 社区名的（ADREC）按名称映射，映射不上的保留原文，归属为空。
-- 本迁移只建表；各主题的抽取器后续逐个实现（theme.meta_table.extractor 为空即「未接入」）。
CREATE SCHEMA IF NOT EXISTS theme;

-- ================================================================================================
-- 开发建设
-- ================================================================================================
CREATE TABLE theme.dev_masterplan (
    project_id         varchar(64)  PRIMARY KEY,
    project_name       varchar(512),
    stage              varchar(128),                      -- stage_dm
    approval_year      int,
    developer_name     varchar(256),
    src_district_id    varchar(32),
    src_community_id   varchar(32),
    district_key       varchar(32),
    community_key      varchar(48),
    geom               geometry(MultiPolygon, 4326),
    loaded_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX dev_masterplan_district_idx ON theme.dev_masterplan (district_key);
CREATE INDEX dev_masterplan_geom_gix ON theme.dev_masterplan USING gist (geom);
COMMENT ON TABLE theme.dev_masterplan IS '总体规划项目边界；源：makani_masterplan_ud_masterplan_boundary';

CREATE TABLE theme.dev_masterplan_plot (
    id                     bigserial PRIMARY KEY,
    project_id             varchar(64),
    udm_gisid              varchar(32),
    plot_key               varchar(32),
    district_key           varchar(32),
    community_key          varchar(48),
    total_gfa              numeric(16, 2),
    residential_gfa        numeric(16, 2),
    office_gfa             numeric(16, 2),
    retail_gfa             numeric(16, 2),
    hotel_gfa              numeric(16, 2),
    industrial_gfa         numeric(16, 2),
    total_residential_units int,
    loaded_at              timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX dev_masterplan_plot_project_idx ON theme.dev_masterplan_plot (project_id);
CREATE INDEX dev_masterplan_plot_plot_idx ON theme.dev_masterplan_plot (plot_key);
COMMENT ON TABLE theme.dev_masterplan_plot IS '总规地块管控指标（规划建筑面积、规划住宅单元）；源：makani_masterplan_ud_plot；udm_gisid = sp_plot.gisid';

CREATE TABLE theme.dev_capital_project (
    project_id               varchar(64) PRIMARY KEY,
    project_name             varchar(1024),
    category                 varchar(128),               -- cip_project_category
    phase                    varchar(128),
    status                   varchar(64),
    approved_budget          numeric(18, 2),
    cumulative_expenditures  numeric(18, 2),
    original_planned_com_date date,
    actual_forecast_com_date date,
    actual_progress          numeric(6, 2),
    revised_progress         numeric(6, 2),
    original_progress        numeric(6, 2),
    cip_location_district    varchar(256),
    cip_location_community   varchar(256),
    district_key             varchar(32),
    community_key            varchar(48),
    geom                     geometry(Geometry, 4326),   -- 点 / 面（view_gsec_project_point / _polygon）
    loaded_at                timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX dev_capital_project_district_idx ON theme.dev_capital_project (district_key);
COMMENT ON TABLE theme.dev_capital_project IS '资本项目（CIP）；源：view_gsec_project_details + view_gsec_project_point / polygon';

CREATE TABLE theme.dev_permit (
    permit_number            varchar(64) PRIMARY KEY,
    issuing_date             date,
    gisid                    varchar(32),
    plot_key                 varchar(32),
    community_key            varchar(48),
    district_key             varchar(32),
    permit_status            varchar(64),
    project_status           varchar(64),
    project_completion_date  date,
    delayed_status           varchar(64),
    delayed_variance_days    int,
    loaded_at                timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX dev_permit_plot_idx ON theme.dev_permit (plot_key);
CREATE INDEX dev_permit_district_idx ON theme.dev_permit (district_key);
COMMENT ON TABLE theme.dev_permit IS '建设许可；源：view_emecs_dashboard（gisid → 地块）';

CREATE TABLE theme.dev_building_application (
    id                    bigserial PRIMARY KEY,
    project_type          varchar(64),
    project_type_name     varchar(256),                  -- 竣工证书 / 维修许可 / 拆除许可 / 改建许可 ...
    current_status        varchar(128),
    submission_date       date,
    zone                  varchar(64),
    sector                varchar(64),
    plot_number           varchar(64),
    plot_key              varchar(32),
    community_key         varchar(48),
    district_key          varchar(32),
    loaded_at             timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX dev_building_application_plot_idx ON theme.dev_building_application (plot_key);
COMMENT ON TABLE theme.dev_building_application IS '建筑类申请（竣工证书、维修、拆除、改建）；源：view_meps_cj_building_projects_dashboard（zone + sector + plot_number → 地块，口径待确认）';

CREATE TABLE theme.dev_construction_site (
    site_id        varchar(32) PRIMARY KEY,
    district_key   varchar(32),
    community_key  varchar(48),
    plot_key       varchar(32),
    geom           geometry(MultiPolygon, 4326),
    loaded_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX dev_construction_site_geom_gix ON theme.dev_construction_site USING gist (geom);
COMMENT ON TABLE theme.dev_construction_site IS '在建工地；源：makani_udm_constructionsite';

-- ================================================================================================
-- 市场（ADREC 不动产 + Tawtheeq 租约）
-- ================================================================================================
CREATE TABLE theme.mkt_sale (
    sale_application_id  varchar(64) PRIMARY KEY,
    contract_date        date,
    price_aed            numeric(18, 2),
    sold_area_sqm        numeric(14, 2),
    rate_aed_sqm         numeric(14, 2),
    src_municipality     varchar(128),
    src_district         varchar(256),                   -- 文本，按名称映射到 district_key
    src_community        varchar(256),
    land_plot            varchar(128),
    building_id          varchar(64),
    district_key         varchar(32),
    community_key        varchar(48),
    plot_key             varchar(32),
    loaded_at            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX mkt_sale_district_idx ON theme.mkt_sale (district_key, contract_date);
COMMENT ON TABLE theme.mkt_sale IS '房产买卖；源：re_x_q21_matview_sale';

CREATE TABLE theme.mkt_mortgage (
    contract_id          varchar(64) PRIMARY KEY,        -- orientational_mortgage_contract_id
    start_date           date,
    amount_aed           numeric(18, 2),                 -- mortgage_change_amount_aed
    general_category     varchar(128),
    src_municipality     varchar(128),
    src_district         varchar(256),
    src_community        varchar(256),
    district_key         varchar(32),
    community_key        varchar(48),
    loaded_at            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX mkt_mortgage_district_idx ON theme.mkt_mortgage (district_key, start_date);
COMMENT ON TABLE theme.mkt_mortgage IS '房产抵押；源：re_x_q21_matview_mortgage';

CREATE TABLE theme.mkt_rent (
    contract_number      varchar(64) PRIMARY KEY,
    start_date           date,
    annual_value_aed     numeric(18, 2),
    rented_area_sqm      numeric(14, 2),
    application_type     varchar(128),
    contract_status      varchar(128),
    src_municipality     varchar(128),
    src_district         varchar(256),
    src_community        varchar(256),
    district_key         varchar(32),
    community_key        varchar(48),
    loaded_at            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX mkt_rent_district_idx ON theme.mkt_rent (district_key, start_date);
COMMENT ON TABLE theme.mkt_rent IS '租赁合同；源：re_x_q21_matview_rent';

CREATE TABLE theme.mkt_plot_transaction (
    id             bigserial PRIMARY KEY,
    kind           varchar(16) NOT NULL,                 -- sale / mortgage
    elms_plot_id   varchar(64),
    amount_aed     numeric(18, 2),
    plot_key       varchar(32),
    loaded_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX mkt_plot_transaction_plot_idx ON theme.mkt_plot_transaction (plot_key);
COMMENT ON TABLE theme.mkt_plot_transaction IS '地块级交易记录；源：re_elms_buy_and_sell_f（sale）、re_elms_mortgages_f（mortgage）；elms_plot_id → sp_plot.elms_plot_id';

CREATE TABLE theme.mkt_tenancy (
    tenancy_id           varchar(64) PRIMARY KEY,
    tawtheeq_unit_id     varchar(64),
    tawtheeq_property_id varchar(64),
    bdms_unit_id         bigint,                         -- 经 makani_tbl_bdms_tawtheeq_link_data 桥接
    unit_key             varchar(40),
    building_key         varchar(40),
    gisid                varchar(32),
    plot_key             varchar(32),
    community_key        varchar(48),
    district_key         varchar(32),
    tenancy_status       varchar(64),
    unit_status          varchar(64),
    is_rented            boolean,                        -- view_vw_tawtheeq_unit_status
    tenancy_end_date     date,
    rental_value_aed     numeric(18, 2),
    loaded_at            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX mkt_tenancy_unit_idx ON theme.mkt_tenancy (unit_key);
CREATE INDEX mkt_tenancy_building_idx ON theme.mkt_tenancy (building_key);
COMMENT ON TABLE theme.mkt_tenancy IS 'Tawtheeq 租约（入住率、单元入住状态、租约到期）；源：makani_tbl_tawtheeq_all + makani_tbl_bdms_tawtheeq_link_data + view_vw_tawtheeq_unit_status';

-- ================================================================================================
-- 城市运行
-- ================================================================================================
CREATE TABLE theme.ops_case (
    case_number       varchar(64) PRIMARY KEY,
    date_opened       timestamp,
    date_closed       timestamp,
    status            varchar(64),
    sla_color         varchar(32),
    sla_compliance    varchar(32),                       -- flood_w_adda_slacomplaince_d
    csat_score        numeric(4, 2),                     -- flood_adda_csat_d q1..q4 平均
    service_category  varchar(128),
    category1         varchar(128),
    category2         varchar(128),
    case_type         varchar(128),
    area_en           varchar(256),
    plot_key          varchar(32),
    community_key     varchar(48),
    district_key      varchar(32),
    geom              geometry(Point, 4326),
    loaded_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ops_case_district_idx ON theme.ops_case (district_key, date_opened);
CREATE INDEX ops_case_geom_gix ON theme.ops_case USING gist (geom);
COMMENT ON TABLE theme.ops_case IS '居民诉求工单（ADDA）；源：view_adda_cases + flood_w_adda_slacomplaince_d + flood_adda_csat_d';

CREATE TABLE theme.ops_accident (
    report_no      varchar(64) PRIMARY KEY,
    report_date    timestamp,
    severity       varchar(64),                          -- pv_fls_desc_en
    accident_kind  varchar(128),
    community_key  varchar(48),
    district_key   varchar(32),
    geom           geometry(Point, 4326),
    loaded_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ops_accident_district_idx ON theme.ops_accident (district_key, report_date);
COMMENT ON TABLE theme.ops_accident IS '道路事故；源：view_accident_master_new';

CREATE TABLE theme.ops_distortion (
    issue_number              varchar(64) PRIMARY KEY,
    raised_on                 timestamp,
    closed_date               timestamp,
    max_time_to_resolve       numeric(10, 2),
    days_passed               numeric(10, 2),
    status                    varchar(64),
    is_duplicate              boolean,
    is_awareness              boolean,
    converted_to_warning      boolean,
    converted_to_violation    boolean,
    service_name              varchar(256),
    violation_name            varchar(512),
    category                  varchar(64),               -- 基础设施 / 建筑外观 / 个人行为（按条目归类）
    responsible_entity        varchar(256),              -- organization / view_uis_v_area_inspections
    zone_name                 varchar(128),
    sector_name               varchar(128),
    plot_number               varchar(64),
    plot_key                  varchar(32),
    community_key             varchar(48),
    district_key              varchar(32),
    geom                      geometry(Point, 4326),
    loaded_at                 timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ops_distortion_district_idx ON theme.ops_distortion (district_key, raised_on);
COMMENT ON TABLE theme.ops_distortion IS '视觉违规上报；源：view_ins_distortion + view_uis_v_area_inspections';

CREATE TABLE theme.ops_violation (
    issue_number             varchar(64) PRIMARY KEY,
    raised_on                timestamp,
    closed_date              timestamp,
    status                   varchar(64),
    is_duplicate             boolean,
    stage                    varchar(16),                -- awareness / warning / fine
    fine_amount              numeric(14, 2),
    received_amount          numeric(14, 2),
    payment_status           varchar(64),
    violation_name           varchar(512),
    service_name             varchar(256),
    law                      varchar(64),                -- 城市外观法 / 建筑处罚 / 占用法（按条目归类）
    plot_gis_id              varchar(32),
    plot_key                 varchar(32),
    community_key            varchar(48),
    district_key             varchar(32),
    loaded_at                timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ops_violation_plot_idx ON theme.ops_violation (plot_key);
CREATE INDEX ops_violation_district_idx ON theme.ops_violation (district_key, raised_on);
COMMENT ON TABLE theme.ops_violation IS '违规与处罚；源：view_ins_violations + view_ins_violation_trx_result + view_ins_violations_uis';

CREATE TABLE theme.ops_occupancy_case (
    issue_number      varchar(64) PRIMARY KEY,
    status            varchar(64),
    current_activity  varchar(128),
    is_urgent         boolean,
    fine_amount       numeric(14, 2),
    plot_key          varchar(32),
    community_key     varchar(48),
    district_key      varchar(32),
    loaded_at         timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE theme.ops_occupancy_case IS '住宅占用（超员）案件；源：view_ins_survey';

CREATE TABLE theme.ops_streetlight (
    id             bigint PRIMARY KEY,                   -- 源 objectid
    lamp_count     int,
    status         varchar(64),
    community_key  varchar(48),
    district_key   varchar(32),
    geom           geometry(Point, 4326),
    loaded_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ops_streetlight_district_idx ON theme.ops_streetlight (district_key);
COMMENT ON TABLE theme.ops_streetlight IS '路灯；源：makani_adwea_e_streetlight';

CREATE TABLE theme.ops_bus_stop (
    gtfs_id        varchar(64) PRIMARY KEY,
    name           varchar(256),
    community_key  varchar(48),
    district_key   varchar(32),
    geom           geometry(Point, 4326),
    loaded_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ops_bus_stop_district_idx ON theme.ops_bus_stop (district_key);
COMMENT ON TABLE theme.ops_bus_stop IS '公交站；源：makani_bus_stop';

-- ================================================================================================
-- 资产与设施
-- ================================================================================================
CREATE TABLE theme.ast_facility (
    category       varchar(16)  NOT NULL,                -- school / medical / mosque / park
    source_id      varchar(64)  NOT NULL,
    name_en        varchar(512),
    name_ar        varchar(512),
    subtype        varchar(128),
    capacity       int,
    area_sqm       numeric(14, 2),                       -- 公园面积
    plot_key       varchar(32),
    community_key  varchar(48),
    district_key   varchar(32),
    geom           geometry(Geometry, 4326),
    loaded_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (category, source_id)
);
CREATE INDEX ast_facility_district_idx ON theme.ast_facility (district_key, category);
CREATE INDEX ast_facility_geom_gix ON theme.ast_facility USING gist (geom);
COMMENT ON TABLE theme.ast_facility IS '公共设施；源：view_lnd_adek_school、view_lnd_doh_facilities、makani_mosque_awqaf、makani_udm_park + makani_parks_details';

-- ================================================================================================
-- 宜居（居民需求调查）
-- ================================================================================================
CREATE TABLE theme.liv_resident_need (
    id             bigserial PRIMARY KEY,
    district_key   varchar(32),
    topic          varchar(128),
    option_name    varchar(256),
    votes          int,
    loaded_at      timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE theme.liv_resident_need IS '理想社区调查得票（居民需求前 3）；源：dream_busstopsurveys（字段待确认）';

INSERT INTO theme.meta_table (table_name, theme, title_zh, source_tables, sort_no) VALUES
    ('dev_masterplan',           '开发建设',   '总体规划',          'makani_masterplan_ud_masterplan_boundary', 11),
    ('dev_masterplan_plot',      '开发建设',   '总规地块指标',      'makani_masterplan_ud_plot', 12),
    ('dev_capital_project',      '开发建设',   '资本项目',          'view_gsec_project_details, view_gsec_project_point, view_gsec_project_polygon', 13),
    ('dev_permit',               '开发建设',   '建设许可',          'view_emecs_dashboard', 14),
    ('dev_building_application', '开发建设',   '竣工 / 维修 / 拆除', 'view_meps_cj_building_projects_dashboard', 15),
    ('dev_construction_site',    '开发建设',   '在建工地',          'makani_udm_constructionsite', 16),
    ('mkt_sale',                 '市场',       '房产买卖',          're_x_q21_matview_sale', 21),
    ('mkt_mortgage',             '市场',       '房产抵押',          're_x_q21_matview_mortgage', 22),
    ('mkt_rent',                 '市场',       '租赁合同',          're_x_q21_matview_rent', 23),
    ('mkt_plot_transaction',     '市场',       '地块交易',          're_elms_buy_and_sell_f, re_elms_mortgages_f', 24),
    ('mkt_tenancy',              '市场',       'Tawtheeq 租约',     'makani_tbl_tawtheeq_all, makani_tbl_bdms_tawtheeq_link_data, view_vw_tawtheeq_unit_status', 25),
    ('ops_case',                 '城市运行',   '居民诉求',          'view_adda_cases, flood_w_adda_slacomplaince_d, flood_adda_csat_d', 31),
    ('ops_accident',             '城市运行',   '道路事故',          'view_accident_master_new', 32),
    ('ops_distortion',           '城市运行',   '视觉违规上报',      'view_ins_distortion, view_uis_v_area_inspections', 33),
    ('ops_violation',            '城市运行',   '违规与处罚',        'view_ins_violations, view_ins_violation_trx_result, view_ins_violations_uis', 34),
    ('ops_occupancy_case',       '城市运行',   '住宅占用案件',      'view_ins_survey', 35),
    ('ops_streetlight',          '城市运行',   '路灯',              'makani_adwea_e_streetlight', 36),
    ('ops_bus_stop',             '城市运行',   '公交站',            'makani_bus_stop', 37),
    ('ast_facility',             '资产与设施', '公共设施',          'view_lnd_adek_school, view_lnd_doh_facilities, makani_mosque_awqaf, makani_udm_park, makani_parks_details', 41),
    ('liv_resident_need',        '宜居',       '居民需求调查',      'dream_busstopsurveys', 51);
