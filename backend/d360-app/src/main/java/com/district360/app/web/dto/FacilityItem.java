package com.district360.app.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 公共设施列表的一项。
 *
 * @param id       源表 objectid
 * @param category school / medical / mosque
 * @param location [lon, lat]
 * @param attrs    详情字段（学校：课程、学段、学生数等；医疗：子类型、专科、床位等），键名见接口文档
 */
public record FacilityItem(String id, String category, String nameEn, String nameAr, String subtype, double[] location,
                           JsonNode attrs) {
}
