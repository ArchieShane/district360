package com.district360.common.extract.snapshot;

import com.district360.common.extract.SourceSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * ADREC 抽取结果：近 12 个月区级汇总、月度成交宗数、社区 → 区映射。
 */
public record AdrecSnapshot(
        LocalDate sourceAsOf,
        YearMonth periodStart,
        YearMonth periodEnd,
        List<District> districts,
        List<Monthly> monthly,
        List<CommunityMapping> communityMappings) implements SourceSnapshot {

    @Override
    public int rowCount() {
        return districts.size() + monthly.size() + communityMappings.size();
    }

    /**
     * @param salesAed       近 12 个月 sale_property_price_aed 求和
     * @param salesYoyPct    与上一个 12 个月相比的同比 %
     * @param transactions   近 12 个月 sale_application_id 计数
     * @param mortgages      近 12 个月按揭数
     * @param avgPriceSqm    近 12 个月 property_sale_rate_aed_sqm 平均
     * @param activeLeases   有效租约数
     */
    public record District(
            String code,
            BigDecimal salesAed,
            BigDecimal salesYoyPct,
            Integer transactions,
            Integer mortgages,
            BigDecimal avgPriceSqm,
            Integer activeLeases) {
    }

    public record Monthly(String code, YearMonth month, int transactions) {
    }

    /** districtCode 为 null 表示该 ADREC 社区还没映射到页面的区。 */
    public record CommunityMapping(String adrecCommunityName, String districtCode, String remark) {
    }
}
