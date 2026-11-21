package com.district360.makani.mapper;

import com.district360.makani.config.MakaniProperties;

/**
 * 带 schema 和前缀的完整表名，在 SQL 里用 ${t.xxx} 引用（值来自配置，不来自请求）。
 * 用普通 getter 而不是 record，保证 MyBatis 的 OGNL 能解析 ${t.district}。
 */
public class MakaniTables {

    private final String district;
    private final String community;
    private final String plot;
    private final String building;
    private final String school;
    private final String doh;
    private final String mosque;

    public MakaniTables(MakaniProperties p) {
        this.district = p.table("udm_district");
        this.community = p.table("udm_community");
        this.plot = p.table("udm_plot");
        this.building = p.table("udm_building");
        this.school = p.table("poi_adek_schools_locations");
        this.doh = p.table("poi_doh_facilities");
        this.mosque = p.table("mosque_awqaf");
    }

    public String getDistrict() { return district; }
    public String getCommunity() { return community; }
    public String getPlot() { return plot; }
    public String getBuilding() { return building; }
    public String getSchool() { return school; }
    public String getDoh() { return doh; }
    public String getMosque() { return mosque; }
}
