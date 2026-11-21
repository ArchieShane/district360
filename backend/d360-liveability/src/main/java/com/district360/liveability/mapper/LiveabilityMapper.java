package com.district360.liveability.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * TODO(宜居同事)：按需改返回类型，SQL 写在 resources/mapper/liveability/LiveabilityMapper.xml。
 * 口径见 docs/District360_数据字段对照_2026112101.xlsx 和 docs/接口设计与协作方案_v0.2.md 第 3、4.2 节。
 */
public interface LiveabilityMapper {

    /** 每个 liv district_id 的 Existing / Pipeline / AP50 得分、公园数、公园面积、公园缺口。 */
    List<Map<String, Object>> districtStats(@Param("livIds") List<Integer> livDistrictIds);

    /** 数据日期，例如 fact_district_scores.updated_at（calc_version_id = 496）的最大值。 */
    LocalDate dataDate();
}
