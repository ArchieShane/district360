package com.district360.common.theme.mkt;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 市场主题的行类型（字段与 theme.mkt_* 表对应）。空间归属在主题库里关联。
 * 个人信息（租户 / 出租人 / 联系人姓名）不在这里，抽取端也不读。
 */
public final class MktRows {

    private MktRows() {
    }

    /** Tawtheeq 租约（makani_tbl_tawtheeq_all），一行 = 一份租约里的一个单元。 */
    public static class Tenancy {
        public String tenancyId;
        public String contractNum;
        public String tawtheeqUnitId;
        public String tawtheeqPropertyId;
        public String gisid;
        public String tenancyStatus;
        public String unitStatus;
        public LocalDate startDate;
        public LocalDate tenancyEndDate;
        public LocalDate terminationDate;
        public BigDecimal rentalValueAed;
        public String unitName;
        public String unitType;
        public String unitUsage;
        public BigDecimal unitAreaSqm;
        public Integer bedrooms;
        public String propertyName;
        public String propertyType;
        public String propertyUsage;
        public String actualUsage;
        public Integer yearBuilt;
        public String plotNumber;
        public String srcDistrict;
        public String srcCommunity;
    }

    /** ADREC 买卖（re_x_q21_matview_sale）。 */
    public static class Sale {
        public String saleApplicationId;
        public LocalDate contractDate;
        public String saleType;
        public BigDecimal priceAed;
        public BigDecimal normalizedPriceAed;
        public BigDecimal soldAreaSqm;
        public BigDecimal rateAedSqm;
        public String srcMunicipality;
        public String srcDistrict;
        public String srcCommunity;
        public String landPlot;
        public String buildingId;
        public String unitFloor;
        public String propertyCategory;
        public String propertyType;
        public String propertyLayout;
        public String projectName;
        public String developerName;
        public Double lon;
        public Double lat;
    }

    /** ADREC 抵押（re_x_q21_matview_mortgage）。 */
    public static class Mortgage {
        public String contractId;
        public LocalDate startDate;
        public LocalDate endDate;
        public BigDecimal amountAed;
        public String generalCategory;
        public String contractType;
        public String contractStatus;
        public String propertyType;
        public String srcMunicipality;
        public String srcDistrict;
        public String srcCommunity;
        public String landPlot;
        public String buildingId;
        public Double lon;
        public Double lat;
    }

    /** ADREC 租赁（re_x_q21_matview_rent）。 */
    public static class Rent {
        public String contractNumber;
        public LocalDate startDate;
        public LocalDate endDate;
        public BigDecimal annualValueAed;
        public BigDecimal totalValueAed;
        public BigDecimal rentedAreaSqm;
        public String applicationType;
        public String contractStatus;
        public String assetClass;
        public String propertyType;
        public String projectName;
        public String developerName;
        public String srcMunicipality;
        public String srcDistrict;
        public String srcCommunity;
        public String landPlot;
        public String buildingId;
        public Double lon;
        public Double lat;
    }

    /** ELMS 地块交易（re_elms_buy_and_sell_f → sale，re_elms_mortgages_f → mortgage）。 */
    public static class PlotTransaction {
        public String kind;
        public String txnId;
        public String elmsPlotId;
        public BigDecimal amountAed;
        public LocalDate txnDate;
        public String projectName;
    }
}
