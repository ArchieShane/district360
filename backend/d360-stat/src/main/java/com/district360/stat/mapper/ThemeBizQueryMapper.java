package com.district360.stat.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 主题库 · 业务视图读查询（资本项目、许可、申请、诉求、事故、视觉违规、违规、占用、单元状态），
 * SQL 在 resources/mapper/stat/ThemeBizQueryMapper.xml。
 * col 只来自 SpaceLevel 白名单（municipality_code / district_key / community_key / plot_key），为 null 表示全酋长国；
 * dim 只来自调用方常量。
 */
public interface ThemeBizQueryMapper {

    Map<String, Object> projectSummary(@Param("col") String col, @Param("key") String key);

    List<Map<String, Object>> projectsBy(@Param("col") String col, @Param("key") String key, @Param("dim") String dim);

    List<Map<String, Object>> projects(@Param("col") String col, @Param("key") String key, @Param("limit") int limit);

    Map<String, Object> permitSummary(@Param("col") String col, @Param("key") String key);

    List<Map<String, Object>> permitsBy(@Param("col") String col, @Param("key") String key, @Param("dim") String dim);

    Map<String, Object> applicationSummary(@Param("col") String col, @Param("key") String key);

    List<Map<String, Object>> applicationTypes(@Param("col") String col, @Param("key") String key);

    List<Map<String, Object>> applicationsByMonth(@Param("col") String col, @Param("key") String key);

    List<Map<String, Object>> plotPermits(@Param("plot") String plotKey, @Param("limit") int limit);

    Map<String, Object> caseSummary(@Param("col") String col, @Param("key") String key);

    List<Map<String, Object>> casesBy(@Param("col") String col, @Param("key") String key, @Param("dim") String dim);

    List<Map<String, Object>> casesByMonth(@Param("col") String col, @Param("key") String key);

    Map<String, Object> accidentSummary(@Param("col") String col, @Param("key") String key);

    List<Map<String, Object>> accidentsBy(@Param("col") String col, @Param("key") String key, @Param("dim") String dim);

    List<Map<String, Object>> accidentsByMonth(@Param("col") String col, @Param("key") String key);

    Map<String, Object> distortionSummary(@Param("col") String col, @Param("key") String key);

    List<Map<String, Object>> distortionsBy(@Param("col") String col, @Param("key") String key, @Param("dim") String dim);

    List<Map<String, Object>> distortionsByMonth(@Param("col") String col, @Param("key") String key);

    Map<String, Object> violationSummary(@Param("col") String col, @Param("key") String key);

    List<Map<String, Object>> violationsBy(@Param("col") String col, @Param("key") String key, @Param("dim") String dim);

    Map<String, Object> occupancyCaseSummary(@Param("col") String col, @Param("key") String key);

    List<Map<String, Object>> occupancyCasesBy(@Param("col") String col, @Param("key") String key, @Param("dim") String dim);

    List<Map<String, Object>> plotOpsRecords(@Param("plot") String plotKey, @Param("limit") int limit);

    Map<String, Object> occupancySummary(@Param("col") String col, @Param("key") String key);

    List<Map<String, Object>> occupancyBy(@Param("col") String col, @Param("key") String key, @Param("dim") String dim);

    /** floor 与 unit 二选一。 */
    List<Map<String, Object>> unitStatuses(@Param("floor") String floorKey, @Param("unit") String unitKey);

    List<Map<String, Object>> watchProjects(@Param("municipality") String municipality);

    List<Map<String, Object>> watchOccupancy(@Param("municipality") String municipality);

    List<Map<String, Object>> watchDistortions(@Param("municipality") String municipality);

    /** 业务视图各主题表的行数与备注（表登记）。 */
    List<Map<String, Object>> tableRows();
}
