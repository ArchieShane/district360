package com.district360.stat.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 主题库 · 市场读查询（/api/v2/space/nodes/{level}/{key}/mkt），SQL 在 resources/mapper/stat/ThemeMktQueryMapper.xml。
 * col 只来自 SpaceLevel 白名单，为 null 表示全酋长国。不返回任何个人信息（主题库里也没有）。
 */
public interface ThemeMktQueryMapper {

    /** Tawtheeq 汇总：记录数、租约数、单元数、各状态、ACTIVE 的年租金合计与中位数、单位面积年租金、90 天内到期、起止日期范围。 */
    Map<String, Object> tenancySummary(@Param("col") String col, @Param("key") String key);

    /** Tawtheeq 分组 [{k, n, medianRent}]：dim 只来自代码常量（status / unitType / propertyUsage）。 */
    List<Map<String, Object>> tenancyBreakdown(@Param("col") String col, @Param("key") String key, @Param("dim") String dim);

    /** 每年新签租约数与年租金中位数 [{y, n, medianRent}]（按 start_date）。 */
    List<Map<String, Object>> tenancyByYear(@Param("col") String col, @Param("key") String key);

    /** 租约明细（地块范围用），按起租日倒序，不含个人信息。 */
    List<Map<String, Object>> tenancies(@Param("col") String col, @Param("key") String key, @Param("limit") int limit);

    /** ADREC 汇总：买卖笔数 / 金额 / 均价（Σ金额 ÷ Σ面积）、抵押笔数 / 金额、租赁笔数 / 年租金、ELMS 地块交易笔数。 */
    Map<String, Object> adrecSummary(@Param("col") String col, @Param("key") String key);

    /** ADREC 每月笔数 [{m, sales, mortgages, rents}]，近 12 个有数据的月份。 */
    List<Map<String, Object>> adrecByMonth(@Param("col") String col, @Param("key") String key);
}
