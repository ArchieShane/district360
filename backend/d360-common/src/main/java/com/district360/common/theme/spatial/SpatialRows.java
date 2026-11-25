package com.district360.common.theme.spatial;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 空间主题的行类型（字段与 theme.sp_* 表一一对应，下划线自动转驼峰）。
 * 用公有字段的普通类而不是 record：既做 MyBatis 结果类型（抽取端），也做写入参数（主题库端）。
 * <p>
 * 编码规则（*Key）由抽取器按源库编号拼好：区域 = 市政-districtid，社区 = 区域编码-communityid，
 * 地块 = gisid，建筑 = udm-内部编号（BDMS 推导的建筑为 bdms-市政-buildingid），楼层 = 市政-floorid，单元 = 市政-unitid
 * （BDMS 编号只在市政内唯一，见 V12 迁移）。
 */
public final class SpatialRows {

    private SpatialRows() {
    }

    public static class Municipality {
        public String municipalityCode;
        public Integer municipalityId;
        public String nameEn;
        public String nameAr;
        public String geomHex;
    }

    public static class District {
        public String districtKey;
        public String municipalityCode;
        public Integer districtId;
        public Integer srcObjectid;
        public String nameEn;
        public String nameAr;
        public String popularNameEn;
        public BigDecimal population;
        public String geomHex;
    }

    public static class Community {
        public String communityKey;
        public String districtKey;
        public String municipalityCode;
        public Integer districtId;
        public String communityId;
        public Integer srcObjectid;
        public String nameEn;
        public String nameAr;
        public String popularNameEn;
        public String compositeNameEn;
        public String geomHex;
    }

    public static class Plot {
        public String plotKey;
        public String gisid;
        public String plotId;
        public String plotNumber;
        public String elmsPlotId;
        public String srcMunicipality;
        public String srcDistrictId;
        public String srcDistrictEn;
        public String srcCommunityId;
        public String srcCommunityEn;
        public BigDecimal areaSqm;
        public BigDecimal plannedAreaSqm;
        public String constructionStatus;
        public String serviceStatus;
        public String planningStatus;
        public String plotStatus;
        public String primaryUse;
        public String parentLandUse;
        public String landUse;
        public String secondaryUse;
        public String ownershipType;
        public String allocationStatus;
        public String investmentName;
        public String devcode;
        public String devcodeCategory;
        public String devcodeFar;
        public BigDecimal devcodeMaxGfa;
        public String maxHeights;
        public String zoneTpss;
        public String sectorTpss;
        public String projectId;
        public String permitLastType;
        public LocalDateTime permitLastDate;
        public LocalDateTime cancelDate;
        public LocalDateTime modifiedDate;
        public LocalDateTime dataReceivedDate;
        public String geomHex;
    }

    public static class Building {
        public String buildingKey;
        public String source;
        public Integer udmObjectid;
        public String buildingId;
        public Integer buildingInternalId;
        public String gisid;
        public String elmsPlotId;
        public String nameEn;
        public String nameAr;
        public String physicalStatus;
        public String primaryUsageType;
        public String primaryUse;
        public String secondaryUsageType;
        public Integer floorsCount;
        public BigDecimal heightM;
        public LocalDateTime completionDate;
        public LocalDateTime demolitionDate;
        public LocalDateTime startDate;
        public Integer permitId;
        public Integer constructionSiteId;
        public String isBdms;
        public String geomHex;
    }

    public static class Floor {
        public String floorKey;
        public Long floorId;
        public Long bdmsBuildingId;
        /** BDMS 编号所属市政：BDMS 编号只在市政内唯一 */
        public String bdmsMunicipality;
        public String floorNumber;
        public BigDecimal baseHeightM;
        public BigDecimal floorHeightM;
        public BigDecimal areaSqm;
        public String buildingStatus;
        public String plotPrimaryUse;
        public String geomHex;
    }

    public static class Unit {
        public String unitKey;
        public Long unitId;
        public Long floorId;
        public Long bdmsBuildingId;
        public String bdmsMunicipality;
        public String unitNumber;
        public String unitType;
        public String usageType;
        public String classification;
        public String category;
        public BigDecimal areaSqm;
        public Integer bedrooms;
        public Integer bathrooms;
        public BigDecimal balconyAreaSqm;
        public String floorNumber;
        public BigDecimal baseHeightM;
        public BigDecimal floorHeightM;
        public String buildingStatus;
        public String geomHex;
    }
}
