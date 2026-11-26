-- 宜居补充指标（与宜居平台 dashboard 的 Budget、District Priority、Facility Scores、Community Voice 口径一致），
-- 由 liveability 抽取器每日整批写入，来源与算法见 d360-liveability 的 LiveabilityMapper.xml。
SET search_path TO stat, public;

ALTER TABLE stat_district_liveability
    ADD COLUMN liv_district_id       int,
    ADD COLUMN calc_version_id       bigint,
    ADD COLUMN prioritization_run_id bigint,
    ADD COLUMN capex_aed             numeric(18, 2),
    ADD COLUMN opex_aed              numeric(18, 2),
    ADD COLUMN capex_tier_target_aed numeric(18, 2),
    ADD COLUMN tier                  varchar(16),
    ADD COLUMN tier_rule             varchar(4),
    ADD COLUMN tier_triggers         varchar(256);

COMMENT ON COLUMN stat_district_liveability.liv_district_id IS '宜居区 id（dim_districts.district_id，按统计范围重合面积最大匹配）';
COMMENT ON COLUMN stat_district_liveability.calc_version_id IS '宜居发布版本（dim_calc_versions：current_flag 且 approved）';
COMMENT ON COLUMN stat_district_liveability.prioritization_run_id IS '优先级批次（fact_prioritization_runs 最近一次 approved）';
COMMENT ON COLUMN stat_district_liveability.capex_aed IS '达标所需投资 CAPEX：fact_budget.total_capex（AED）';
COMMENT ON COLUMN stat_district_liveability.opex_aed IS 'OPEX：fact_budget.total_opex（AED）';
COMMENT ON COLUMN stat_district_liveability.capex_tier_target_aed IS 'CAPEX（全部按分档目标缺口计）：fact_budget.total_capex_tier_target（AED）';
COMMENT ON COLUMN stat_district_liveability.tier IS '优先级分档 high_priority / priority / remaining：coalesce(approved, suggested)_classification';
COMMENT ON COLUMN stat_district_liveability.tier_rule IS '分档规则号 H1–H3 / P1–P4 / R（宜居平台 leadership_service.RULES 级联）';
COMMENT ON COLUMN stat_district_liveability.tier_triggers IS '触发项，逗号分隔（classification_triggers.triggered）';

CREATE TABLE stat_district_liv_facility_gap (
    district_code  varchar(32)  NOT NULL,
    rk             smallint     NOT NULL,
    facility       varchar(128) NOT NULL,
    category       varchar(16),
    score_existing numeric(6, 2),
    score_pipeline numeric(6, 2),
    score_ap50     numeric(6, 2),
    target_pct     numeric(6, 2),
    gap            numeric(6, 2),
    PRIMARY KEY (district_code, rk)
);
COMMENT ON TABLE stat_district_liv_facility_gap IS '区域最缺设施：FPP 类项目（含公园）在途供给评分低于分档目标的，按差距降序；宜居平台 Facility Scores 口径';

CREATE TABLE stat_district_liv_need (
    district_code varchar(32)  NOT NULL,
    rk            smallint     NOT NULL,
    item          varchar(128) NOT NULL,
    pct           numeric(6, 2),
    votes         int,
    respondents   int,
    PRIMARY KEY (district_code, rk)
);
COMMENT ON TABLE stat_district_liv_need IS '居民需求：理想社区调查（community_facilities_needs）得票占比前 3；受访少于 10 人的区不写；宜居平台 Community Voice 口径';
