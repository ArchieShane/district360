package com.district360.ods.mapper;

import com.district360.common.theme.mkt.MktRows;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.ResultHandler;

/** ODS 市场取数，SQL 在 resources/mapper/ods/OdsMktMapper.xml；流式回调。 */
public interface OdsMktMapper {

    /** 表是否存在（schema.table 全名）。ADREC 表在部分 ODS 库里还没导入。 */
    @Select("SELECT to_regclass(#{name}) IS NOT NULL")
    boolean exists(@Param("name") String fullTableName);

    /** 数据日期：Tawtheeq 表入 ODS 的最晚时间。 */
    @Select("SELECT max(trs_moye_input_time)::date FROM ${t.tawtheeq}")
    java.time.LocalDate dataDate(@Param("t") OdsTables t);

    void tenancies(@Param("t") OdsTables t, ResultHandler<MktRows.Tenancy> handler);

    void sales(@Param("t") OdsTables t, ResultHandler<MktRows.Sale> handler);

    void mortgages(@Param("t") OdsTables t, ResultHandler<MktRows.Mortgage> handler);

    void rents(@Param("t") OdsTables t, ResultHandler<MktRows.Rent> handler);

    void plotSales(@Param("t") OdsTables t, ResultHandler<MktRows.PlotTransaction> handler);

    void plotMortgages(@Param("t") OdsTables t, ResultHandler<MktRows.PlotTransaction> handler);
}
