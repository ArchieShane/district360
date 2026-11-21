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
 */
public record LiveabilitySnapshot(LocalDate sourceAsOf, List<District> districts) implements SourceSnapshot {

    @Override
    public int rowCount() {
        return districts.size();
    }

    /**
     * @param inScope       是否在宜居评估范围（宜居库 param_district_scope.in_scope）；false 时页面不展示得分
     * @param scoreExisting fact_district_scores.overall_score，stage = Existing
     * @param scoreCurrent  stage = Pipeline（页面主值「目前」）
     * @param scoreTarget   stage = AP50（目标）
     * @param parks         公园数：fact_facilities 中 stage = Existing 的公园（面内一点落在范围内）
     * @param parkAreaHa    fact_parks_demand.existing_sqm / 10000
     * @param parkGapHa     max(0, area_required_sqm - existing_sqm) / 10000
     */
    public record District(
            String code,
            Boolean inScope,
            BigDecimal scoreExisting,
            BigDecimal scoreCurrent,
            BigDecimal scoreTarget,
            Integer parks,
            BigDecimal parkAreaHa,
            BigDecimal parkGapHa) {
    }
}
