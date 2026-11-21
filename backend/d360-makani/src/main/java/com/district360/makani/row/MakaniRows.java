package com.district360.makani.row;

import java.math.BigDecimal;

/** makani 查询结果行（MyBatis 按列名驼峰自动填充字段）。 */
public final class MakaniRows {

    private MakaniRows() {
    }

    public static class District {
        public Integer makaniId;
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
    }

    public static class Count {
        public Integer makaniId;
        public int n;
    }

    public static class Plot {
        public Integer makaniId;
        public int total;
        public int constructed;
        public int underConstruction;
        public int vacantServiced;
        public int vacantUnserviced;
    }

    public static class BuildingStat {
        public Integer makaniId;
        public int total;
        public int underConstruction;
    }

    public static class LandUse {
        public Integer makaniId;
        public String useDesc;
        public int plotCount;
    }

    public static class Building {
        public Integer makaniId;
        public String buildingId;
        public BigDecimal height;
        public boolean underConstruction;
        public String geomHex;
    }
}
