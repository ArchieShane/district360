package com.district360.app.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * 地图点击详情：地块或建筑。没有值的字段不输出。
 *
 * @param kind      plot / building
 * @param id        统计库行 id（与矢量瓦片里的 id 属性一致）
 * @param code      所在区编码
 * @param refId     地块 plot_id / 建筑 building_id（源表编号，可能为空）
 * @param status    地块：constructed / under_construction / vacant_serviced / vacant_unserviced / other
 * @param useDesc   地块：主用途（udm_plot.primaryuseengdesc）
 * @param height    建筑：高度（米；源表无高度时按层数 × 3.5 估算）
 * @param location  卡片定位点 [lon, lat]（对象内一点）
 * @param attrs     源表详情字段，键名见接口文档
 * @param buildings 地块：中心点落在地块内的建筑数
 * @param plot      建筑：所在地块
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapFeatureDetail(String kind, long id, String code, String refId, String status, String useDesc,
                               BigDecimal height, Boolean underConstruction, double[] location, JsonNode attrs,
                               Integer buildings, PlotRef plot) {

    /** 建筑所在地块：id 可再调地块详情接口 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlotRef(long id, String plotId, String plotNo) {
    }
}
