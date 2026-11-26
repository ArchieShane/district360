package com.district360.common.theme.dev;

import java.math.BigDecimal;

/**
 * 开发建设主题的行类型（字段与 theme.dev_* 表对应，下划线自动转驼峰）。几何为 EPSG:4326 的 EWKB hex（MultiPolygon）。
 * 空间归属（plot_key / community_key / district_key）不由抽取器给，写入后在主题库里关联。
 */
public final class DevRows {

    private DevRows() {
    }

    /** 总体规划项目（makani_masterplan_ud_masterplan_boundary）。 */
    public static class MasterPlan {
        public String projectId;
        public String projectNo;
        public String projectName;
        /** 归一后的阶段：Concept / Detailed / Minor Amendment / Time Extension / Enquiry / Other */
        public String stage;
        public String stageRaw;
        public Integer approvalYear;
        public String developerName;
        public String srcMunicipality;
        public String municipalityCode;
        public String srcDistrictId;
        public String srcCommunityId;
        public String masterplanLevel;
        public String projectType;
        public String projectLead;
        public String ownerName;
        public String parentProjectNo;
        public String geomHex;
    }

    /** 总规地块管控指标（makani_masterplan_ud_plot）。 */
    public static class MasterPlanPlot {
        public Integer srcObjectid;
        public String projectId;
        public String masterplanType;
        public String approvalStatus;
        public String udmGisid;
        public String udmPlotNumber;
        public String registeredLandUse;
        public String landUseL1;
        public String landUseL2;
        public BigDecimal plotAreaSqm;
        public BigDecimal far;
        public Integer buildingStoreys;
        public Integer buildings;
        public BigDecimal residentialPopulation;
        public Integer totalResidentialUnits;
        public Integer hotelRooms;
        public BigDecimal totalGfa;
        public BigDecimal residentialGfa;
        public BigDecimal officeGfa;
        public BigDecimal retailGfa;
        public BigDecimal hotelGfa;
        public BigDecimal industrialGfa;
        public BigDecimal commercialRecreationGfa;
        public BigDecimal religiousGfa;
        public BigDecimal educationGfa;
        public BigDecimal healthcareGfa;
        public BigDecimal communityFacilitiesGfa;
        public String geomHex;
    }

    /** 在建工地（makani_udm_constructionsite）。 */
    public static class ConstructionSite {
        public String siteId;
        public String description;
        public String municipalityCode;
        public String geomHex;
    }
}
