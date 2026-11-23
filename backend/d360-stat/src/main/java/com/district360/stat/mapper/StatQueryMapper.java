package com.district360.stat.mapper;

import com.district360.stat.row.District360Row;
import com.district360.stat.row.DistrictListRow;
import com.district360.stat.row.LandUseRow;
import com.district360.stat.row.MapFeatureRow;
import com.district360.stat.row.MonthlyRow;
import com.district360.stat.row.PoiRow;
import com.district360.stat.row.ScopeRow;
import com.district360.stat.row.SourceStatusRow;
import com.district360.stat.row.TileRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 接口读统计库。 */
public interface StatQueryMapper {

    /** 区列表分页（按英文名排序），q 为空表示不过滤。 */
    List<DistrictListRow> listDistricts(@Param("q") String q, @Param("offset") int offset, @Param("limit") int limit);

    int countDistricts(@Param("q") String q);

    District360Row findDistrict360(@Param("code") String code);

    List<LandUseRow> listLandUse(@Param("code") String code);

    List<MonthlyRow> listAdrecMonthly(@Param("code") String code);

    String districtBoundariesGeoJson();

    TileRow buildingTile(@Param("z") int z, @Param("x") int x, @Param("y") int y);

    /** 某区某类公共设施列表（按名称排序）。 */
    List<PoiRow> listPois(@Param("code") String code, @Param("category") String category);

    TileRow plotTile(@Param("z") int z, @Param("x") int x, @Param("y") int y);

    /** 地图点击详情：地块（id = map_plot.id，即瓦片里的 id 属性）。 */
    MapFeatureRow findPlot(@Param("id") long id);

    /** 地图点击详情：建筑（id = map_building.id，即瓦片里的 id 属性）。 */
    MapFeatureRow findBuilding(@Param("id") long id);

    List<SourceStatusRow> listSourceStatus();

    /** 各区统计范围，供宜居 / ADREC 抽取器做空间查询。 */
    List<ScopeRow> listScopes();
}
