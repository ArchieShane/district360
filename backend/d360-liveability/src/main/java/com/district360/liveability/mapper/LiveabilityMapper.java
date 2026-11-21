package com.district360.liveability.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Map;

/**
 * TODO(宜居同事)：按需改返回类型，SQL 写在 resources/mapper/liveability/LiveabilityMapper.xml。
 * <p>
 * 口径要求：<b>按统计范围做空间查询，不用区 ID</b>。参数 scope 是该区 makani 原始区面（EWKB hex，
 * 来自 {@code DistrictRef#scopeEwkbHex()}），已验证与 dim_districts.geom 基本重合（IoU ≥ 0.997）。
 * 指标口径参考 docs/District360_数据字段对照_*.xlsx（仅供参考，以与数据治理确认的为准）；确认后的取数方式写进 metric-lineage.yml，
 * 页面悬停会展示。接入步骤见 docs/同事接入指南_宜居与ADREC.md。
 */
public interface LiveabilityMapper {

    /** 一个区：Existing / Pipeline / AP50 得分、公园数、公园面积、公园缺口。 */
    Map<String, Object> districtStats(@Param("scope") String scopeEwkbHex);

    /** 数据日期，例如 fact_district_scores.updated_at（calc_version_id = 496）的最大值。 */
    LocalDate dataDate();
}
