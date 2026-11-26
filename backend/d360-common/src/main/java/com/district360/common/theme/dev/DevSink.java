package com.district360.common.theme.dev;

import com.district360.common.theme.ThemeSink;

/**
 * 开发建设主题的写入端（source = ods-dev）：总体规划、总规地块、在建工地，逐行写入。
 * 资本项目、建设许可、竣工 / 维修 / 拆除的源视图在 ODS 里还没有数据，接入时在这里加方法。
 */
public interface DevSink extends ThemeSink {

    void masterPlan(DevRows.MasterPlan row);

    void masterPlanPlot(DevRows.MasterPlanPlot row);

    void constructionSite(DevRows.ConstructionSite row);
}
