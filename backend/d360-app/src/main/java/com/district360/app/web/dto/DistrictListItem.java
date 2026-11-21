package com.district360.app.web.dto;

import java.math.BigDecimal;

/**
 * 区列表 / 地图悬停用。
 *
 * @param centroid         [lon, lat]，makani 没有区面时为 null
 * @param bbox             [minLon, minLat, maxLon, maxLat]
 * @param liveabilityStatus ok / sample / not_assessed / unavailable
 */
public record DistrictListItem(
        String code,
        String nameEn,
        String nameAr,
        int sortNo,
        boolean mappingConfirmed,
        String mappingNote,
        double[] centroid,
        double[] bbox,
        BigDecimal areaKm2,
        BigDecimal liveabilityScore,
        String liveabilityStatus) {
}
