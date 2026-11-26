package com.district360.stat.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 宜居页签读查询（/api/v2/space/nodes/{level}/{key}/liv），SQL 在 resources/mapper/stat/ThemeLivQueryMapper.xml。
 * 数据来自 v1 的 stat.stat_district_liveability（每日从宜居库抽取），按区域给出评分、排名与公园。
 */
public interface ThemeLivQueryMapper {

    /**
     * 区域宜居评分：existing / pipeline / target / gap、全酋长国与市政内排名（只排评估范围内的区）、公园数 / 面积 / 缺口。
     * municipality、district 都为 null 时返回全部区；按范围内在前、在途评分降序。
     */
    List<Map<String, Object>> districts(@Param("municipality") String municipality, @Param("district") String district);
}
