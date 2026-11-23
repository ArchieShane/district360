package com.district360.stat.row;

import java.math.BigDecimal;

/** 地图点击详情：地块或建筑一行（两者共用，未用到的字段为 null）。 */
public class MapFeatureRow {
    public long id;
    public String code;
    /** 地块 plot_id / 建筑 building_id */
    public String refId;
    /** 地块：建设状态分类、主用途 */
    public String status;
    public String useDesc;
    /** 建筑：高度（米）、是否在建 */
    public BigDecimal height;
    public Boolean underConstruction;
    /** 详情字段 JSON 文本 */
    public String attrs;
    /** 卡片定位点（对象内一点，EPSG:4326） */
    public double lon;
    public double lat;
    /** 地块：上面的建筑数 */
    public Integer relatedCount;
    /** 建筑：所在地块 */
    public Long relatedId;
    public String relatedRef;
    public String relatedName;
}
