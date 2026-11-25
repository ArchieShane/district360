package com.district360.common.theme.spatial;

import com.district360.common.theme.ThemeSink;

/**
 * 只同步单元表的写入端（source = ods-units）：单元表在 ODS 重新导入后，只重写 theme.sp_unit，
 * 再补齐由单元推导的楼层 / 建筑并重算汇总，不重抽区域、社区、地块、建筑、楼层。
 * 前提：已经做过一次 ods-spatial 全量抽取（楼层、建筑在主题库里）。
 */
public interface UnitSink extends ThemeSink {

    void unit(SpatialRows.Unit row);
}
