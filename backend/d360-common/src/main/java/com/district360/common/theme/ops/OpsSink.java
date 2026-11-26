package com.district360.common.theme.ops;

import com.district360.common.theme.ThemeSink;

/**
 * 城市运行主题的写入端（source = ods-ops）：路灯、公交站台逐行写入。
 * 居民诉求、道路事故、视觉违规、违规处罚、住宅占用的源视图还没有数据，抽取器只检查并通过 {@link #sourceStatus} 报告，
 * 写入端记到表登记里（页面显示原因）。有了数据再在这里加对应的方法。
 */
public interface OpsSink extends ThemeSink {

    void streetlight(OpsRows.Streetlight row);

    void busStop(OpsRows.BusStop row);

    /**
     * @param themeTable 主题表，如 ops_case
     * @param note       状态说明，如「ODS 源表无数据：ods.ods_view_adda_cases」
     */
    void sourceStatus(String themeTable, String note);
}
