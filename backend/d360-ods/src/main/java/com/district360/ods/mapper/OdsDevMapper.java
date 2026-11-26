package com.district360.ods.mapper;

import com.district360.common.theme.dev.DevRows;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;

import java.time.LocalDate;

/** ODS 开发建设取数，SQL 在 resources/mapper/ods/OdsDevMapper.xml；流式回调。 */
public interface OdsDevMapper {

    void masterPlans(@Param("t") OdsTables t, ResultHandler<DevRows.MasterPlan> handler);

    void masterPlanPlots(@Param("t") OdsTables t, ResultHandler<DevRows.MasterPlanPlot> handler);

    void constructionSites(@Param("t") OdsTables t, ResultHandler<DevRows.ConstructionSite> handler);

    /** 数据日期：总规项目表入 ODS 的最晚时间。 */
    LocalDate dataDate(@Param("t") OdsTables t);
}
