package com.district360.common.extract.snapshot;

import com.district360.common.extract.SourceSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Liveability 抽取结果。
 * <ul>
 *   <li>范围内找不到宜居区的区不要放进 districts，页面显示「没有查询到数据」；</li>
 *   <li>找得到宜居区、但不在宜居评估范围的区要放进来，{@code inScope = false}：页面不展示宜居得分，
 *       显示「不在宜居评估范围」，公园数 / 面积 / 缺口照常展示。</li>
 * </ul>
 *
 * @param calcVersionId 宜居发布版本（dim_calc_versions，current_flag 且 approved）
 * @param runId         优先级批次（fact_prioritization_runs 最近一次 approved）；没有时为 null，分档与最缺设施为空
 */
public record LiveabilitySnapshot(LocalDate sourceAsOf, Long calcVersionId, Long runId, List<District> districts)
        implements SourceSnapshot {

    @Override
    public int rowCount() {
        return districts.size();
    }

    /**
     * @param inScope            是否在宜居评估范围（宜居库 param_district_scope.in_scope）；false 时页面不展示得分
     * @param livDistrictId      宜居区 id（dim_districts.district_id，按范围重合面积最大匹配）
     * @param scoreExisting      fact_district_scores.overall_score，stage = Existing
     * @param scoreCurrent       stage = Pipeline（页面主值「在途」）
     * @param scoreTarget        stage = AP50（目标）
     * @param parks              公园数：fact_facilities 中 stage = Existing 的公园（面内一点落在范围内）
     * @param parkAreaHa         fact_parks_demand.existing_sqm / 10000
     * @param parkGapHa          max(0, area_required_sqm - existing_sqm) / 10000
     * @param capexAed           fact_budget.total_capex（AED，宜居平台 Budget 卡片总额）
     * @param opexAed            fact_budget.total_opex（AED）
     * @param capexTierTargetAed fact_budget.total_capex_tier_target（AED，全部按分档目标缺口计）
     * @param tier               优先级分档 high_priority / priority / remaining
     * @param tierRule           分档规则号 H1–H3 / P1–P4 / R
     * @param tierTriggers       触发项，逗号分隔（classification_triggers.triggered）
     * @param facilityGaps       最缺设施（按差距降序）
     * @param needs              居民需求（理想社区调查得票占比降序）；受访人数不足时为空
     */
    public record District(
            String code,
            Boolean inScope,
            Integer livDistrictId,
            BigDecimal scoreExisting,
            BigDecimal scoreCurrent,
            BigDecimal scoreTarget,
            Integer parks,
            BigDecimal parkAreaHa,
            BigDecimal parkGapHa,
            BigDecimal capexAed,
            BigDecimal opexAed,
            BigDecimal capexTierTargetAed,
            String tier,
            String tierRule,
            String tierTriggers,
            List<FacilityGap> facilityGaps,
            List<Need> needs) {
    }

    /** 一个设施项目：三阶段供给评分（0–100）、分档目标、差距 = 目标 − 在途。 */
    public record FacilityGap(String facility, String category, BigDecimal scoreExisting, BigDecimal scorePipeline,
                              BigDecimal scoreAp50, BigDecimal targetPct, BigDecimal gap) {
    }

    /** 理想社区调查的一个选项：得票占比 %、得票（受访人数）、本区受访人数。 */
    public record Need(String item, BigDecimal pct, Integer votes, Integer respondents) {
    }
}
