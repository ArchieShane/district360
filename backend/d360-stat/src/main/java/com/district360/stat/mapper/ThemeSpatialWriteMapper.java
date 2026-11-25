package com.district360.stat.mapper;

import com.district360.common.theme.spatial.SpatialRows;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

/**
 * 主题库 · 空间下钻写入与关联，SQL 在 resources/mapper/stat/ThemeSpatialWriteMapper.xml。
 * 由 {@link com.district360.stat.theme.ThemeSpatialLoader} 在一个事务里按顺序调用；表名参数只来自代码里的常量。
 */
public interface ThemeSpatialWriteMapper {

    @Delete("DELETE FROM theme.${table}")
    void clear(@Param("table") String table);

    @Update("ANALYZE theme.${table}")
    void analyze(@Param("table") String table);

    @Select("SELECT count(*) FROM theme.${table}")
    long count(@Param("table") String table);

    void insertMunicipalities(@Param("rows") List<SpatialRows.Municipality> rows);

    void insertDistricts(@Param("rows") List<SpatialRows.District> rows);

    void insertCommunities(@Param("rows") List<SpatialRows.Community> rows);

    void insertPlots(@Param("rows") List<SpatialRows.Plot> rows);

    void insertBuildings(@Param("rows") List<SpatialRows.Building> rows);

    void insertFloors(@Param("rows") List<SpatialRows.Floor> rows);

    void insertUnits(@Param("rows") List<SpatialRows.Unit> rows);

    // ---- 写入后在主题库内关联（按调用顺序）--------------------------------------------------------

    /** 楼层表缺的楼层由单元推导（source = bdms_units）。 */
    int deriveFloorsFromUnits();

    /** BDMS 建筑代表点（最低一层楼层 / 单元面内一点），临时表，事务结束自动删除。 */
    void createBdmsPoints();

    void indexBdmsPoints();

    /** 临时表没有统计信息，ANALYZE 后规划器才能按实际行数选计划。 */
    void analyzeBdmsPoints();

    /** 还没对应 BDMS 的 udm 建筑面复制到临时表（避开主表索引里本事务删除的旧条目）。 */
    void createUdmFootprints();

    void indexUdmFootprints();

    void analyzeUdmFootprints();

    /** V12 之前抽取的楼层（键里没有市政）行数；大于 0 时不能只同步单元，要先全量 ods-spatial。 */
    @Select("SELECT count(*) FROM theme.sp_floor WHERE bdms_municipality IS NULL")
    long legacyFloors();

    /** 单元同步：删掉上一次由单元推导的楼层。 */
    int deleteDerivedFloors();

    /** 单元同步：删掉已经没有楼层的 bdms 推导建筑。 */
    int deleteOrphanBdmsBuildings();

    /** BDMS 建筑 → udm 建筑：代表点落在建筑面内（多个命中取面积最小的）。 */
    int matchBdmsBuildings();

    /** 没匹配上 udm 建筑的 BDMS 建筑，用其楼层外包面生成建筑（source = bdms），按 (市政, 编号) 分组。 */
    int insertBdmsBuildings();

    int fillMunicipalitiesFromDistricts();

    void fillAreas();

    int linkCommunitiesToDistricts();

    /** 地块 → 社区：按地块自带的市政 + 区 + 社区编号。 */
    int linkPlotsByAttributes();

    /** 地块 → 社区：属性对不上的按位置。 */
    int linkPlotsToCommunities();

    int linkPlotsToDistricts();

    int linkBuildingsByGisid();

    int linkBuildingsByLocation();

    int propagateBuildingsFromPlots();

    int linkBuildingsToCommunities();

    /** onlyMissing = true 时只关联还没有建筑的楼层（单元同步用）。 */
    int linkFloorsToBuildings(@Param("onlyMissing") boolean onlyMissing);

    void numberFloors();

    int linkUnitsToFloors();

    void fillBuildingCounts();

    void computeNodeStats();

    void updateMeta(@Param("table") String table, @Param("rows") long rows, @Param("asOf") LocalDate asOf);
}
