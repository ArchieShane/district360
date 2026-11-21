package com.district360.app.web.dto;

import java.math.BigDecimal;

/**
 * 区列表的一行（左侧列表，分页加载）。
 *
 * @param centroid          [lon, lat]
 * @param bbox              [minLon, minLat, maxLon, maxLat]
 * @param liveabilityScore  宜居得分「目前」；不在宜居评估范围的区为 null
 * @param liveabilityStatus ok / sample / not_assessed（不在宜居评估范围）/ unavailable
 */
public record DistrictListItem(
        String code,
        String nameEn,
        String nameAr,
        int sortNo,
        String municipality,
        Integer makaniDistrictId,
        double[] centroid,
        double[] bbox,
        BigDecimal areaKm2,
        BigDecimal liveabilityScore,
        String liveabilityStatus) {
}
