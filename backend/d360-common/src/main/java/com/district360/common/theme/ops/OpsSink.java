package com.district360.common.theme.ops;

import com.district360.common.theme.ThemeSink;

/**
 * 城市运行主题的写入端（source = ods-ops）：路灯、公交站台逐行写入。
 * 居民诉求、道路事故、视觉违规、违规处罚、住宅占用来自业务视图（ods_pop_view），源表不存在 / 为空时抽取器通过 {@link #sourceStatus} 报告，
 * 写入端记到表登记里（页面显示原因）。
 */
public interface OpsSink extends ThemeSink {

    void streetlight(OpsRows.Streetlight row);

    void busStop(OpsRows.BusStop row);

    /**
     * @param themeTable 主题表，如 ops_case
     * @param note       状态说明，如「ODS 源表无数据：ods.ods_view_adda_cases」
     */
    void sourceStatus(String themeTable, String note);

    /** 居民诉求工单（ADDA）。 */
    default void communityCase(OpsRows.Case row) {
    }

    default void accident(OpsRows.Accident row) {
    }

    default void distortion(OpsRows.Distortion row) {
    }

    default void violation(OpsRows.Violation row) {
    }

    default void occupancyCase(OpsRows.OccupancyCase row) {
    }
}
