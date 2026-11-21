package com.district360.stat.row;

import java.math.BigDecimal;

/** 区列表行（MyBatis 结果映射用，字段按列名驼峰自动填充）。 */
public class DistrictListRow {
    public String code;
    public String nameEn;
    public String nameAr;
    public int sortNo;
    public String municipality;
    public Integer makaniDistrictId;
    public Double centroidLon;
    public Double centroidLat;
    public Double minLon;
    public Double minLat;
    public Double maxLon;
    public Double maxLat;
    public BigDecimal areaKm2;
    public BigDecimal scoreCurrent;
    /** 是否在宜居评估范围；NULL 视为在范围内（旧数据） */
    public Boolean livInScope;
    public Boolean livSample;
    public boolean livPresent;
}
