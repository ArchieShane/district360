package com.district360.stat.mapper;

import com.district360.common.theme.dev.DevRows;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 主题库 · 开发建设写入与关联，SQL 在 resources/mapper/stat/ThemeDevWriteMapper.xml。
 * 清表、ANALYZE、计数、登记复用 {@link ThemeSpatialWriteMapper}。
 */
public interface ThemeDevWriteMapper {

    void insertMasterPlans(@Param("rows") List<DevRows.MasterPlan> rows);

    void insertMasterPlanPlots(@Param("rows") List<DevRows.MasterPlanPlot> rows);

    void insertConstructionSites(@Param("rows") List<DevRows.ConstructionSite> rows);

    /** 总规项目 → 社区 / 区域：项目面内点落在社区面内，社区没有的落区域。 */
    int linkMasterPlans();

    /** 总规地块 → 地块：先 udm_gisid，再面内点落在未注销地块面内。 */
    int linkMasterPlanPlotsByGisid();

    int linkMasterPlanPlotsByLocation();

    /** 总规地块 / 在建工地的社区、区域、市政随地块；没有地块的按位置落社区 / 区域。 */
    int propagateMasterPlanPlots();

    int linkConstructionSites();

    /** 在建工地的在建建筑数（sp_building.construction_site_id）。 */
    int countSiteBuildings();
}
