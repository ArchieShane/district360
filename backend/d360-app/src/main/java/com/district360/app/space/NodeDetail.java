package com.district360.app.space;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.util.List;
import java.util.Map;

/**
 * 空间下钻某一节点的完整视图（右侧 360 面板一次取齐）。
 *
 * @param level      层级 emirate / municipality / district / community / plot / building / floor / unit
 * @param path       上级链（从酋长国开始，不含自己），面包屑用
 * @param childLevel 下一级层级（单元为 null）
 * @param childCount 下一级个数（地块不含已注销）
 * @param bbox       [minLon, minLat, maxLon, maxLat]
 * @param center     面内一点 [lon, lat]
 * @param geometry   节点自身几何（GeoJSON，区域及以上已简化），地图高亮用
 * @param info       本层级的属性（列名驼峰），值为空的不返回
 * @param stat       计数汇总（地块数、已建、建筑、楼层、单元、面积……）
 * @param breakdowns 分组计数：plotStatus / landUse / buildingUse / buildingStatus / unitClass / unitType / bedrooms，
 *                   每项 [{k, n, area}]
 * @param layers     本层级可用的地图图层
 */
public record NodeDetail(String level, String key, String name, String nameAr, List<NodeRef> path,
                         String childLevel, long childCount, double[] bbox, double[] center,
                         @JsonRawValue String geometry, Map<String, Object> info, Map<String, Object> stat,
                         Map<String, List<Map<String, Object>>> breakdowns, List<String> layers) {

    public record NodeRef(String level, String key, String name) {
    }
}
