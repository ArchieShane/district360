package com.district360.stat.mapper;

import com.district360.common.theme.ast.AstRows;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 主题库 · 设施写入与关联，SQL 在 resources/mapper/stat/ThemeAstWriteMapper.xml。
 * 清表、ANALYZE、计数、登记复用 {@link ThemeSpatialWriteMapper}。
 */
public interface ThemeAstWriteMapper {

    void insertFacilities(@Param("rows") List<AstRows.Facility> rows);

    /** DMT 公园明细并入所在 udm 公园（名称、设施、开放时间）；没落在任何公园里的转成独立公园。 */
    int mergeParkDetails();

    /** 公园面积。 */
    int parkAreas();

    /** 学校按 udm_gisid 落地块；其余（及对不上的学校）按位置点落未注销地块。 */
    int linkPlots();

    /** 社区 / 区域 / 市政按设施自身位置点落区（与 v1 区情概览一致），社区没有的落区域。 */
    int linkAreas();
}
