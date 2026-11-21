package com.district360.makani.mapper;

import com.district360.makani.row.MakaniRows;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * makani 区级汇总：udm_district 里的全部区（所有市政）。
 * 区的唯一键用 objectid（districtid 在不同市政之间会重复）。
 * 除区面本身外，所有计数都按「对象中心点落在区面内」做空间汇总，不依赖各表里的区 ID / 区名字段。
 */
public interface MakaniMapper {

    List<MakaniRows.District> districts(@Param("t") MakaniTables t, @Param("tol") double tolerance);

    List<MakaniRows.Count> communities(@Param("t") MakaniTables t);

    List<MakaniRows.Plot> plots(@Param("t") MakaniTables t);

    List<MakaniRows.LandUse> landUse(@Param("t") MakaniTables t);

    List<MakaniRows.BuildingStat> buildingStats(@Param("t") MakaniTables t);

    List<MakaniRows.Count> schools(@Param("t") MakaniTables t);

    List<MakaniRows.Count> medical(@Param("t") MakaniTables t, @Param("exclude") List<String> excludeNamePatterns);

    List<MakaniRows.Count> mosques(@Param("t") MakaniTables t);

    List<MakaniRows.Building> buildings(@Param("t") MakaniTables t);

    List<MakaniRows.PlotShape> plotShapes(@Param("t") MakaniTables t);

    /** 学校 / 医疗 / 清真寺点位，筛选条件与 schools / medical / mosques 计数一致。 */
    List<MakaniRows.PoiRow> pois(@Param("t") MakaniTables t, @Param("exclude") List<String> excludeNamePatterns);

    LocalDate plotDataDate(@Param("t") MakaniTables t);
}
