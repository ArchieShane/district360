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

    /** 区域最缺设施：FPP 类项目（含公园）在途供给评分低于分档目标的，按差距降序前 limit 个。 */
    List<Map<String, Object>> facilityGaps(@Param("district") String district, @Param("limit") int limit);

    /** 居民需求：理想社区调查得票占比前 3（受访不足 10 人的区为空）。 */
    List<Map<String, Object>> needs(@Param("district") String district);

    /**
     * 设施类型目录（35 类）及范围内现状数量：municipality、district 都为 null 时为全酋长国合计。
     * 每类一行（数量为 0 的也给出），按领域、数量降序。目录为空（抽取尚未跑过）时返回空列表。
     */
    List<Map<String, Object>> facilityTypes(@Param("municipality") String municipality, @Param("district") String district);
}
