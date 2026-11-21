package com.district360.app.web.dto;

import java.util.List;

/** 分页结果。page 从 0 开始。 */
public record PageResult<T>(List<T> items, int page, int size, int total, boolean hasMore) {
}
