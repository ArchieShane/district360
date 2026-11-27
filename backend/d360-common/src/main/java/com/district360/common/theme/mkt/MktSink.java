package com.district360.common.theme.mkt;

import com.district360.common.theme.ThemeSink;

/**
 * 市场主题的写入端（source = ods-mkt）：Tawtheeq 租约、ADREC 买卖 / 抵押 / 租赁、ELMS 地块交易。
 * 源表在 ODS 里不存在时，抽取器调用 {@link #sourceMissing}，写入端在表登记里记下原因（页面显示「源表未导入」）。
 */
public interface MktSink extends ThemeSink {

    void tenancy(MktRows.Tenancy row);

    void sale(MktRows.Sale row);

    void mortgage(MktRows.Mortgage row);

    void rent(MktRows.Rent row);

    void plotTransaction(MktRows.PlotTransaction row);

    /**
     * @param themeTable  受影响的主题表，如 mkt_sale
     * @param sourceTable 缺失的 ODS 源表全名
     */
    void sourceMissing(String themeTable, String sourceTable);

    /** Tawtheeq 单元状态（业务视图 view_vw_tawtheeq_unit_status）。 */
    default void unitStatus(MktRows.UnitStatus row) {
    }

    /** 源表存在但没有数据（业务视图）；写入端在表登记里记下「源表无数据」。 */
    default void sourceEmpty(String themeTable, String sourceTable) {
    }
}
