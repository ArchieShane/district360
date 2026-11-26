package com.district360.stat.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 主题库 · 设施读查询（/api/v2/space/nodes/{level}/{key}/ast），SQL 在 resources/mapper/stat/ThemeAstQueryMapper.xml。
 * col 只来自 SpaceLevel 白名单，为 null 表示全酋长国。
 */
public interface ThemeAstQueryMapper {

    /** 各类数量与容量：学校（学位、在校生、教职工）、医疗（床位）、药房、清真寺（礼拜人数）、公园（个数按 park_id 去重、面积）。 */
    Map<String, Object> summary(@Param("col") String col, @Param("key") String key);

    /** 某一类按 subtype 分组 [{k, n, capacity}]；category 只来自代码常量。 */
    List<Map<String, Object>> bySubtype(@Param("col") String col, @Param("key") String key, @Param("category") String category);

    /** 设施列表（按容量 / 面积降序），带位置与详情字段 attrs。 */
    List<Map<String, Object>> items(@Param("col") String col, @Param("key") String key, @Param("category") String category,
                                    @Param("limit") int limit);

    /** 地块周边 radius 米内的设施与公交站（不含地块上的设施），按距离升序。 */
    List<Map<String, Object>> nearby(@Param("plot") String plotKey, @Param("radius") int radiusM);

    /** 地块面内一点 {cx, cy}。 */
    Map<String, Object> plotPoint(@Param("plot") String plotKey);
}
