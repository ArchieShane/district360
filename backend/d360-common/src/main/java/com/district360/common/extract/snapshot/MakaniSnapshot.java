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
        List<Building> buildings) implements SourceSnapshot {

    @Override
    public int rowCount() {
        return districts.size() + landUse.size() + buildings.size();
    }

    /**
     * 每区一行（ADM 全部区）。所有计数都是「对象中心点落在区面内」的空间汇总。
     *
     * @param code   页面区编码，由 makani 区名生成
     * @param nameEn 显示用英文名
     * @param sortNo 按英文名排序的序号
     *
     * @param boundaryEwkbHex 简化后的区面（EPSG:4326，EWKB hex），只用于地图展示
     * @param scopeEwkbHex    未简化的区面，作为三个库共用的统计范围
     */
    public record District(
            String code,
            String nameEn,
            int sortNo,
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

    /** 建筑体块（EPSG:4326，EWKB hex）。 */
    public record Building(String code, String buildingId, BigDecimal height, boolean underConstruction, String ewkbHex) {
    }
}
