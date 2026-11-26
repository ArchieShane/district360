package com.district360.stat.mapper;

import com.district360.common.theme.ops.OpsRows;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 主题库 · 城市运行写入与关联，SQL 在 resources/mapper/stat/ThemeOpsWriteMapper.xml。
 * 清表、ANALYZE、计数、登记复用 {@link ThemeSpatialWriteMapper}，备注复用 {@link ThemeMktWriteMapper#updateNote}。
 */
public interface ThemeOpsWriteMapper {

    void insertStreetlights(@Param("rows") List<OpsRows.Streetlight> rows);

    void insertBusStops(@Param("rows") List<OpsRows.BusStop> rows);

    /** 点落社区，社区没有的落区域。table 只来自代码常量（ops_streetlight / ops_bus_stop）。 */
    int linkPoints(@Param("table") String table);
}
