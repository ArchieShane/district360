package com.district360.stat.mapper;

import com.district360.common.theme.mkt.MktRows;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 主题库 · 市场写入与关联，SQL 在 resources/mapper/stat/ThemeMktWriteMapper.xml。
 * 清表、ANALYZE、计数、登记复用 {@link ThemeSpatialWriteMapper}。
 */
public interface ThemeMktWriteMapper {

    void insertTenancies(@Param("rows") List<MktRows.Tenancy> rows);

    void insertSales(@Param("rows") List<MktRows.Sale> rows);

    void insertMortgages(@Param("rows") List<MktRows.Mortgage> rows);

    void insertRents(@Param("rows") List<MktRows.Rent> rows);

    void insertPlotTransactions(@Param("rows") List<MktRows.PlotTransaction> rows);

    /** Tawtheeq → 地块（gisid）及其社区 / 区域。 */
    int linkTenancies();

    /** ADREC 买卖 / 抵押 / 租赁：坐标落地块、建筑；社区 / 区域随地块，没有地块的按坐标落社区。table 只来自代码常量。 */
    int linkByLocation(@Param("table") String table);

    /** ELMS 地块交易 → 地块（elms_plot_id）。 */
    int linkPlotTransactions();

    @Update("UPDATE theme.meta_table SET note = #{note} WHERE table_name = #{table}")
    void updateNote(@Param("table") String table, @Param("note") String note);
}
