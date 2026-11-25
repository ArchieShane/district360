package com.district360.ods.mapper;

import com.district360.ods.config.OdsProperties;

/**
 * 带 schema 和前缀的 ODS 表全名，在 SQL 里用 ${t.xxx} 引用（值来自配置，不来自请求）。
 * 用普通 getter，保证 MyBatis 的 OGNL 能解析。
 */
public class OdsTables {

    private final String municipality;
    private final String district;
    private final String community;
    private final String plot;
    private final String building;
    private final String floors;
    private final String units;

    public OdsTables(OdsProperties p) {
        this.municipality = p.table("makani_uae_all_municipalities");
        this.district = p.table("makani_udm_district");
        this.community = p.table("makani_udm_community");
        this.plot = p.table("makani_udm_plot");
        this.building = p.table("makani_udm_building");
        this.floors = p.table("makani_bdms_floors_height");
        this.units = p.table("makani_bdms_units_height");
    }

    public String getMunicipality() { return municipality; }
    public String getDistrict() { return district; }
    public String getCommunity() { return community; }
    public String getPlot() { return plot; }
    public String getBuilding() { return building; }
    public String getFloors() { return floors; }
    public String getUnits() { return units; }
}
