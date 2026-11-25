package com.district360.ods.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ODS 取数配置（d360.ods）。源表全名 = &lt;schema&gt;.&lt;table-prefix&gt;&lt;表名&gt;，例如 ods.ods_makani_udm_district。
 */
@ConfigurationProperties(prefix = "d360.ods")
public class OdsProperties {

    /** 源表所在 schema。 */
    private String schema = "ods";
    /** 源表名前缀（ODS 统一为 ods_，后面跟源系统前缀 makani_ / view_ / re_ ...）。 */
    private String tablePrefix = "ods_";
    /** 流式读取每次取回的行数（ODS 连接关闭自动提交，PostgreSQL 才会按游标分批取）。 */
    private int fetchSize = 5000;
    /** 单条 SQL 超时（秒）。 */
    private int statementTimeoutSeconds = 1800;
    /**
     * 市政编号 → 市政编码。ods_makani_uae_all_municipalities 是全阿联酋的市政，只取这里列出的；
     * 编码与 udm_district.municipalityname 一致。
     */
    private Map<Integer, String> municipalityCodes = new LinkedHashMap<>(Map.of(101, "ADM", 102, "AAM", 103, "WRM"));

    public String table(String name) {
        String full = schema + "." + tablePrefix + name;
        if (!full.matches("[A-Za-z0-9_]+\\.[A-Za-z0-9_]+")) {
            throw new IllegalStateException("非法的 ODS 表名配置: " + full
                    + "（d360.ods.schema / table-prefix 只能包含字母、数字、下划线）");
        }
        return full;
    }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getTablePrefix() { return tablePrefix; }
    public void setTablePrefix(String tablePrefix) { this.tablePrefix = tablePrefix; }
    public int getFetchSize() { return fetchSize; }
    public void setFetchSize(int fetchSize) { this.fetchSize = fetchSize; }
    public int getStatementTimeoutSeconds() { return statementTimeoutSeconds; }
    public void setStatementTimeoutSeconds(int v) { this.statementTimeoutSeconds = v; }
    public Map<Integer, String> getMunicipalityCodes() { return municipalityCodes; }
    public void setMunicipalityCodes(Map<Integer, String> v) { this.municipalityCodes = v; }
}
