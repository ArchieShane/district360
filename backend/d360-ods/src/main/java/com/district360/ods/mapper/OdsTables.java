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
    private final String streetlight;
    private final String busStop;
    private final String addaCases;
    private final String accidents;
    private final String distortions;
    private final String violations;
    private final String occupancySurvey;
    private final String schools;
    private final String doh;
    private final String mosques;
    private final String parks;
    private final String parkDetails;
    private final String violationsUis;
    private final String violationResults;
    private final String gsecProjects;
    private final String gsecPoints;
    private final String gsecPolygons;
    private final String emecs;
    private final String meps;
    private final String tawtheeqUnitStatus;

    public OdsTables(OdsProperties p) {
        this.schools = p.table("makani_poi_adek_schools_locations");
        this.doh = p.table("makani_poi_doh_facilities");
        this.mosques = p.table("makani_mosque_awqaf");
        this.parks = p.table("makani_udm_park");
        this.parkDetails = p.table("makani_parks_details");
        this.streetlight = p.table("makani_adwea_e_streetlight");
        this.busStop = p.table("makani_bus_stop");
        this.addaCases = p.view("adda_cases");
        this.accidents = p.view("accident_master_new");
        this.distortions = p.view("ins_distortion");
        this.violations = p.view("ins_violations");
        this.violationsUis = p.view("ins_violations_uis");
        this.violationResults = p.view("ins_violation_trx_result");
        this.occupancySurvey = p.view("ins_survey");
        this.gsecProjects = p.view("gsec_project_details");
        this.gsecPoints = p.view("gsec_project_point");
        this.gsecPolygons = p.view("gsec_project_polygon");
        this.emecs = p.view("emecs_dashboard");
        this.meps = p.view("meps_cj_building_projects_dashboard");
        this.tawtheeqUnitStatus = p.view("vw_tawtheeq_unit_status");
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
    public String getStreetlight() { return streetlight; }
    public String getBusStop() { return busStop; }
    public String getAddaCases() { return addaCases; }
    public String getAccidents() { return accidents; }
    public String getDistortions() { return distortions; }
    public String getViolations() { return violations; }
    public String getOccupancySurvey() { return occupancySurvey; }
    public String getSchools() { return schools; }
    public String getDoh() { return doh; }
    public String getMosques() { return mosques; }
    public String getParks() { return parks; }
    public String getParkDetails() { return parkDetails; }
    public String getViolationsUis() { return violationsUis; }
    public String getViolationResults() { return violationResults; }
    public String getGsecProjects() { return gsecProjects; }
    public String getGsecPoints() { return gsecPoints; }
    public String getGsecPolygons() { return gsecPolygons; }
    public String getEmecs() { return emecs; }
    public String getMeps() { return meps; }
    public String getTawtheeqUnitStatus() { return tawtheeqUnitStatus; }
}
