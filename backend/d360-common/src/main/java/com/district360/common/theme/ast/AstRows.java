package com.district360.common.theme.ast;

/** 设施主题的行类型（theme.ast_facility）。几何为 EPSG:4326 的 EWKB hex（点或面）。 */
public final class AstRows {

    private AstRows() {
    }

    /** 一处公共设施：学校 / 医疗 / 药房 / 清真寺 / 公园。 */
    public static class Facility {
        /** school / medical / pharmacy / mosque / park；DMT 公园明细先用 park_detail，写入后并入所在公园 */
        public String category;
        public String sourceId;
        public String nameEn;
        public String nameAr;
        public String subtype;
        public String subCategory;
        public Integer capacity;
        public Integer students;
        public Integer staff;
        public Integer floors;
        public Boolean isPublic;
        public String status;
        public String parkId;
        public String gisid;
        /** 详情字段（JSON 文本），写入 attrs jsonb */
        public String attrsJson;
        public String geomHex;
    }
}
