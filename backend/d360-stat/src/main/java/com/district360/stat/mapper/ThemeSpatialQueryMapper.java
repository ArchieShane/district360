package com.district360.stat.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 主题库 · 空间下钻读查询，SQL 在 resources/mapper/stat/ThemeSpatialQueryMapper.xml。
 * <p>
 * level / layer / dim 等结构参数只来自 d360-app 里的白名单枚举（SpaceLevel、SpaceLayer、SpaceDim），不直接来自请求；
 * 请求里的编码、关键字一律用 #{} 绑定。
 */
public interface ThemeSpatialQueryMapper {

    /** 节点本身：名称、上级编码与名称、外包框、中心、简化后的几何（GeoJSON 文本），以及该层级的属性列。 */
    Map<String, Object> node(@Param("level") String level, @Param("key") String key);

    /** 节点计数（theme.sp_node_stat；楼层、单元现算）。emirate 为三个市政之和。 */
    Map<String, Object> stat(@Param("level") String level, @Param("col") String col, @Param("key") String key);

    /** 下一级列表（分页 + 关键字），列名为驼峰（双引号别名），直接返回给前端。 */
    List<Map<String, Object>> children(@Param("child") String childLevel, @Param("col") String parentCol,
                                       @Param("key") String key, @Param("q") String q,
                                       @Param("limit") int limit, @Param("offset") int offset);

    long childCount(@Param("child") String childLevel, @Param("col") String parentCol,
                    @Param("key") String key, @Param("q") String q);

    /** 分组计数：table / dimExpr / areaExpr / extra 来自 SpaceDim 白名单。 */
    List<Map<String, Object>> breakdown(@Param("table") String table, @Param("dimExpr") String dimExpr,
                                        @Param("areaExpr") String areaExpr, @Param("extra") String extra,
                                        @Param("col") String col, @Param("key") String key);

    /** 地图图层（GeoJSON FeatureCollection 文本）。 */
    String features(@Param("layer") String layer, @Param("col") String col, @Param("key") String key,
                    @Param("limit") int limit, @Param("tol") double tolerance);

    List<Map<String, Object>> search(@Param("q") String q, @Param("limit") int limit);

    List<Map<String, Object>> metaTables();

    List<Map<String, Object>> lastRuns(@Param("prefix") String sourcePrefix);
}
