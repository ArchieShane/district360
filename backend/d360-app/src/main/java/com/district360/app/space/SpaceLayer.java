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
    UNITS(20000, 0);

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
