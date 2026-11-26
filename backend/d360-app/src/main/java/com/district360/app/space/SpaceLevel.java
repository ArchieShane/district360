package com.district360.app.space;

import java.util.List;
import java.util.Locale;

/**
 * 空间下钻层级：酋长国 → 市政 → 区域 → 社区 → 地块 → 建筑 → 楼层 → 单元。
 * col 是下级各表里指向本级的编码列（主题库各级表都冗余了全部上级编码）；dims / layers 是本级可用的分组与地图图层。
 */
public enum SpaceLevel {

    EMIRATE(null, List.of(SpaceDim.PLOT_STATUS, SpaceDim.LAND_USE, SpaceDim.BUILDING_USE, SpaceDim.UNIT_CLASS),
            List.of(SpaceLayer.MUNICIPALITIES, SpaceLayer.DISTRICTS)),
    MUNICIPALITY("municipality_code", List.of(SpaceDim.PLOT_STATUS, SpaceDim.LAND_USE, SpaceDim.BUILDING_USE, SpaceDim.UNIT_CLASS),
            List.of(SpaceLayer.DISTRICTS)),
    DISTRICT("district_key", List.of(SpaceDim.PLOT_STATUS, SpaceDim.LAND_USE, SpaceDim.BUILDING_USE, SpaceDim.UNIT_CLASS),
            List.of(SpaceLayer.COMMUNITIES, SpaceLayer.PLOTS, SpaceLayer.BUILDINGS, SpaceLayer.MASTERPLANS, SpaceLayer.SITES)),
    COMMUNITY("community_key", List.of(SpaceDim.PLOT_STATUS, SpaceDim.LAND_USE, SpaceDim.BUILDING_USE, SpaceDim.UNIT_CLASS),
            List.of(SpaceLayer.PLOTS, SpaceLayer.BUILDINGS, SpaceLayer.MASTERPLANS, SpaceLayer.SITES)),
    PLOT("plot_key", List.of(SpaceDim.BUILDING_USE, SpaceDim.BUILDING_STATUS, SpaceDim.UNIT_CLASS),
            List.of(SpaceLayer.BUILDINGS, SpaceLayer.SITES)),
    BUILDING("building_key", List.of(SpaceDim.UNIT_CLASS, SpaceDim.UNIT_TYPE, SpaceDim.BEDROOMS),
            List.of(SpaceLayer.FLOORS, SpaceLayer.UNITS)),
    FLOOR("floor_key", List.of(SpaceDim.UNIT_CLASS, SpaceDim.BEDROOMS),
            List.of(SpaceLayer.UNITS)),
    UNIT(null, List.of(), List.of());

    private final String col;
    private final List<SpaceDim> dims;
    private final List<SpaceLayer> layers;

    SpaceLevel(String col, List<SpaceDim> dims, List<SpaceLayer> layers) {
        this.col = col;
        this.dims = dims;
        this.layers = layers;
    }

    /** 下级表里指向本级的列；酋长国为 null（不过滤）。 */
    public String col() {
        return col;
    }

    public List<SpaceDim> dims() {
        return dims;
    }

    public List<SpaceLayer> layers() {
        return layers;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 下一级；单元没有下一级。 */
    public SpaceLevel child() {
        return this == UNIT ? null : values()[ordinal() + 1];
    }

    public static SpaceLevel of(String s) {
        for (SpaceLevel l : values()) {
            if (l.id().equals(s)) {
                return l;
            }
        }
        throw new IllegalArgumentException("未知的层级: " + s + "（emirate / municipality / district / community / plot / building / floor / unit）");
    }
}
