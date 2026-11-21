package com.district360.common.district;

/**
 * 区字典里的一行。页面统一用 {@code code} 标识区，各数据源的 ID 由这里翻译。
 *
 * @param code              页面区编码（如 reem），全系统主键
 * @param nameEn            页面显示的英文名
 * @param sortNo            排序号
 * @param makaniDistrictId  makani udm_district.districtid（与 municipalityname = ADM 一起使用）；null 表示 makani 没有对应区
 * @param livDistrictId     Liveability dim_districts.district_id；null 表示不在宜居评估范围
 * @param mappingConfirmed  对应关系是否已经确认
 * @param mappingNote       对应关系备注（待确认原因等）
 * @param scopeEwkbHex      统计范围：makani 区面原始多边形（EPSG:4326，EWKB hex）。不在区字典里配置，
 *                          由框架在调用宜居 / ADREC 抽取器前从统计库填入。三个库都按这个范围做空间查询，
 *                          保证口径一致（各库的区 ID 可能不一致，但范围一致）。
 */
public record DistrictRef(
        String code,
        String nameEn,
        int sortNo,
        Integer makaniDistrictId,
        Integer livDistrictId,
        boolean mappingConfirmed,
        String mappingNote,
        String scopeEwkbHex) {

    public DistrictRef withScope(String scopeEwkbHex) {
        return new DistrictRef(code, nameEn, sortNo, makaniDistrictId, livDistrictId, mappingConfirmed, mappingNote, scopeEwkbHex);
    }
}
