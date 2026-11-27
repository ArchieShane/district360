package com.district360.common.theme.dev;

import java.time.LocalDate;
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

    /** 资本项目（view_gsec_project_details，几何取 view_gsec_project_point，没有点时取 _polygon）。 */
    public static class CapitalProject {
        public String projectId;
        public String projectName;
        public String category;
        public String phase;
        public String status;
        public String projectType;
        public String dofNo;
        public BigDecimal approvedBudget;
        public BigDecimal cumulativeExpenditures;
        public LocalDate originalPlannedComDate;
        public LocalDate actualForecastComDate;
        public BigDecimal actualProgress;
        public BigDecimal revisedProgress;
        public BigDecimal originalProgress;
        public String cipLocationDistrict;
        public String cipLocationCommunity;
        public String cipLocationPlot;
        public String srcMunicipality;
        public String municipalityCode;
        public String contractorName;
        public Integer startYear;
        /** point / polygon / null */
        public String geomKind;
        /** EPSG:4326 任意几何 */
        public String geomHex;
    }

    /** 建设许可（view_emecs_dashboard），同一许可号取最新一行。 */
    public static class Permit {
        public String permitNumber;
        public LocalDate issuingDate;
        public String gisid;
        public String permitStatus;
        public String projectStatus;
        public LocalDate projectCompletionDate;
        public String delayedStatus;
        public Integer delayedVarianceDays;
        public String projectNumber;
        public String projectName;
        public String ownerType;
        public LocalDate registrationDate;
        public LocalDate workStartDate;
        public String expectedCompletion;
        public String landUse;
        public String zone;
        public String sector;
        public String plotNumber;
        public String srcMunicipality;
        public String municipalityCode;
        /** EPSG:4326 点 */
        public String geomHex;
    }

    /** 建筑类申请：竣工证书、维修、拆除等（view_meps_cj_building_projects_dashboard）。 */
    public static class BuildingApplication {
        public String projectNumber;
        public String projectName;
        public String projectType;
        public String projectTypeName;
        /** completion / maintenance / demolition / other */
        public String kind;
        public String currentStatus;
        public LocalDate submissionDate;
        public String zone;
        public String sector;
        public String plotNumber;
        public String landUseGroup;
        public Boolean isLegalization;
        public LocalDate kpiStartDate;
        public LocalDate kpiEndDate;
        public LocalDate lastStatusDate;
        public Integer durationDays;
        public String srcMunicipality;
        public String municipalityCode;
    }
}
