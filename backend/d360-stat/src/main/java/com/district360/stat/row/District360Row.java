package com.district360.stat.row;

import java.math.BigDecimal;
import java.time.LocalDate;

/** v_district_360 一行。 */
public class District360Row {
    public String code;
    public String nameEn;
    public String nameAr;
    public int sortNo;
    public boolean mappingConfirmed;
    public String mappingNote;

    public BigDecimal areaKm2;
    public Integer communities;
    public Integer plots;
    public Integer plotsConstructed;
    public Integer plotsUnderConstruction;
    public Integer plotsVacantServiced;
    public Integer plotsVacantUnserviced;
    public Integer buildings;
    public Integer buildingsUnderConstruction;
    public Integer schools;
    public Integer medical;
    public Integer mosques;
    public LocalDate makaniAsOf;

    public BigDecimal scoreExisting;
    public BigDecimal scoreCurrent;
    public BigDecimal scoreTarget;
    public Integer parks;
    public BigDecimal parkAreaHa;
    public BigDecimal parkGapHa;
    public Boolean livSample;
    public LocalDate livAsOf;

    public LocalDate periodStartMonth;
    public LocalDate periodEndMonth;
    public BigDecimal salesAed;
    public BigDecimal salesYoyPct;
    public Integer transactions;
    public Integer mortgages;
    public BigDecimal avgPriceSqm;
    public Integer activeLeases;
    public Boolean adrecSample;
    public LocalDate adrecAsOf;
}
