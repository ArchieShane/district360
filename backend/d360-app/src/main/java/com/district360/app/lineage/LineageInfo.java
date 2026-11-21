package com.district360.app.lineage;

import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/lineage 的返回：占位符已替换成实际库名、表名；:makani_district_id 等区参数由前端按选中区替换。
 *
 * @param statDb 统计库（页面实际读取的库）
 */
public record LineageInfo(String statDb, Map<String, SourceInfo> sources, Map<String, MetricInfo> metrics) {

    /** @param implemented 该源的抽取器是否已启用；false 时 SQL 是计划口径 */
    public record SourceInfo(String nameZh, String nameEn, String database, boolean implemented) {
    }

    public record MetricInfo(String nameZh, String nameEn, String source, String statColumn, List<String> sourceTables,
                             String logicZh, String logicEn, String sql) {
    }
}
