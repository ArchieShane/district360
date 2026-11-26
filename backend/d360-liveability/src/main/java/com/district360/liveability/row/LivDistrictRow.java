package com.district360.liveability.row;

import java.math.BigDecimal;

/**
 * {@code LiveabilityMapper.districtStats} 的结果行：一个统计范围对应的宜居指标（列名下划线自动转驼峰）。
 * {@code livDistrictId == null} 表示范围内没有宜居区。
 */
public class LivDistrictRow {
    public Integer livDistrictId;
    /** 是否在宜居评估范围（param_district_scope.in_scope） */
    public Boolean inScope;
    public BigDecimal scoreExisting;
    public BigDecimal scoreCurrent;
    public BigDecimal scoreTarget;
    public Integer parks;
    public BigDecimal parkAreaHa;
    public BigDecimal parkGapHa;
    /** fact_budget.total_capex / total_opex / total_capex_tier_target，AED */
    public BigDecimal capexAed;
    public BigDecimal opexAed;
    public BigDecimal capexTierTargetAed;
    /** high_priority / priority / remaining */
    public String tier;
    /** H1–H3 / P1–P4 / R */
    public String tierRule;
    /** classification_triggers.triggered，逗号分隔 */
    public String tierTriggers;
}
