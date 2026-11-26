package com.district360.stat.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 主题库 · 城市运行读查询（/api/v2/space/nodes/{level}/{key}/ops），SQL 在 resources/mapper/stat/ThemeOpsQueryMapper.xml。
 * col 只来自 SpaceLevel 白名单（municipality_code / district_key / community_key），为 null 表示全酋长国。
 */
public interface ThemeOpsQueryMapper {

    /** 路灯点数 / 灯盏数 / 在用数，公交站数（gtfs_id 去重）/ 站台数。 */
    Map<String, Object> summary(@Param("col") String col, @Param("key") String key);

    /** 路灯分组 [{k, n, lamps}]：dim 只来自代码常量（status / lamp_type）。 */
    List<Map<String, Object>> lightsBy(@Param("col") String col, @Param("key") String key, @Param("dim") String dim);

    /** 公交站列表（按站去重，带站台数与位置），按名称排序。 */
    List<Map<String, Object>> busStops(@Param("col") String col, @Param("key") String key, @Param("limit") int limit);
}
