package com.district360.app.web.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record MetaInfo(int districtCount, int blockCount, List<SourceInfo> sources) {

    /**
     * @param enabled    后端是否配置并启用了该源的抽取器
     * @param sample     统计库里是否还是样例数据
     */
    public record SourceInfo(String source, boolean enabled, int rowCount, LocalDate sourceAsOf, boolean sample,
                             String lastStatus, OffsetDateTime lastFinishedAt, String lastMessage) {
    }
}
