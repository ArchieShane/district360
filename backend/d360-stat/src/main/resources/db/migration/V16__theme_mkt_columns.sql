-- 市场主题接入（抽取器 ods-mkt）：按 ODS 实际字段补列。
-- 源表（读当前 ODS 库；表不存在时跳过并在 meta_table.note 里写明）：
--   makani_tbl_tawtheeq_all                Tawtheeq 租约（makani_1119 有 1 万行样例）
--   re_x_q21_matview_sale / mortgage / rent ADREC 买卖 / 抵押 / 租赁（makani_1119 暂无，pop 里 10 行样例）
--   re_elms_buy_and_sell_f / re_elms_mortgages_f  ELMS 地块交易（同上）
-- 个人信息不入主题库：Tawtheeq 的租户 / 出租人姓名、联系人一律不抽。

-- 表登记加备注：源表缺失、口径说明等
ALTER TABLE theme.meta_table ADD COLUMN IF NOT EXISTS note varchar(512);

-- Tawtheeq 租约 -----------------------------------------------------------------------------------
-- 一份租约可含多个单元（1 万行里 9,112 个租约），主键改为自增 id
ALTER TABLE theme.mkt_tenancy DROP CONSTRAINT IF EXISTS mkt_tenancy_pkey;
ALTER TABLE theme.mkt_tenancy ADD COLUMN IF NOT EXISTS id bigserial PRIMARY KEY;
ALTER TABLE theme.mkt_tenancy
    ADD COLUMN IF NOT EXISTS contract_num       varchar(64),
    ADD COLUMN IF NOT EXISTS start_date         date,
    ADD COLUMN IF NOT EXISTS termination_date   date,
    ADD COLUMN IF NOT EXISTS unit_name          varchar(128),
    ADD COLUMN IF NOT EXISTS unit_type          varchar(128),
    ADD COLUMN IF NOT EXISTS unit_usage         varchar(128),
    ADD COLUMN IF NOT EXISTS unit_area_sqm      numeric(14, 2),
    ADD COLUMN IF NOT EXISTS bedrooms           int,
    ADD COLUMN IF NOT EXISTS property_name      varchar(256),
    ADD COLUMN IF NOT EXISTS property_type      varchar(128),
    ADD COLUMN IF NOT EXISTS property_usage     varchar(128),
    ADD COLUMN IF NOT EXISTS actual_usage       varchar(128),
    ADD COLUMN IF NOT EXISTS year_built         int,
    ADD COLUMN IF NOT EXISTS plot_number        varchar(64),
    ADD COLUMN IF NOT EXISTS src_district       varchar(128),
    ADD COLUMN IF NOT EXISTS src_community      varchar(128),
    ADD COLUMN IF NOT EXISTS municipality_code  varchar(8);
CREATE INDEX IF NOT EXISTS mkt_tenancy_plot_idx ON theme.mkt_tenancy (plot_key);
CREATE INDEX IF NOT EXISTS mkt_tenancy_district_idx ON theme.mkt_tenancy (district_key);
CREATE INDEX IF NOT EXISTS mkt_tenancy_community_idx ON theme.mkt_tenancy (community_key);
COMMENT ON COLUMN theme.mkt_tenancy.plot_key IS 'gisid → sp_plot；unit_key 需经 tbl_bdms_tawtheeq_link_data 桥接（当前样例对不上）';

