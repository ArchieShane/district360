package com.district360.app.web.dto;

import java.math.BigDecimal;

/**
 * 区列表的一行（左侧列表，分页加载）。
 *
 * @param centroid          [lon, lat]
 * @param bbox              [minLon, minLat, maxLon, maxLat]
 * @param liveabilityStatus ok / sample / not_assessed / unavailable
 */
public record DistrictListItem(
        String code,
        String nameEn,
        String nameAr,
        int sortNo,
        Integer makaniDistrictId,
        double[] centroid,
        double[] bbox,
        BigDecimal areaKm2,
        BigDecimal liveabilityScore,
        String liveabilityStatus) {
}
