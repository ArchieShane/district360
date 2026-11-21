package com.district360.common.district;

/**
 * 一个区。区列表来自 makani：udm_district 中 municipalityname = ADM 的全部区（由 makani 抽取写入 stat.dim_district）。
 *
 * @param code             页面区编码，由 makani 区名生成（如 AL REEM ISLAND → al-reem-island），全系统主键
 * @param nameEn           显示用英文名（如 Al Reem Island）
 * @param makaniDistrictId makani udm_district.districtid（与 municipalityname = ADM 一起使用）
 * @param scopeEwkbHex     统计范围：makani 区面原始多边形（EPSG:4326，EWKB hex）。三个库都按这个范围做空间查询，
 *                         保证口径一致（各库的区 ID / 区名可能不一致，但范围一致）
 */
public record DistrictRef(String code, String nameEn, Integer makaniDistrictId, String scopeEwkbHex) {
}
