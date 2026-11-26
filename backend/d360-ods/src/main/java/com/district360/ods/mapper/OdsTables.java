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
    private final String masterplanBoundary;
    private final String masterplanPlot;
    private final String constructionSite;
    private final String tawtheeq;
    private final String reSale;
    private final String reMortgage;
    private final String reRent;
    private final String elmsBuySell;
    private final String elmsMortgage;

    public OdsTables(OdsProperties p) {
        this.tawtheeq = p.table("makani_tbl_tawtheeq_all");
        this.reSale = p.table("re_x_q21_matview_sale");
        this.reMortgage = p.table("re_x_q21_matview_mortgage");
        this.reRent = p.table("re_x_q21_matview_rent");
        this.elmsBuySell = p.table("re_elms_buy_and_sell_f");
        this.elmsMortgage = p.table("re_elms_mortgages_f");
        this.masterplanBoundary = p.table("makani_masterplan_ud_masterplan_boundary");
        this.masterplanPlot = p.table("makani_masterplan_ud_plot");
        this.constructionSite = p.table("makani_udm_constructionsite");
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
    public String getMasterplanBoundary() { return masterplanBoundary; }
    public String getMasterplanPlot() { return masterplanPlot; }
    public String getConstructionSite() { return constructionSite; }
    public String getTawtheeq() { return tawtheeq; }
    public String getReSale() { return reSale; }
    public String getReMortgage() { return reMortgage; }
    public String getReRent() { return reRent; }
    public String getElmsBuySell() { return elmsBuySell; }
    public String getElmsMortgage() { return elmsMortgage; }
}
