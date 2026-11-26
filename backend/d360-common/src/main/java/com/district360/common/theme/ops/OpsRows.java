package com.district360.common.theme.ops;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 城市运行主题的行类型（字段与 theme.ops_* 表对应）。几何为 EPSG:4326 点的 EWKB hex。 */
public final class OpsRows {

    private OpsRows() {
    }

    /** 路灯（makani_adwea_e_streetlight）。 */
    public static class Streetlight {
        public Long id;
        public Integer lampCount;
        public String status;
        public String lampType;
        public String roadType;
        public BigDecimal fixtureHeightM;
        public LocalDate installationDate;
        public String entity;
        public String geomHex;
    }

    /** 公交站台（makani_bus_stop），一行一个站台。 */
    public static class BusStop {
        public Long id;
        public String gtfsId;
        public String code;
        public String name;
        public String nameAr;
        public Integer stopNumber;
        public Integer pointType;
        public String geomHex;
    }
}
