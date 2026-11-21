package com.district360.liveability.row;

import java.math.BigDecimal;

/**
 * {@code LiveabilityMapper.districtStats} 的结果行：一个统计范围对应的宜居指标（列名下划线自动转驼峰）。
 * {@code livDistrictId == null} 表示范围内没有宜居区。
 */
public class LivDistrictRow {
    public Integer livDistrictId;
    public BigDecimal scoreExisting;
    public BigDecimal scoreCurrent;
    public BigDecimal scoreTarget;
    public Integer parks;
    public BigDecimal parkAreaHa;
    public BigDecimal parkGapHa;
}
