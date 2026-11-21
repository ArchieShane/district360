package com.district360.common.extract.snapshot;

import com.district360.common.extract.SourceSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * makani 抽取结果：区面、区级计数、土地利用、建筑体块。
 */
public record MakaniSnapshot(
        LocalDate sourceAsOf,
        List<District> districts,
        List<LandUse> landUse,
        List<Building> buildings,
        List<Plot> plots) implements SourceSnapshot {

    @Override
    public int rowCount() {
        return districts.size() + landUse.size() + buildings.size() + plots.size();
    }

    /**
     * 每区一行（udm_district 全部区，所有市政）。所有计数都是「对象中心点落在区面内」的空间汇总。
     *
     * @param code         页面区编码：市政 + 区名，如 adm-al-reem-island（区名跨市政会重复）
     * @param nameEn       显示用英文名
     * @param sortNo       按区名排序的序号
     * @param municipality makani municipalityname（ADM / AAM / WRM）
     *
     * @param boundaryEwkbHex 简化后的区面（EPSG:4326，EWKB hex），只用于地图展示
     * @param scopeEwkbHex    未简化的区面，作为三个库共用的统计范围
     */
    public record District(
            String code,
            String nameEn,
            int sortNo,
            String municipality,
            Integer makaniDistrictId,
            String makaniName,
            String nameAr,
            BigDecimal areaKm2,
            double centroidLon,
            double centroidLat,
            double minLon,
            double minLat,
            double maxLon,
            double maxLat,
            String boundaryEwkbHex,
            String scopeEwkbHex,
            int communities,
            int plots,
            int plotsConstructed,
            int plotsUnderConstruction,
            int plotsVacantServiced,
            int plotsVacantUnserviced,
            int buildings,
            int buildingsUnderConstruction,
            int schools,
            int medical,
            int mosques) {
    }

    /** 区 × makani 原始用途（udm_plot.primaryuseengdesc）的地块数；归类规则在读接口里按配置做。 */
    public record LandUse(String code, String useDesc, int plotCount) {
    }

    /**
     * 地块（EPSG:4326，EWKB hex），供地图按建设状态高亮。
     *
     * @param status constructed / under_construction / vacant_serviced / vacant_unserviced / other，与面板「地块状态」4 类一致
     */
    public record Plot(String code, String plotId, String status, String useDesc, String ewkbHex) {
    }

    /** 建筑体块（EPSG:4326，EWKB hex）。 */
    public record Building(String code, String buildingId, BigDecimal height, boolean underConstruction, String ewkbHex) {
    }
}
