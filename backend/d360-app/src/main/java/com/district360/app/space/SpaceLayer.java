package com.district360.app.space;

import java.util.Locale;

/** 地图图层（白名单）：名称、最大要素数、简化容差（度）。 */
public enum SpaceLayer {

    MUNICIPALITIES(10, 0.0005),
    DISTRICTS(500, 0.00005),
    COMMUNITIES(5000, 0.00002),
    PLOTS(30000, 0),
    BUILDINGS(30000, 0),
    FLOORS(2000, 0),
    UNITS(20000, 0),
    /** 开发建设：总体规划项目面（要素 level = masterplan，不是下钻层级）。 */
    MASTERPLANS(2000, 0.00001),
    /** 开发建设：在建工地面（要素 level = site）。 */
    SITES(5000, 0),
    /** 城市运行：公交站台点（要素 level = busstop）。 */
    BUSSTOPS(5000, 0),
    /** 城市运行：路灯点（要素 level = streetlight）。 */
    STREETLIGHTS(30000, 0),
    /** 设施：学校 / 医疗 / 药房 / 清真寺 / 公园的位置点（要素 level = facility，属性 c 为类别）。 */
    FACILITIES(10000, 0);

    private final int limit;
    private final double tolerance;

    SpaceLayer(int limit, double tolerance) {
        this.limit = limit;
        this.tolerance = tolerance;
    }

    public int limit() { return limit; }
    public double tolerance() { return tolerance; }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SpaceLayer of(String s) {
        for (SpaceLayer l : values()) {
            if (l.id().equals(s)) {
                return l;
            }
        }
        throw new IllegalArgumentException("未知的图层: " + s);
    }
}
