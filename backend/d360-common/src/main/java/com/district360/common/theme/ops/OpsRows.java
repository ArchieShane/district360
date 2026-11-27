package com.district360.common.theme.ops;

import java.time.LocalDateTime;
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

    /** 居民诉求工单（view_adda_cases），同一工单号取最新一行。 */
    public static class Case {
        public String caseNumber;
        public LocalDateTime dateOpened;
        public LocalDateTime dateClosed;
        public String status;
        public Boolean closed;
        public String slaColor;
        public String serviceCategory;
        public String serviceName;
        public String category1;
        public String category2;
        public String caseType;
        public String areaEn;
        public String entity;
        public String priority;
        public String channel;
        public String geomHex;
    }

    /** 道路事故（view_accident_master_new）。 */
    public static class Accident {
        public String reportNo;
        public LocalDateTime reportDate;
        public String severity;
        public String accidentKind;
        public String reportKind;
        public String street;
        public String emirate;
        public String geomHex;
    }

    /** 视觉违规上报（view_ins_distortion，isdistortion 为真）。 */
    public static class Distortion {
        public String issueNumber;
        public LocalDateTime raisedOn;
        public LocalDateTime closedDate;
        public BigDecimal maxTimeToResolve;
        public BigDecimal daysPassed;
        public String status;
        public Boolean isDuplicate;
        public Boolean isAwareness;
        public Boolean convertedToWarning;
        public Boolean convertedToViolation;
        public Boolean withinSla;
        public String serviceName;
        public String violationName;
        public String responsibleEntity;
        public String orgId;
        public String serviceCenter;
        public String zoneName;
        public String sectorName;
        public String plotNumber;
        public String plotGisId;
        public String municipalityCode;
        public String geomHex;
    }

    /** 违规与处罚（view_ins_violations，补 view_ins_violations_uis 里没有的号；地块经 view_ins_violation_trx_result）。 */
    public static class Violation {
        public String issueNumber;
        /** ins / uis */
        public String source;
        public LocalDateTime raisedOn;
        public LocalDateTime closedDate;
        public String status;
        public String issueStatus;
        public String currentActivity;
        public Boolean isDuplicate;
        /** awareness / warning / fine */
        public String stage;
        public BigDecimal fineAmount;
        public BigDecimal receivedAmount;
        public String paymentStatus;
        public String violationName;
        public String serviceName;
        public String zoneName;
        public String sectorName;
        public String plotNumber;
        public String plotGisId;
        public String result;
        public String municipalityCode;
        public String geomHex;
    }

    /** 住宅占用（超员）案件（view_ins_survey）。 */
    public static class OccupancyCase {
        public String issueNumber;
        public LocalDateTime raisedOn;
        public LocalDateTime closedDate;
        public String status;
        public String currentActivity;
        public Boolean isUrgent;
        public BigDecimal fineAmount;
        public String serviceName;
        public String violationName;
        public String zoneName;
        public String sectorName;
        public String plotNumber;
        public String municipalityCode;
        public String geomHex;
    }
}
