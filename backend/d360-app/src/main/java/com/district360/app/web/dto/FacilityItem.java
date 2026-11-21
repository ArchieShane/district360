package com.district360.app.web.dto;

/**
 * 公共设施列表的一项。
 *
 * @param id       源表 objectid
 * @param category school / medical / mosque
 * @param location [lon, lat]
 */
public record FacilityItem(String id, String category, String nameEn, String nameAr, String subtype, double[] location) {
}
