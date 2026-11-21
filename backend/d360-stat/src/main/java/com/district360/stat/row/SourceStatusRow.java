package com.district360.stat.row;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 每个数据源在统计库里的现状：最新数据日期、是否样例、最近一次抽取。 */
public class SourceStatusRow {
    public String source;
    public int rowCount;
    public LocalDate sourceAsOf;
    public Boolean sample;
    public String lastStatus;
    public OffsetDateTime lastStartedAt;
    public OffsetDateTime lastFinishedAt;
    public String lastMessage;
}
