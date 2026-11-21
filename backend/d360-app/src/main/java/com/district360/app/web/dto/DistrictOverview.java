package com.district360.app.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 右侧面板：标题 + 5 个区块（摘要由前端按模板拼）。 */
public record DistrictOverview(
        Header district,
        Liveability liveability,
        Land land,
        RealEstate realEstate,
        Facilities facilities) {

    public record Header(String code, String nameEn, String nameAr, String municipality, BigDecimal areaKm2,
                         Integer communities, Integer plots, boolean mappingConfirmed, String mappingNote) {
    }

    public record Liveability(String status, LocalDate asOf, BigDecimal existing, BigDecimal current, BigDecimal target,
                              BigDecimal gap) {
    }

    public record Land(String status, LocalDate asOf, Integer plots, Integer buildings, Integer buildingsUnderConstruction,
                       BigDecimal plotBuiltRatePct, PlotStatus plotStatus, List<LandUseShare> landUse) {
    }

    public record PlotStatus(Integer constructed, Integer underConstruction, Integer vacantServiced, Integer vacantUnserviced) {
    }

    /** @param highlight 是否显示在面板「土地利用」一行；fallback 为「未列出用途」的兜底类别 */
    public record LandUseShare(String key, String nameZh, String nameEn, int plotCount, BigDecimal pct, boolean highlight, boolean fallback) {
    }

    public record RealEstate(String status, LocalDate asOf, String periodStart, String periodEnd, BigDecimal salesAedBn,
                             BigDecimal salesYoyPct, Integer transactions, Integer mortgages, BigDecimal avgPriceSqm,
                             Integer activeLeases, List<MonthlyCount> monthly) {
    }

    public record MonthlyCount(String month, int transactions) {
    }

    public record Facilities(String status, LocalDate asOf, Integer schools, Integer medical, Integer mosques, Parks parks) {
    }

    public record Parks(String status, LocalDate asOf, Integer count, BigDecimal areaHa, BigDecimal gapHa) {
    }
}
