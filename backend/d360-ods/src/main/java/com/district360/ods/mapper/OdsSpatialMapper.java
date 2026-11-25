package com.district360.ods.mapper;

import com.district360.common.theme.spatial.SpatialRows;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;

import java.time.LocalDate;
import java.util.Map;

/**
 * ODS 空间下钻取数，SQL 在 resources/mapper/ods/OdsSpatialMapper.xml。
 * 每个方法流式回调（ResultHandler），不把整张表装进内存。
 */
public interface OdsSpatialMapper {

    void municipalities(@Param("t") OdsTables t, @Param("codes") Map<Integer, String> codes,
                        ResultHandler<SpatialRows.Municipality> handler);

    void districts(@Param("t") OdsTables t, ResultHandler<SpatialRows.District> handler);

    void communities(@Param("t") OdsTables t, ResultHandler<SpatialRows.Community> handler);

    void plots(@Param("t") OdsTables t, ResultHandler<SpatialRows.Plot> handler);

    void buildings(@Param("t") OdsTables t, ResultHandler<SpatialRows.Building> handler);

    void floors(@Param("t") OdsTables t, ResultHandler<SpatialRows.Floor> handler);

    void units(@Param("t") OdsTables t, ResultHandler<SpatialRows.Unit> handler);

    /** 数据日期：区域表入 ODS 的最晚时间（trs_moye_input_time）。 */
    LocalDate dataDate(@Param("t") OdsTables t);
}
