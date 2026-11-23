package com.district360.makani.row;

import java.math.BigDecimal;

/** makani 查询结果行（MyBatis 按列名驼峰自动填充字段）。 */
public final class MakaniRows {

    private MakaniRows() {
    }

    public static class District {
        /** udm_district.objectid：区的唯一键 */
        public Integer districtKey;
        public Integer makaniId;
        public String municipality;
        public String makaniName;
        public String nameAr;
        public BigDecimal areaKm2;
        public double centroidLon;
        public double centroidLat;
        public double minLon;
        public double minLat;
        public double maxLon;
        public double maxLat;
        public String boundaryHex;
        public String scopeHex;
    }

    public static class Count {
        public Integer districtKey;
        public int n;
    }

    public static class Plot {
        public Integer districtKey;
        public int total;
        public int constructed;
        public int underConstruction;
        public int vacantServiced;
        public int vacantUnserviced;
    }

    public static class BuildingStat {
        public Integer districtKey;
        public int total;
        public int underConstruction;
    }

    public static class LandUse {
        public Integer districtKey;
        public String useDesc;
        public int plotCount;
    }

    public static class PlotShape {
        public Integer districtKey;
        public String plotId;
        public String status;
        public String useDesc;
        /** 详情字段 JSON 文本 */
        public String attrs;
        public String geomHex;
    }

    public static class PoiRow {
        public Integer districtKey;
        public String category;
        public String sourceId;
        public String nameEn;
        public String nameAr;
        public String subtype;
        public double lon;
        public double lat;
        /** 详情字段 JSON 文本 */
        public String attrs;
    }

    public static class Building {
        public Integer districtKey;
        public String buildingId;
        public BigDecimal height;
        public boolean underConstruction;
        /** 详情字段 JSON 文本 */
        public String attrs;
        public String geomHex;
    }
}
