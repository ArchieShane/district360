package com.district360.makani.mapper;

import com.district360.makani.row.MakaniRows;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * makani 区级汇总。除区面本身外，所有计数都按「对象中心点落在区面内」做空间汇总
 * （原型设计说明：按区面做空间汇总），不依赖各表里的区 ID / 区名字段。
 */
public interface MakaniMapper {

    List<MakaniRows.District> districts(@Param("t") MakaniTables t, @Param("muni") String muni,
                                        @Param("ids") List<Integer> ids, @Param("tol") double tolerance);

    List<MakaniRows.Count> communities(@Param("t") MakaniTables t, @Param("muni") String muni, @Param("ids") List<Integer> ids);

    List<MakaniRows.Plot> plots(@Param("t") MakaniTables t, @Param("muni") String muni, @Param("ids") List<Integer> ids);

    List<MakaniRows.LandUse> landUse(@Param("t") MakaniTables t, @Param("muni") String muni, @Param("ids") List<Integer> ids);

    List<MakaniRows.BuildingStat> buildingStats(@Param("t") MakaniTables t, @Param("muni") String muni, @Param("ids") List<Integer> ids);

    List<MakaniRows.Count> schools(@Param("t") MakaniTables t, @Param("muni") String muni, @Param("ids") List<Integer> ids);

    List<MakaniRows.Count> medical(@Param("t") MakaniTables t, @Param("muni") String muni, @Param("ids") List<Integer> ids,
                                   @Param("exclude") List<String> excludeNamePatterns);

    List<MakaniRows.Count> mosques(@Param("t") MakaniTables t, @Param("muni") String muni, @Param("ids") List<Integer> ids);

    List<MakaniRows.Building> buildings(@Param("t") MakaniTables t, @Param("muni") String muni, @Param("ids") List<Integer> ids);

    LocalDate plotDataDate(@Param("t") MakaniTables t);
}
