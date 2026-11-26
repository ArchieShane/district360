package com.district360.ods.mapper;

import com.district360.common.theme.ops.OpsRows;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.ResultHandler;

import java.time.LocalDate;

/** ODS 城市运行取数，SQL 在 resources/mapper/ods/OdsOpsMapper.xml；流式回调。 */
public interface OdsOpsMapper {

    @Select("SELECT to_regclass(#{name}) IS NOT NULL")
    boolean exists(@Param("name") String fullTableName);

    /** 表里是否有行。table 只来自 {@link OdsTables}（配置拼出的表名），不来自请求。 */
    @Select("SELECT EXISTS (SELECT 1 FROM ${table})")
    boolean hasRows(@Param("table") String fullTableName);

    /** 数据日期：路灯表入 ODS 的最晚时间。 */
    @Select("SELECT max(trs_moye_input_time)::date FROM ${t.streetlight}")
    LocalDate dataDate(@Param("t") OdsTables t);

    void streetlights(@Param("t") OdsTables t, ResultHandler<OpsRows.Streetlight> handler);

    void busStops(@Param("t") OdsTables t, ResultHandler<OpsRows.BusStop> handler);
}
