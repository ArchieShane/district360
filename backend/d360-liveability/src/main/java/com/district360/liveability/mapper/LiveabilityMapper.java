package com.district360.liveability.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Map;

/**
 * TODO(宜居同事)：按需改返回类型，SQL 写在 resources/mapper/liveability/LiveabilityMapper.xml。
 * <p>
 * 口径要求：<b>按统计范围做空间查询，不用区 ID</b>。参数 scope 是该区 makani 原始区面（EWKB hex，
 * 来自 {@code DistrictRef#scopeEwkbHex()}），已验证与 dim_districts.geom 基本重合（IoU ≥ 0.997）。
 * 指标口径见 docs/District360_数据字段对照_2026112101-2.xlsx 与 metric-lineage.yml。
 */
public interface LiveabilityMapper {

    /** 一个区：Existing / Pipeline / AP50 得分、公园数、公园面积、公园缺口。 */
    Map<String, Object> districtStats(@Param("scope") String scopeEwkbHex);

    /** 数据日期，例如 fact_district_scores.updated_at（calc_version_id = 496）的最大值。 */
    LocalDate dataDate();
}