-- ADREC 买卖 / 抵押 / 租赁：坐标落地块，补位置与物业字段 ------------------------------------------------
ALTER TABLE theme.mkt_sale
    ADD COLUMN IF NOT EXISTS sale_type              varchar(128),
    ADD COLUMN IF NOT EXISTS normalized_price_aed   numeric(18, 2),
    ADD COLUMN IF NOT EXISTS property_category      varchar(128),
    ADD COLUMN IF NOT EXISTS property_type          varchar(128),
    ADD COLUMN IF NOT EXISTS property_layout        varchar(128),
    ADD COLUMN IF NOT EXISTS project_name           varchar(256),
    ADD COLUMN IF NOT EXISTS developer_name         varchar(256),
    ADD COLUMN IF NOT EXISTS unit_floor             varchar(64),
    ADD COLUMN IF NOT EXISTS municipality_code      varchar(8),
    ADD COLUMN IF NOT EXISTS building_key           varchar(40),
    ADD COLUMN IF NOT EXISTS geom                   geometry(Point, 4326);
CREATE INDEX IF NOT EXISTS mkt_sale_plot_idx ON theme.mkt_sale (plot_key);
CREATE INDEX IF NOT EXISTS mkt_sale_community_idx ON theme.mkt_sale (community_key);

ALTER TABLE theme.mkt_mortgage
    ADD COLUMN IF NOT EXISTS contract_type      varchar(128),
    ADD COLUMN IF NOT EXISTS contract_status    varchar(64),
    ADD COLUMN IF NOT EXISTS end_date           date,
    ADD COLUMN IF NOT EXISTS property_type      varchar(128),
    ADD COLUMN IF NOT EXISTS land_plot          varchar(128),
    ADD COLUMN IF NOT EXISTS building_id        varchar(64),
    ADD COLUMN IF NOT EXISTS municipality_code  varchar(8),
    ADD COLUMN IF NOT EXISTS plot_key           varchar(32),
    ADD COLUMN IF NOT EXISTS building_key       varchar(40),
    ADD COLUMN IF NOT EXISTS geom               geometry(Point, 4326);
CREATE INDEX IF NOT EXISTS mkt_mortgage_plot_idx ON theme.mkt_mortgage (plot_key);
CREATE INDEX IF NOT EXISTS mkt_mortgage_community_idx ON theme.mkt_mortgage (community_key);

ALTER TABLE theme.mkt_rent
    ADD COLUMN IF NOT EXISTS total_value_aed    numeric(18, 2),
    ADD COLUMN IF NOT EXISTS end_date           date,
    ADD COLUMN IF NOT EXISTS asset_class        varchar(128),
    ADD COLUMN IF NOT EXISTS property_type      varchar(128),
    ADD COLUMN IF NOT EXISTS project_name       varchar(256),
    ADD COLUMN IF NOT EXISTS developer_name     varchar(256),
    ADD COLUMN IF NOT EXISTS land_plot          varchar(128),
    ADD COLUMN IF NOT EXISTS building_id        varchar(64),
    ADD COLUMN IF NOT EXISTS municipality_code  varchar(8),
    ADD COLUMN IF NOT EXISTS plot_key           varchar(32),
    ADD COLUMN IF NOT EXISTS building_key       varchar(40),
    ADD COLUMN IF NOT EXISTS geom               geometry(Point, 4326);
CREATE INDEX IF NOT EXISTS mkt_rent_plot_idx ON theme.mkt_rent (plot_key);
CREATE INDEX IF NOT EXISTS mkt_rent_community_idx ON theme.mkt_rent (community_key);

-- ELMS 地块交易 -----------------------------------------------------------------------------------
ALTER TABLE theme.mkt_plot_transaction
    ADD COLUMN IF NOT EXISTS txn_id             varchar(64),
    ADD COLUMN IF NOT EXISTS txn_date           date,
    ADD COLUMN IF NOT EXISTS project_name       varchar(256),
    ADD COLUMN IF NOT EXISTS community_key      varchar(48),
    ADD COLUMN IF NOT EXISTS district_key       varchar(32),
    ADD COLUMN IF NOT EXISTS municipality_code  varchar(8);

UPDATE theme.meta_table SET extractor = 'ods-mkt'
 WHERE table_name IN ('mkt_sale', 'mkt_mortgage', 'mkt_rent', 'mkt_plot_transaction', 'mkt_tenancy');
