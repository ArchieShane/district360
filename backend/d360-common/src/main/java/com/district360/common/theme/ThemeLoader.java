package com.district360.common.theme;

/**
 * 主题库写入：一个主题一个 Loader（实现在 d360-stat）。整批替换、单事务。
 *
 * @param <K> 写入端类型
 */
public interface ThemeLoader<K extends ThemeSink> {

    Class<K> sinkType();

    /** 清空本主题的表 → 调用抽取器写入 → 关联与汇总；全部在一个事务内。 */
    ThemeLoadResult load(ThemeExtractor<K> extractor);
}
