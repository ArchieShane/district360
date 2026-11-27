package com.district360.ods.mapper;

import com.district360.common.theme.dev.DevRows;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.ResultHandler;

import java.time.LocalDate;

/** ODS 开发建设取数，SQL 在 resources/mapper/ods/OdsDevMapper.xml；流式回调。 */
public interface OdsDevMapper {

    @Select("SELECT to_regclass(#{name}) IS NOT NULL")
    boolean exists(@Param("name") String fullTableName);

    /** 表里是否有行。table 只来自 {@link OdsTables}（配置拼出的表名），不来自请求。 */
    @Select("SELECT EXISTS (SELECT 1 FROM ${table})")
    boolean hasRows(@Param("table") String fullTableName);

    void masterPlans(@Param("t") OdsTables t, ResultHandler<DevRows.MasterPlan> handler);

    void masterPlanPlots(@Param("t") OdsTables t, ResultHandler<DevRows.MasterPlanPlot> handler);

    void constructionSites(@Param("t") OdsTables t, ResultHandler<DevRows.ConstructionSite> handler);

    /** 需要点表、面表都存在。 */
    void capitalProjects(@Param("t") OdsTables t, ResultHandler<DevRows.CapitalProject> handler);

    void permits(@Param("t") OdsTables t, ResultHandler<DevRows.Permit> handler);

    void buildingApplications(@Param("t") OdsTables t, ResultHandler<DevRows.BuildingApplication> handler);

    /** 数据日期：总规项目表入 ODS 的最晚时间。 */
    LocalDate dataDate(@Param("t") OdsTables t);
}
