package com.district360.common.theme;

import java.time.LocalDate;
import java.util.Map;

/**
 * 一次主题抽取的结果。
 *
 * @param rows       写入的总行数
 * @param sourceAsOf 源数据日期
 * @param tableRows  各主题表最终行数（含关联后推导出来的行）
 */
public record ThemeLoadResult(long rows, LocalDate sourceAsOf, Map<String, Long> tableRows) {
}
