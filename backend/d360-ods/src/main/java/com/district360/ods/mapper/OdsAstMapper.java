package com.district360.ods.mapper;

import com.district360.common.theme.ast.AstRows;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.ResultHandler;

import java.time.LocalDate;

/** ODS 设施取数，SQL 在 resources/mapper/ods/OdsAstMapper.xml；流式回调。 */
public interface OdsAstMapper {

    @Select("SELECT to_regclass(#{name}) IS NOT NULL")
    boolean exists(@Param("name") String fullTableName);

    /** 数据日期：学校表入 ODS 的最晚时间。 */
    @Select("SELECT max(trs_moye_input_time)::date FROM ${t.schools}")
    LocalDate dataDate(@Param("t") OdsTables t);

    void schools(@Param("t") OdsTables t, ResultHandler<AstRows.Facility> handler);

    void health(@Param("t") OdsTables t, ResultHandler<AstRows.Facility> handler);

    void mosques(@Param("t") OdsTables t, ResultHandler<AstRows.Facility> handler);

    void parks(@Param("t") OdsTables t, ResultHandler<AstRows.Facility> handler);

    void parkDetails(@Param("t") OdsTables t, ResultHandler<AstRows.Facility> handler);
}
