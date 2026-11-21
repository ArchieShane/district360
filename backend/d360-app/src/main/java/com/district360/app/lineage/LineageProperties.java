package com.district360.app.lineage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 指标血缘配置（metric-lineage.yml，前缀 d360.lineage）。 */
@ConfigurationProperties(prefix = "d360.lineage")
public class LineageProperties {

    private Map<String, Source> sources = new LinkedHashMap<>();
    private Map<String, Metric> metrics = new LinkedHashMap<>();

    public Map<String, Source> getSources() { return sources; }
    public void setSources(Map<String, Source> sources) { this.sources = sources; }
    public Map<String, Metric> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Metric> metrics) { this.metrics = metrics; }

    public record Source(String nameZh, String nameEn, String plannedDb) {
    }

    public record Metric(String nameZh, String nameEn, String source, String statColumn, List<String> sourceTables,
                         String logicZh, String logicEn, String sql) {
    }
}
