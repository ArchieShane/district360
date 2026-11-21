package com.district360.common.extract;

import java.time.LocalDate;

/**
 * 一次抽取的结果。由各源的 {@link StatExtractor} 返回，统计库写入由 d360-stat 统一负责。
 */
public interface SourceSnapshot {

    /** 源数据日期（页面上各区块标注的「数据日期」）。 */
    LocalDate sourceAsOf();

    /** 用于抽取日志的行数。 */
    int rowCount();
}
