package com.district360.common.extract.snapshot;

import com.district360.common.extract.SourceSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Liveability 抽取结果。不在宜居评估范围的区不要放进 districts，页面会显示「未纳入评估」。
 */
public record LiveabilitySnapshot(LocalDate sourceAsOf, List<District> districts) implements SourceSnapshot {

    @Override
    public int rowCount() {
        return districts.size();
    }

    /**
     * @param scoreExisting fact_district_scores.overall_score，stage = Existing
     * @param scoreCurrent  stage = Pipeline（页面主值「目前」）
     * @param scoreTarget   stage = AP50（目标）
     * @param parks         公园数（来源待确认：dim_facilities 还是 makani）
     * @param parkAreaHa    fact_parks_demand.existing_sqm / 10000
     * @param parkGapHa     max(0, area_required_sqm - existing_sqm) / 10000
     */
    public record District(
            String code,
            BigDecimal scoreExisting,
            BigDecimal scoreCurrent,
            BigDecimal scoreTarget,
            Integer parks,
            BigDecimal parkAreaHa,
            BigDecimal parkGapHa) {
    }
}
