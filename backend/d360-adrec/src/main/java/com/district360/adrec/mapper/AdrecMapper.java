package com.district360.adrec.mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * TODO(房地产同事)：按需改返回类型，SQL 写在 resources/mapper/adrec/AdrecMapper.xml。
 * <p>
 * 口径要求：<b>按统计范围做空间查询</b>（{@code DistrictRef#scopeEwkbHex()}，makani 原始区面），
 * 成交 / 按揭 / 租约记录的位置落在范围内即计入；不要按区名或区 ID 匹配。
 * 口径见 docs/District360_数据字段对照_2026112101.xlsx 和 docs/接口设计与协作方案_v0.2.md 第 3、4.2 节。
 */
public interface AdrecMapper {

    /** ADREC 里出现过的全部社区名，用来维护社区 → 区映射。 */
    List<String> communityNames();

    /** 按社区、按月汇总：成交额、宗数、均价等（区级汇总在 Java 里按映射表累加）。 */
    List<Map<String, Object>> monthlyByCommunity();

    /** 数据日期，例如 _ingested_at 的最大值。 */
    LocalDate dataDate();
}
