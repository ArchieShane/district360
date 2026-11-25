package com.district360.common.theme;

/**
 * 主题库写入端（标记接口）。每个主题定义自己的 Sink（如 {@link com.district360.common.theme.spatial.SpatialSink}），
 * 抽取器把源库记录<b>逐行</b>推给 Sink，由 d360-stat 里对应的 {@link ThemeLoader} 分批写入主题库。
 * <p>
 * 与 {@link com.district360.common.extract.StatExtractor} 的区别：统计库每个区一行，快照整体放在内存里；
 * 主题库是明细（全量 38 万建筑、52 万楼层、73 万单元），必须流式写入，不能一次性装进内存。
 */
public interface ThemeSink {
}
