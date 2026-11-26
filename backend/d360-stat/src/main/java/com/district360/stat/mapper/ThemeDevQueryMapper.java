package com.district360.stat.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 主题库 · 开发建设读查询（/api/v2/space/nodes/{level}/{key}/dev），SQL 在 resources/mapper/stat/ThemeDevQueryMapper.xml。
 * col 只来自 SpaceLevel 白名单（municipality_code / district_key / community_key / plot_key），为 null 表示全酋长国。
 */
public interface ThemeDevQueryMapper {

    /** 汇总：总规数、总规地块数与已建数、规划建筑面积 / 住宅单元 / 人口 / 酒店房间、在建工地与在建建筑。 */
    Map<String, Object> summary(@Param("col") String col, @Param("key") String key);

    /** 总规按阶段计数 [{k, n}]。 */
    List<Map<String, Object>> stages(@Param("col") String col, @Param("key") String key);

    /** 规划建筑面积按用途 [{k, gfa}]。 */
    List<Map<String, Object>> gfaByUse(@Param("col") String col, @Param("key") String key);

    /** 总规项目列表（按面积降序），每项带该范围内的总规地块数、已建数、规划建筑面积、住宅单元。 */
    List<Map<String, Object>> masterPlans(@Param("col") String col, @Param("key") String key, @Param("limit") int limit);

    /** 在建工地列表（按面积降序）。 */
    List<Map<String, Object>> sites(@Param("col") String col, @Param("key") String key, @Param("limit") int limit);

    /** 所属总规名称（社区：面积最大的前 3 个；地块：对应总规）；没有时 null。level 只来自代码。 */
    String masterPlanOf(@Param("level") String level, @Param("key") String key);

    /** 某地块的总规管控记录（容积率、层数、用途、规划面积等）。 */
    List<Map<String, Object>> plotControls(@Param("plotKey") String plotKey);
}
