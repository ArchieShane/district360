package com.district360.stat.mapper;

import com.district360.common.theme.dev.DevRows;
import com.district360.common.theme.mkt.MktRows;
import com.district360.common.theme.ops.OpsRows;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 主题库 · 业务视图（ods_pop_view）写入与空间归属，SQL 在 resources/mapper/stat/ThemeBizWriteMapper.xml。
 * 表名、列名参数只来自代码常量。
 */
public interface ThemeBizWriteMapper {

    void insertCapitalProjects(@Param("rows") List<DevRows.CapitalProject> rows);

    void insertPermits(@Param("rows") List<DevRows.Permit> rows);

    void insertBuildingApplications(@Param("rows") List<DevRows.BuildingApplication> rows);

    void insertCases(@Param("rows") List<OpsRows.Case> rows);

    void insertAccidents(@Param("rows") List<OpsRows.Accident> rows);

    void insertDistortions(@Param("rows") List<OpsRows.Distortion> rows);

    void insertViolations(@Param("rows") List<OpsRows.Violation> rows);

    void insertOccupancyCases(@Param("rows") List<OpsRows.OccupancyCase> rows);

    void insertUnitStatuses(@Param("rows") List<MktRows.UnitStatus> rows);

    /** gisid 列 → 地块（及其社区、区域、市政）。 */
    int linkGisid(@Param("table") String table, @Param("col") String gisidCol);

    /** gisid 对不上时，按 plot_id（市政内唯一）再对一次。 */
    int linkPlotId(@Param("table") String table, @Param("col") String col);

    /** zone + sector + 地块号 → 地块。 */
    int linkZoneSectorPlot(@Param("table") String table, @Param("zone") String zone, @Param("sector") String sector,
                           @Param("plot") String plot);

    /** 位置 → 地块（withPlot）/ 社区 / 区域，只处理还没有区域的行。 */
    int linkLocation(@Param("table") String table, @Param("geom") String geomCol, @Param("withPlot") boolean withPlot);

    int linkProjectsByDistrictName();

    int flagProjects();

    int linkUnitStatuses();
}
