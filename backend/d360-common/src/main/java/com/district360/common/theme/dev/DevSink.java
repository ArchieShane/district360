package com.district360.common.theme.dev;

import com.district360.common.theme.ThemeSink;

/**
 * 开发建设主题的写入端（source = ods-dev）：总体规划、总规地块、在建工地，逐行写入。
 * 资本项目、建设许可、竣工 / 维修 / 拆除来自业务视图（ods_pop_view），源表为空时只报告状态。
 */
public interface DevSink extends ThemeSink {

    void masterPlan(DevRows.MasterPlan row);

    void masterPlanPlot(DevRows.MasterPlanPlot row);

    void constructionSite(DevRows.ConstructionSite row);

    /** 资本项目（业务视图，源表为空时不调用）。 */
    default void capitalProject(DevRows.CapitalProject row) {
    }

    /** 建设许可。 */
    default void permit(DevRows.Permit row) {
    }

    /** 竣工证书 / 维修 / 拆除等建筑类申请。 */
    default void buildingApplication(DevRows.BuildingApplication row) {
    }

    /**
     * 业务视图源表的状态（不存在 / 无数据），写入端记到表登记备注；有数据时传 null 清掉旧备注。
     *
     * @param themeTable 主题表，如 dev_capital_project
     */
    default void sourceStatus(String themeTable, String note) {
    }
}
