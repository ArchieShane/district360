package com.district360.stat.mapper;

import com.district360.stat.row.District360Row;
import com.district360.stat.row.DistrictListRow;
import com.district360.stat.row.LandUseRow;
import com.district360.stat.row.MonthlyRow;
import com.district360.stat.row.SourceStatusRow;
import com.district360.stat.row.TileRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 接口读统计库。 */
public interface StatQueryMapper {

    List<DistrictListRow> listDistricts();

    District360Row findDistrict360(@Param("code") String code);

    List<LandUseRow> listLandUse(@Param("code") String code);

    List<MonthlyRow> listAdrecMonthly(@Param("code") String code);

    String districtBoundariesGeoJson();

    TileRow buildingTile(@Param("z") int z, @Param("x") int x, @Param("y") int y);

    List<SourceStatusRow> listSourceStatus();
}
