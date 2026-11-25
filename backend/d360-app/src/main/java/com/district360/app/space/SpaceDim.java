package com.district360.app.space;

/**
 * 分组计数维度（白名单）：表、分组表达式、面积表达式、固定过滤条件。SQL 片段只在这里定义，不来自请求。
 */
public enum SpaceDim {

    /** 地块建设状态（不含已注销），面积为地块面积。 */
    PLOT_STATUS("plotStatus", "sp_plot", "coalesce(nullif(x.construction_status, ''), 'Unknown')", "x.area_sqm", "x.cancel_date IS NULL"),
    /** 地块主要用途（udm_plot.primaryuseengdesc）。 */
    LAND_USE("landUse", "sp_plot", "coalesce(nullif(x.primary_use, ''), 'Undefined')", "x.area_sqm", "x.cancel_date IS NULL"),
    /** 建筑主要用途（只计数；全量 48 万栋算基底面积太慢，页面也不展示）。 */
    BUILDING_USE("buildingUse", "sp_building", "coalesce(nullif(x.primary_use, ''), 'Unknown')", "0", "true"),
    BUILDING_STATUS("buildingStatus", "sp_building", "coalesce(nullif(x.physical_status, ''), 'Unknown')", "0", "true"),
    /** 单元分类（Apartment / Villa / Shop ...），面积为单元面积。 */
    UNIT_CLASS("unitClass", "sp_unit", "coalesce(nullif(x.classification, ''), nullif(x.unit_type, ''), 'Unknown')", "x.area_sqm", "true"),
    UNIT_TYPE("unitType", "sp_unit", "coalesce(nullif(x.unit_type, ''), 'Unknown')", "x.area_sqm", "true"),
    BEDROOMS("bedrooms", "sp_unit", "coalesce(x.bedrooms::text, '—')", "x.area_sqm", "true");

    private final String key;
    private final String table;
    private final String dimExpr;
    private final String areaExpr;
    private final String extra;

    SpaceDim(String key, String table, String dimExpr, String areaExpr, String extra) {
        this.key = key;
        this.table = table;
        this.dimExpr = dimExpr;
        this.areaExpr = areaExpr;
        this.extra = extra;
    }

    public String key() { return key; }
    public String table() { return table; }
    public String dimExpr() { return dimExpr; }
    public String areaExpr() { return areaExpr; }
    public String extra() { return extra; }
}
