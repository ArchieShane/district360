package com.district360.common.theme.spatial;

import com.district360.common.theme.ThemeSink;

/**
 * 空间下钻主题的写入端：市政 → 区域 → 社区 → 地块 → 建筑 → 楼层 → 单元，逐行写入。
 * 行类型见 {@link SpatialRows}，几何一律为 EPSG:4326 的 EWKB hex（二维，面转为 MultiPolygon）。
 * <p>
 * 上下级关系不需要抽取器算：抽取器只填源库里有的编号，写完后由主题库统一关联（属性 + 空间）。
 */
public interface SpatialSink extends ThemeSink {

    void municipality(SpatialRows.Municipality row);

    void district(SpatialRows.District row);

    void community(SpatialRows.Community row);

    void plot(SpatialRows.Plot row);

    void building(SpatialRows.Building row);

    void floor(SpatialRows.Floor row);

    void unit(SpatialRows.Unit row);
}
