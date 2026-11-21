package com.district360.makani.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * makani 取数配置（d360.makani）。库切换时只改 schema / tablePrefix。
 */
@ConfigurationProperties(prefix = "d360.makani")
public class MakaniProperties {

    /** 源表所在 schema，makani_1119 为 ods。 */
    private String schema = "ods";
    /** 源表名前缀，makani_1119 为 ods_makani_。 */
    private String tablePrefix = "ods_makani_";
    /** 区面所属市政（udm_district.municipalityname）。districtid 跨市政重复，必须带上。 */
    private String municipality = "ADM";
    /** 区面简化容差（度），约 3 米。 */
    private double boundarySimplifyTolerance = 0.00003;
    /** 医疗设施名称排除规则（ILIKE），用于去掉 DOH 表里的测试数据。 */
    private List<String> medicalExcludeNamePatterns = List.of("%test%");

    public String table(String name) {
        String full = schema + "." + tablePrefix + name;
        if (!full.matches("[A-Za-z0-9_]+\\.[A-Za-z0-9_]+")) {
            throw new IllegalStateException("非法的 makani 表名配置: " + full);
        }
        return full;
    }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getTablePrefix() { return tablePrefix; }
    public void setTablePrefix(String tablePrefix) { this.tablePrefix = tablePrefix; }
    public String getMunicipality() { return municipality; }
    public void setMunicipality(String municipality) { this.municipality = municipality; }
    public double getBoundarySimplifyTolerance() { return boundarySimplifyTolerance; }
    public void setBoundarySimplifyTolerance(double v) { this.boundarySimplifyTolerance = v; }
    public List<String> getMedicalExcludeNamePatterns() { return medicalExcludeNamePatterns; }
    public void setMedicalExcludeNamePatterns(List<String> v) { this.medicalExcludeNamePatterns = v; }
}
